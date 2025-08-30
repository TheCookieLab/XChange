package info.bitrich.xchangestream.binance;

import static info.bitrich.xchangestream.binance.dto.BaseBinanceWebSocketTransaction.BinanceWebSocketTypes.EXECUTION_REPORT;
import static info.bitrich.xchangestream.binance.dto.BaseBinanceWebSocketTransaction.BinanceWebSocketTypes.ORDER_TRADE_UPDATE;
import static info.bitrich.xchangestream.binance.dto.BaseBinanceWebSocketTransaction.BinanceWebSocketTypes.TRADE_LITE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.binance.dto.AccountUpdateBinanceWebSocketTransaction;
import info.bitrich.xchangestream.binance.dto.BaseBinanceWebSocketTransaction.BinanceWebSocketTypes;
import info.bitrich.xchangestream.binance.dto.ExecutionReportBinanceUserTransaction;
import info.bitrich.xchangestream.binance.dto.ExecutionReportBinanceUserTransaction.ExecutionType;
import info.bitrich.xchangestream.binance.dto.OrderTradeUpdateBinanceWebSocketTransaction;
import info.bitrich.xchangestream.binance.dto.TradeLiteBinanceWebsocketTransaction;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.io.IOException;
import java.math.BigDecimal;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BinanceStreamingTradeService implements StreamingTradeService {

  private static final Logger LOG = LoggerFactory.getLogger(BinanceStreamingTradeService.class);

  private final Subject<ExecutionReportBinanceUserTransaction> executionReportsPublisher =
      PublishSubject.<ExecutionReportBinanceUserTransaction>create().toSerialized();

  private final Subject<OrderTradeUpdateBinanceWebSocketTransaction> orderTradeUpdatePublisher =
      PublishSubject.<OrderTradeUpdateBinanceWebSocketTransaction>create().toSerialized();

  private final Subject<TradeLiteBinanceWebsocketTransaction> tradeLitePublisher =
      PublishSubject.<TradeLiteBinanceWebsocketTransaction>create().toSerialized();

  private final Subject<AccountUpdateBinanceWebSocketTransaction> positionChangesPublisher =
      PublishSubject.<AccountUpdateBinanceWebSocketTransaction>create().toSerialized();

  private volatile Disposable executionReports;
  private volatile Disposable orderTradeUpdate;
  private volatile Disposable tradeLite;
  private volatile Disposable positionChanges;
  private final BinanceExchange exchange;
  private volatile BinanceUserDataStreamingService binanceUserDataStreamingService;

  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  public BinanceStreamingTradeService(BinanceExchange exchange,
      BinanceUserDataStreamingService binanceUserDataStreamingService) {
    this.exchange = exchange;
    this.binanceUserDataStreamingService = binanceUserDataStreamingService;
  }

  public Observable<ExecutionReportBinanceUserTransaction> getRawExecutionReports() {
    if (binanceUserDataStreamingService == null || !binanceUserDataStreamingService.isSocketOpen()) {
      throw new ExchangeSecurityException("Not authenticated");
    }
    return executionReportsPublisher;
  }

  public Observable<OrderTradeUpdateBinanceWebSocketTransaction> getRawOrderTradeUpdate() {
    if (binanceUserDataStreamingService == null || !binanceUserDataStreamingService.isSocketOpen()) {
      throw new ExchangeSecurityException("Not authenticated");
    }
    return orderTradeUpdatePublisher;
  }

  public Observable<TradeLiteBinanceWebsocketTransaction> getRawTradeLite() {
    if (binanceUserDataStreamingService == null || !binanceUserDataStreamingService.isSocketOpen()) {
      throw new ExchangeSecurityException("Not authenticated");
    }
    return tradeLitePublisher;
  }

  public Observable<AccountUpdateBinanceWebSocketTransaction> getRawPositionChanges(boolean isFuture) {
    if (binanceUserDataStreamingService == null || !binanceUserDataStreamingService.isSocketOpen()) {
      throw new ExchangeSecurityException("Not authenticated");
    }
    return positionChangesPublisher;
  }


  public Observable<Order> getOrderChanges(boolean isFuture) {
    if (exchange.isFuturesEnabled()) {
      return getRawOrderTradeUpdate()
          .map(orderTradeUpdate -> orderTradeUpdate.getUpdateTransaction()
              .toOrder(isFuture));
    } else {
      return getRawExecutionReports()
          .filter(r -> !r.getExecutionType().equals(ExecutionType.REJECTED))
          .map(binanceExec -> binanceExec.toOrder(isFuture));
    }
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    return getOrderChanges(false).filter(oc -> currencyPair.equals(oc.getInstrument()));
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    return getOrderChanges(instrument instanceof FuturesContract)
        .filter(oc -> instrument.equals(oc.getInstrument()));
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    return getUserTrades(false).filter(t -> t.getInstrument().equals(currencyPair));
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    return getUserTrades(instrument instanceof FuturesContract)
        .filter(t -> t.getInstrument().equals(instrument));
  }

  public Observable<UserTrade> getUserTrades(boolean isFuture) {
    if (exchange.isFuturesEnabled()) {
      return getRawTradeLite()
          .map(tradeList -> tradeList.toUserTrade(isFuture));
    } else {
      return getRawExecutionReports()
          .filter(r -> r.getExecutionType().equals(ExecutionType.TRADE))
          .map(binanceExec -> binanceExec.toUserTrade(isFuture));
    }
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    if (exchange.isFuturesEnabled() || exchange.isPortfolioMarginEnabled()) {
      boolean isFutures = instrument instanceof FuturesContract;
      return getRawPositionChanges(isFutures)
          .map(position -> position.getAccountUpdate()
              .getPositions()
              .stream()
              .map(p -> p.toOpenPosition(isFutures))
              .filter(f -> f.getInstrument().equals(instrument))
              .findFirst().orElseGet(() ->
                  // return zero position
                  OpenPosition.builder().instrument(instrument)
                      .size(BigDecimal.ZERO)
                      .build()));
    } else {
      throw new UnsupportedOperationException("spot not supported");
    }
    }

    /**
     * Registers subsriptions with the streaming service for the given products.
     */
    public void openSubscriptions () {
      if (binanceUserDataStreamingService != null) {
        executionReports =
            binanceUserDataStreamingService
                .subscribeChannel(
                    EXECUTION_REPORT)
                .map(this::executionReport)
                .subscribe(executionReportsPublisher::onNext);
        orderTradeUpdate = binanceUserDataStreamingService
            .subscribeChannel(ORDER_TRADE_UPDATE)
            .map(this::orderTradeUpdate)
            .subscribe(orderTradeUpdatePublisher::onNext);
        tradeLite = binanceUserDataStreamingService.subscribeChannel(TRADE_LITE)
            .map(this::tradeLite)
            .subscribe(tradeLitePublisher::onNext);
        positionChanges = binanceUserDataStreamingService
            .subscribeChannel(BinanceWebSocketTypes.ACCOUNT_UPDATE)
            .map(this::positionChanges)
            .subscribe(positionChangesPublisher::onNext);

        binanceUserDataStreamingService.setEnableLoggingHandler(true);
      }
    }

    /**
     * User data subscriptions may have to persist across multiple socket connections to different URLs and therefore must act in a publisher fashion so that subscribers get an uninterrupted stream.
     */
    void setUserDataStreamingService (
        BinanceUserDataStreamingService binanceUserDataStreamingService){
      if (executionReports != null && !executionReports.isDisposed()) {
        executionReports.dispose();
      }
      if (orderTradeUpdate != null && !orderTradeUpdate.isDisposed()) {
        orderTradeUpdate.dispose();
      }
      if (tradeLite != null && !tradeLite.isDisposed()) {
        tradeLite.dispose();
      }
      if (positionChanges != null && !positionChanges.isDisposed()) {
        positionChanges.dispose();
      }
      this.binanceUserDataStreamingService = binanceUserDataStreamingService;
      openSubscriptions();
    }

    private OrderTradeUpdateBinanceWebSocketTransaction orderTradeUpdate (JsonNode json){
      try {
        return mapper.treeToValue(json, OrderTradeUpdateBinanceWebSocketTransaction.class);
      } catch (IOException e) {
        throw new ExchangeException("Unable to parse order trade update", e);
      }
    }

    private TradeLiteBinanceWebsocketTransaction tradeLite (JsonNode json){
      try {
        return mapper.treeToValue(json, TradeLiteBinanceWebsocketTransaction.class);
      } catch (IOException e) {
        throw new ExchangeException("Unable to parse order trade update", e);
      }
    }

    private AccountUpdateBinanceWebSocketTransaction positionChanges (JsonNode json){
      try {
        return mapper.treeToValue(json, AccountUpdateBinanceWebSocketTransaction.class);
      } catch (IOException e) {
        throw new ExchangeException("Unable to parse order trade update", e);
      }
    }

    private ExecutionReportBinanceUserTransaction executionReport (JsonNode json){
      try {
        return mapper.treeToValue(json, ExecutionReportBinanceUserTransaction.class);
      } catch (IOException e) {
        throw new ExchangeException("Unable to parse execution report", e);
      }
    }
  }
