package info.bitrich.xchangestream.okex;

import static info.bitrich.xchangestream.okex.OkexPrivateStreamingService.USERTRADES;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexAdapters;
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
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    String channelUniqueId = USERTRADES + OkexAdapters.adaptInstrument(instrument);

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
}
