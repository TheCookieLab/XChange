package info.bitrich.xchangestream.okex;


import static info.bitrich.xchangestream.okex.OkexPrivateStreamingService.USER_ORDER_CHANGES;
import static info.bitrich.xchangestream.okex.OkexPrivateStreamingService.USER_POSITION_CHANGES;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexAdapters;
import org.knowm.xchange.okex.dto.account.OkexPosition;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;

public class OkexStreamingTradeService implements StreamingTradeService {

  private final OkexPrivateStreamingService privateStreamingService;
  private final ExchangeMetaData exchangeMetaData;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  public OkexStreamingTradeService(
      OkexPrivateStreamingService privateStreamingService, ExchangeMetaData exchangeMetaData) {
    this.privateStreamingService = privateStreamingService;
    this.exchangeMetaData = exchangeMetaData;
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    String channelUniqueId = USER_ORDER_CHANGES + OkexAdapters.adaptInstrument(instrument);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkexOrderDetails> okexOrderDetails =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkexOrderDetails.class));
              return Observable.fromIterable(
                  OkexAdapters.adaptOrdersChanges(okexOrderDetails, exchangeMetaData));
            });
  }

  // cannot use OrderChanges and UserTrades together
  // leave it for backward compatibility, but it is not trade at all
  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    String channelUniqueId = USER_ORDER_CHANGES + OkexAdapters.adaptInstrument(instrument);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkexOrderDetails> okexOrderDetails =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkexOrderDetails.class));
              return Observable.fromIterable(
                  OkexAdapters.adaptUserTrades(okexOrderDetails, exchangeMetaData).getUserTrades());
            });
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    String channelUniqueId = USER_POSITION_CHANGES + OkexAdapters.adaptInstrument(instrument);
    return privateStreamingService.subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkexPosition> okexPositions =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkexPosition.class));
              return Observable.fromIterable(
                  OkexAdapters.adaptOpenPositions(okexPositions, exchangeMetaData).getOpenPositions());
            });
  }
}
