package info.bitrich.xchangestream.okx;

import static info.bitrich.xchangestream.okx.OkxStreamingService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.service.exception.NotConnectedException;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.dto.marketdata.*;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.dto.marketdata.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkxStreamingMarketDataService implements StreamingMarketDataService {

  private static final Logger LOG = LoggerFactory.getLogger(OkxStreamingMarketDataService.class);

  private final OkxStreamingService service;
  private final OkxBusinessStreamingService businessStreamingService;
  private final ExchangeMetaData exchangeMetaData;

  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private final Map<Instrument, PublishSubject<List<OrderBookUpdate>>>
      orderBookUpdatesSubscriptions;

  public OkxStreamingMarketDataService(
      OkxStreamingService service,
      OkxBusinessStreamingService businessStreamingService,
      ExchangeMetaData exchangeMetaData) {
    this.service = service;
    this.businessStreamingService = businessStreamingService;
    this.exchangeMetaData = exchangeMetaData;
    this.orderBookUpdatesSubscriptions = new ConcurrentHashMap<>();
  }

  private final Map<String, OrderBook> orderBookMap = new HashMap<>();

  private final OkxBookContinuity bookContinuity = new OkxBookContinuity();

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    String channelUniqueId = TICKERS + OkxAdapters.adaptInstrument(instrument);

    return service
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxTicker> okxTickers =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper.getTypeFactory().constructCollectionType(List.class, OkxTicker.class));
              return Observable.fromIterable(okxTickers).map(OkxAdapters::adaptTicker);
            });
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    String channelUniqueId = TRADES + OkxAdapters.adaptInstrument(instrument);

    return service
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxTrade> okxTradeList =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper.getTypeFactory().constructCollectionType(List.class, OkxTrade.class));
              return Observable.fromIterable(
                  OkxAdapters.adaptTrades(okxTradeList, instrument, exchangeMetaData).getTrades());
            });
  }

  @Override
  public Observable<FundingRate> getFundingRate(Instrument instrument, Object... args) {
    String channelUniqueId = FUNDING_RATE + OkxAdapters.adaptInstrument(instrument);

    return service
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .map(
            jsonNode -> {
              List<OkxFundingRate> okxFundingRates =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxFundingRate.class));
              return OkxAdapters.adaptFundingRate(okxFundingRates);
            });
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    String instId = OkxAdapters.adaptInstrument(instrument);
    String channelName = args.length >= 1 ? args[0].toString() : "books";
    String channelUniqueId = channelName + instId;

    return service
        .subscribeChannel(channelUniqueId)
        .flatMap(
            jsonNode -> {
              List<OkxOrderbook> okxOrderbooks =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxOrderbook.class));
              if (jsonNode.get("action") != null) {
                // "books5" channel pushes 5 depth levels every time.
                String action =
                    channelName.equals(ORDERBOOK5) ? "snapshot" : jsonNode.get("action").asText();
                if ("snapshot".equalsIgnoreCase(action)) {
                  if (!bookContinuity.snapshot(channelUniqueId, jsonNode.get("data").get(0))) {
                    LOG.warn(
                        "Order book snapshot failed checksum for channel={}, requesting a fresh snapshot.",
                        channelUniqueId);
                    service.resubscribeChannel(channelUniqueId);
                    return Observable.fromIterable(new LinkedList<>());
                  }
                  OrderBook orderBook =
                      OkxAdapters.adaptOrderBook(okxOrderbooks, instrument, exchangeMetaData);
                  orderBookMap.put(channelUniqueId, orderBook);
                  return Observable.just(orderBook);
                } else if ("update".equalsIgnoreCase(action)) {
                  if (!channelName.equals(ORDERBOOK5)) {
                    OkxBookContinuity.Gate gate =
                        bookContinuity.gateUpdate(channelUniqueId, jsonNode.get("data").get(0));
                    if (gate == OkxBookContinuity.Gate.REBUILD) {
                      LOG.warn(
                          "Order book continuity violated for channel={}, requesting a fresh snapshot.",
                          channelUniqueId);
                      service.resubscribeChannel(channelUniqueId);
                      return Observable.fromIterable(new LinkedList<>());
                    }
                    if (gate == OkxBookContinuity.Gate.DROP_STALE) {
                      return Observable.fromIterable(new LinkedList<>());
                    }
                  }
                  OrderBook orderBook = orderBookMap.getOrDefault(channelUniqueId, null);
                  if (orderBook == null) {
                    LOG.error("Failed to get orderBook, channel={}.", channelUniqueId);
                    return Observable.fromIterable(new LinkedList<>());
                  }
                  Date timestamp = new Timestamp(Long.parseLong(okxOrderbooks.get(0).getTs()));
                  BigDecimal contractValue =
                      OkxAdapters.instrumentMetaData(instrument, exchangeMetaData)
                          .getContractValue();
                  List<OrderBookUpdate> orderBookUpdates =
                      OkxAdapters.adaptOrderBookUpdates(
                          instrument,
                          okxOrderbooks.get(0).getAsks(),
                          okxOrderbooks.get(0).getBids(),
                          contractValue,
                          timestamp);
                  orderBookUpdates.forEach(orderBook::update);
                  if (orderBookUpdatesSubscriptions.get(instrument) != null) {
                    orderBookUpdatesSubscriptions(instrument, orderBookUpdates);
                  }
                  return Observable.just(orderBook);
                } else {
                  LOG.error("Unexpected books action={}, message={}", action, jsonNode);
                  return Observable.fromIterable(new LinkedList<>());
                }
              } else {
                if (channelName.contains(ORDERBOOK_BBO_TBT)) {
                  // one level orderbook snapshot
                  OrderBook orderBook =
                      OkxAdapters.adaptOrderBook(okxOrderbooks, instrument, exchangeMetaData);
                  return Observable.just(orderBook);
                }
                return Observable.fromIterable(new LinkedList<>());
              }
            });
  }

  @Override
  public Observable<List<OrderBookUpdate>> getOrderBookUpdates(
      Instrument instrument, Object... args) {
    return orderBookUpdatesSubscriptions.computeIfAbsent(instrument, v -> PublishSubject.create());
  }

  private void orderBookUpdatesSubscriptions(
      Instrument instrument, List<OrderBookUpdate> orderBookUpdates) {
    orderBookUpdatesSubscriptions.get(instrument).onNext(orderBookUpdates);
  }

  @Override
  public Observable<CandleStickData> getCandleStick(
      Instrument instrument, CandleStickInterval interval) {
    String channelUniqueId =
        OkxAdapters.adaptCandleStickInterval(interval).name()
            + "-"
            + OkxAdapters.adaptInstrument(instrument);

    if (businessStreamingService == null) {
      return Observable.error(new NotConnectedException());
    }

    return businessStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxCandleStick> okxCandles =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxCandleStick.class));
              return Observable.just(OkxAdapters.adaptCandleStickData(okxCandles, instrument));
            });
  }
}
