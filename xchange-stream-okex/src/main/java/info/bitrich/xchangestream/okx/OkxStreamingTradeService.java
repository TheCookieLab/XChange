package info.bitrich.xchangestream.okx;

import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.CANCEL_ORDER;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.CHANGE_ORDER;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.PLACE_ORDER;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.USER_ORDER_CHANGES;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.USER_POSITION_CHANGES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.github.resilience4j.rxjava3.ratelimiter.operator.RateLimiterOperator;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.OrderNotValidException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams.OkxCancelOrderParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;

public class OkxStreamingTradeService implements StreamingTradeService {

  /** Default cap for the per-channel private-event dedupe caches. */
  static final int DEFAULT_DEDUPE_CACHE_SIZE = 10000;

  private final OkxPrivateStreamingService privateStreamingService;
  private final ExchangeMetaData exchangeMetaData;
  private final ResilienceRegistries resilienceRegistries;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private final int dedupeCacheSize;
  private final Map<String, OkxEventDeduplicator> deduplicators = new ConcurrentHashMap<>();

  public OkxStreamingTradeService(
      OkxPrivateStreamingService privateStreamingService,
      ExchangeMetaData exchangeMetaData,
      ResilienceRegistries resilienceRegistries) {
    this(
        privateStreamingService, exchangeMetaData, resilienceRegistries, DEFAULT_DEDUPE_CACHE_SIZE);
  }

  /**
   * Package-private constructor with an explicit dedupe cache size, kept injectable for offline
   * tests that exercise the bounded-cache behavior.
   */
  OkxStreamingTradeService(
      OkxPrivateStreamingService privateStreamingService,
      ExchangeMetaData exchangeMetaData,
      ResilienceRegistries resilienceRegistries,
      int dedupeCacheSize) {
    this.privateStreamingService = privateStreamingService;
    this.exchangeMetaData = exchangeMetaData;
    this.resilienceRegistries = resilienceRegistries;
    this.dedupeCacheSize = dedupeCacheSize;
  }

  private OkxEventDeduplicator deduplicatorFor(String channelUniqueId) {
    return deduplicators.computeIfAbsent(
        channelUniqueId, key -> new OkxEventDeduplicator(dedupeCacheSize));
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    String channelUniqueId = USER_ORDER_CHANGES + OkxAdapters.adaptInstrument(instrument);
    OkxEventDeduplicator deduplicator = deduplicatorFor(channelUniqueId);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxOrderDetails> okxOrderDetails =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxOrderDetails.class));
              List<OkxOrderDetails> freshOrderDetails =
                  okxOrderDetails.stream()
                      .filter(details -> !deduplicator.isDuplicate(orderEventKey(details)))
                      .collect(Collectors.toList());
              return Observable.fromIterable(
                  OkxAdapters.adaptOrdersChanges(freshOrderDetails, exchangeMetaData));
            });
  }

  // cannot use OrderChanges and UserTrades together
  // leave it for backward compatibility, but it is not trade at all
  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    String channelUniqueId = USER_ORDER_CHANGES + OkxAdapters.adaptInstrument(instrument);
    OkxEventDeduplicator deduplicator = deduplicatorFor(channelUniqueId);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxOrderDetails> okxOrderDetails =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxOrderDetails.class));
              List<OkxOrderDetails> freshOrderDetails =
                  okxOrderDetails.stream()
                      .filter(details -> !deduplicator.isDuplicate(orderEventKey(details)))
                      .collect(Collectors.toList());
              List<UserTrade> userTrades = new ArrayList<>(freshOrderDetails.size());
              for (OkxOrderDetails details : freshOrderDetails) {
                UserTrade userTrade =
                    OkxAdapters.adaptUserTrades(
                            Collections.singletonList(details), exchangeMetaData)
                        .getUserTrades()
                        .get(0);
                // Surface the fill-level tradeId (the wire "tradeId") so callers can correlate
                // the streamed fill with REST fills and order history.
                String tradeId = details.getLastTradeId();
                if (tradeId != null && !tradeId.isEmpty()) {
                  userTrade.setId(tradeId);
                }
                userTrades.add(userTrade);
              }
              return Observable.fromIterable(userTrades);
            });
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    String channelUniqueId = USER_POSITION_CHANGES + OkxAdapters.adaptInstrument(instrument);
    OkxEventDeduplicator deduplicator = deduplicatorFor(channelUniqueId);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxPosition> okxPositions =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxPosition.class));
              List<OkxPosition> freshPositions =
                  okxPositions.stream()
                      .filter(position -> !deduplicator.isDuplicate(positionEventKey(position)))
                      .collect(Collectors.toList());
              return Observable.fromIterable(
                  OkxAdapters.adaptOpenPositions(freshPositions, exchangeMetaData)
                      .getOpenPositions());
            });
  }

  @Override
  public Single<Integer> placeLimitOrder(LimitOrder order, Object... args) {
    return submitOrderRequest(
        PLACE_ORDER, order, order.getUserReference(), OkxAuthenticated.placeOrderPath);
  }

  @Override
  public Single<Integer> placeMarketOrder(MarketOrder order, Object... args) {
    return submitOrderRequest(
        PLACE_ORDER, order, order.getUserReference(), OkxAuthenticated.placeOrderPath);
  }

  @Override
  public Single<Integer> changeOrder(LimitOrder order, Object... args) {
    return submitOrderRequest(
        CHANGE_ORDER, order, order.getUserReference(), OkxAuthenticated.amendOrderPath);
  }

  @Override
  public Single<Integer> cancelOrder(CancelOrderParams params, Object... args) {
    String clientOrderId =
        params instanceof OkxCancelOrderParams
            ? ((OkxCancelOrderParams) params).getUserReference()
            : null;
    return submitOrderRequest(
        CANCEL_ORDER, params, clientOrderId, OkxAuthenticated.cancelOrderPath);
  }

  /**
   * Submits a private order operation over the websocket and correlates the response with the
   * requested client order id ({@code clOrdId}) when one was provided.
   *
   * <p>Errors are surfaced as typed xchange exceptions: {@link OrderNotValidException} for order
   * rejections (per-order {@code sCode}/{@code sMsg} or response-level {@code code}/{@code msg})
   * and {@link ExchangeException} when the private socket is not authorized.
   */
  private Single<Integer> submitOrderRequest(
      String method, Object payload, String clientOrderId, String rateLimiterPath) {
    if (privateStreamingService == null || !privateStreamingService.isLoginDone()) {
      throw new ExchangeException("privateStreamingService not authorized");
    }
    Observable<Integer> observable =
        privateStreamingService
            .subscribeChannel(String.valueOf(System.nanoTime()), method, payload)
            .flatMap(
                node -> {
                  TypeReference<OkxResponse<List<OkxOrderResponse>>> typeReference =
                      new TypeReference<>() {};
                  OkxResponse<List<OkxOrderResponse>> response =
                      mapper.treeToValue(node, typeReference);
                  OkxOrderResponse first =
                      response.getData() == null || response.getData().isEmpty()
                          ? null
                          : response.getData().get(0);
                  String orderCode = first == null ? null : first.getCode();
                  if (response.isSuccess() && (orderCode == null || "0".equals(orderCode))) {
                    if (first != null
                        && clientOrderId != null
                        && !clientOrderId.isEmpty()
                        && first.getClientOrderId() != null
                        && !first.getClientOrderId().isEmpty()
                        && !clientOrderId.equals(first.getClientOrderId())) {
                      throw new OrderNotValidException(
                          "OKX order response clOrdId '"
                              + first.getClientOrderId()
                              + "' does not match the requested client order id '"
                              + clientOrderId
                              + "'");
                    }
                    return Observable.just(0);
                  }
                  String errorCode = orderCode != null ? orderCode : response.getCode();
                  String errorMessage =
                      first != null && first.getMessage() != null
                          ? first.getMessage()
                          : response.getMsg();
                  throw new OrderNotValidException(
                      "OKX rejected order request (code "
                          + errorCode
                          + "): "
                          + (errorMessage == null ? "unknown error" : errorMessage));
                });
    return observable
        .firstOrError()
        .compose(
            RateLimiterOperator.of(
                resilienceRegistries.rateLimiters().rateLimiter(rateLimiterPath)));
  }

  /**
   * Canonical dedupe key for one order-event: re-delivered events carrying the same order id and
   * identical fill/state attributes are dropped.
   */
  private static String orderEventKey(OkxOrderDetails details) {
    return details.getOrderId()
        + "|"
        + details.getUpdateTime()
        + "|"
        + details.getState()
        + "|"
        + details.getLastTradeId()
        + "|"
        + details.getLastFilledPrice()
        + "|"
        + details.getLastFilledQuantity()
        + "|"
        + details.getLastFilledTime()
        + "|"
        + details.getFee();
  }

  /** Canonical dedupe key for one position event. */
  private static String positionEventKey(OkxPosition position) {
    return position.getPositionId()
        + "|"
        + position.getInstrumentId()
        + "|"
        + position.getPositionSide()
        + "|"
        + position.getUpdateTime()
        + "|"
        + position.getPosition()
        + "|"
        + position.getAverageOpenPrice();
  }
}
