package info.bitrich.xchangestream.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsOrder;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsTrade;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.polymarket.PolymarketAdapters;

/**
 * Authenticated Polymarket user streams (order-state updates and trade fills) over the CLOB user
 * channel. Both require the exchange to be configured with the L2 credential triplet ({@code
 * apiKey}, {@code secretKey}, and {@code password} as the L2 passphrase); without them the methods
 * throw {@link ExchangeSecurityException} before any subscription is attempted.
 *
 * <p>Order and trade events for one market share a single user-channel subscription per condition
 * id (the base streaming service would otherwise orphan any second subscriber on the same
 * channel).
 */
public class PolymarketStreamingTradeService implements StreamingTradeService {

  private final PolymarketStreamingService service;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private final Map<String, Observable<JsonNode>> userChannels = new ConcurrentHashMap<>();

  public PolymarketStreamingTradeService(PolymarketStreamingService service) {
    this.service = service;
  }

  /**
   * Streams fills on the user's own orders: one entry per matched leg, so a {@code MAKER} trade
   * event yields one fill per matched maker order.
   */
  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    requireCredentials();
    String conditionId = PolymarketAdapters.conditionId(instrument);
    return userChannel(conditionId)
        .filter(node -> "trade".equals(node.path("event_type").asText("")))
        .flatMapIterable(
            node ->
                PolymarketStreamingAdapters.adaptUserTrades(
                    mapper.treeToValue(node, PolymarketWsTrade.class)));
  }

  /** Streams the full current state of the user's orders on placement, update, and cancellation. */
  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    requireCredentials();
    String conditionId = PolymarketAdapters.conditionId(instrument);
    return userChannel(conditionId)
        .filter(node -> "order".equals(node.path("event_type").asText("")))
        .map(
            node ->
                (Order)
                    PolymarketStreamingAdapters.adaptOrder(
                        mapper.treeToValue(node, PolymarketWsOrder.class)));
  }

  private Observable<JsonNode> userChannel(String conditionId) {
    return userChannels.computeIfAbsent(
        conditionId,
        c -> service.subscribeChannel(PolymarketStreamingService.CHANNEL_USER, c));
  }

  private void requireCredentials() {
    if (!service.hasCredentials()) {
      throw new ExchangeSecurityException(
          "Polymarket user streams require the apiKey, secretKey, and password (L2 passphrase)"
              + " credentials");
    }
  }
}
