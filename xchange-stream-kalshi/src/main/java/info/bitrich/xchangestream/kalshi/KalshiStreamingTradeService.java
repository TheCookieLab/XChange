package info.bitrich.xchangestream.kalshi;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsFill;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsUserOrder;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kalshi.KalshiAdapters;

/**
 * Authenticated Kalshi user streams (fills and order-state updates). Credentials are mandatory
 * for every Kalshi WebSocket session and enforced at {@link KalshiStreamingService} construction,
 * so no per-call guard is needed here.
 */
public class KalshiStreamingTradeService implements StreamingTradeService {

  private final KalshiStreamingService service;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  public KalshiStreamingTradeService(KalshiStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    String ticker = KalshiAdapters.marketTicker(instrument);
    return service
        .subscribeChannel(KalshiStreamingService.CHANNEL_FILL, ticker)
        .map(
            node ->
                KalshiStreamingAdapters.adaptFill(
                    mapper.treeToValue(node.path("msg"), KalshiWsFill.class)));
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    String ticker = KalshiAdapters.marketTicker(instrument);
    return service
        .subscribeChannel(KalshiStreamingService.CHANNEL_USER_ORDER, ticker)
        .map(
            node ->
                (Order)
                    KalshiStreamingAdapters.adaptUserOrder(
                        mapper.treeToValue(node.path("msg"), KalshiWsUserOrder.class)));
  }
}
