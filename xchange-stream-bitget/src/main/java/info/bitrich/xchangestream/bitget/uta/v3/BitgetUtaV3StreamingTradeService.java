package info.bitrich.xchangestream.bitget.uta.v3;

import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3FillData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3PositionData;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Adapters;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3UnknownOutcomeException;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CancelOrderParams;

/**
 * Bitget UTA v3 private WebSocket trade service.
 *
 * <p>The private channels are account-wide subscriptions (instType {@code UTA}, no symbol): the
 * {@code order} channel pushes order-state changes, the {@code fill} channel pushes one entry per
 * executed fill, and the {@code position} channel pushes position updates. Pushed {@code symbol}
 * (and, for orders/fills, {@code category}) identify the instrument; position pushes carry no
 * category, so the caller's instrument is used after matching on symbol.
 *
 * <p>Deduping is bounded (LRU) per channel, keyed by the provider id ({@code orderId}, {@code
 * execId}, {@code symbol+posSide}): updates are emitted only when the payload actually changed, and
 * the dedupe survives reconnects so a replayed snapshot does not re-emit stale state.
 *
 * <p>Placements are delegated to the REST {@link BitgetUtaV3TradeService} and answered with {@code
 * 0} (accepted). A client-generated {@code clientOid} is injected when the order carries none; if
 * the private socket disconnects while a placement is pending, the outcome is unknown and each
 * pending placement fails with {@link BitgetUtaV3UnknownOutcomeException} on {@link
 * #subscribePlacementFailures()} instead of being silently replayed.
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3StreamingTradeService implements StreamingTradeService {

  private static final int DEDUPE_CAPACITY = 1000;

  private final BitgetUtaV3PrivateStreamingService service;
  private final BitgetUtaV3TradeService restTradeService;

  private final ConcurrentHashMap.KeySetView<String, Boolean> pendingClientOids =
      ConcurrentHashMap.newKeySet();
  private final PublishSubject<Throwable> placementFailures = PublishSubject.create();

  public BitgetUtaV3StreamingTradeService(
      BitgetUtaV3PrivateStreamingService service, BitgetUtaV3TradeService restTradeService) {
    this.service = service;
    this.restTradeService = restTradeService;
    service
        .subscribeDisconnect()
        .subscribe(
            ignored -> failPendingPlacements(),
            throwable -> log.error("Bitget UTA v3 disconnect stream failed", throwable));
  }

  /** Dedicated stream for placement failures with unknown outcomes (never the placement stream). */
  public Observable<Throwable> subscribePlacementFailures() {
    return placementFailures.share();
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    return getOrderChanges((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("order").build();
    String expectedSymbol = BitgetUtaV3StreamingAdapters.toString(instrument);
    Map<String, Order> dedupe = boundedLru();
    return service
        .subscribeChannel(null, channel)
        .flatMap(notification -> Observable.fromIterable(notification.getPayloadItems()))
        .map(
            item ->
                Config.getInstance().getObjectMapper().treeToValue(item, BitgetUtaV3Order.class))
        .filter(dto -> expectedSymbol.equals(dto.getSymbol()))
        .filter(dto -> dto.getOrderId() != null)
        .flatMap(
            dto -> {
              Order order = BitgetUtaV3Adapters.toOrder(dto, instrument);
              Order previous = dedupe.put(dto.getOrderId(), order);
              if (order.equals(previous)) {
                return Observable.empty();
              }
              if (dto.getClientOid() != null) {
                pendingClientOids.remove(dto.getClientOid());
              }
              return Observable.just(order);
            });
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    return getUserTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("fill").build();
    String expectedSymbol = BitgetUtaV3StreamingAdapters.toString(instrument);
    Map<String, UserTrade> dedupe = boundedLru();
    return service
        .subscribeChannel(null, channel)
        .flatMap(notification -> Observable.fromIterable(notification.getPayloadItems()))
        .map(
            item ->
                Config.getInstance().getObjectMapper().treeToValue(item, BitgetUtaV3FillData.class))
        .filter(dto -> expectedSymbol.equals(dto.getSymbol()))
        .filter(dto -> dto.getExecId() != null)
        .flatMap(
            dto -> {
              UserTrade trade = BitgetUtaV3StreamingAdapters.toUserTrade(dto, instrument);
              UserTrade previous = dedupe.put(dto.getExecId(), trade);
              if (trade.equals(previous)) {
                return Observable.empty();
              }
              return Observable.just(trade);
            });
  }

  @Override
  public Observable<UserTrade> getUserTrades() {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("fill").build();
    Map<String, UserTrade> dedupe = boundedLru();
    return service
        .subscribeChannel(null, channel)
        .flatMap(notification -> Observable.fromIterable(notification.getPayloadItems()))
        .map(
            item ->
                Config.getInstance().getObjectMapper().treeToValue(item, BitgetUtaV3FillData.class))
        .filter(dto -> dto.getExecId() != null)
        .flatMap(
            dto -> {
              Instrument instrument =
                  BitgetUtaV3StreamingAdapters.toInstrument(dto.getCategory(), dto.getSymbol());
              UserTrade trade = BitgetUtaV3StreamingAdapters.toUserTrade(dto, instrument);
              UserTrade previous = dedupe.put(dto.getExecId(), trade);
              if (trade.equals(previous)) {
                return Observable.empty();
              }
              return Observable.just(trade);
            });
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder().instType(BitgetUtaV3InstType.UTA).topic("position").build();
    String expectedSymbol = BitgetUtaV3StreamingAdapters.toString(instrument);
    Map<String, OpenPosition> dedupe = boundedLru();
    return service
        .subscribeChannel(null, channel)
        .flatMap(notification -> Observable.fromIterable(notification.getPayloadItems()))
        .map(
            item ->
                Config.getInstance()
                    .getObjectMapper()
                    .treeToValue(item, BitgetUtaV3PositionData.class))
        .filter(dto -> expectedSymbol.equals(dto.getSymbol()))
        .flatMap(
            dto -> {
              OpenPosition position = BitgetUtaV3StreamingAdapters.toOpenPosition(dto, instrument);
              String key = dto.getSymbol() + "_" + dto.getPosSide();
              OpenPosition previous = dedupe.put(key, position);
              if (position.equals(previous)) {
                return Observable.empty();
              }
              return Observable.just(position);
            });
  }

  @Override
  public Single<Integer> placeMarketOrder(MarketOrder marketOrder, Object... args) {
    return placeOrder(
        marketOrder.getUserReference(), oid -> placeMarketOrderRest(marketOrder, oid));
  }

  @Override
  public Single<Integer> placeLimitOrder(LimitOrder limitOrder, Object... args) {
    return placeOrder(limitOrder.getUserReference(), oid -> placeLimitOrderRest(limitOrder, oid));
  }

  @Override
  public Single<Integer> cancelOrder(CancelOrderParams params, Object... args) {
    return Single.fromCallable(
        () -> {
          try {
            restTradeService.cancelOrder(params);
            return 0;
          } catch (IOException e) {
            throw new ExchangeException("Failed to cancel order on Bitget UTA v3", e);
          }
        });
  }

  private Single<Integer> placeOrder(String clientOid, PlaceOrder action) {
    final String effectiveClientOid =
        clientOid == null || clientOid.isEmpty()
            ? UUID.randomUUID().toString().replace("-", "")
            : clientOid;
    pendingClientOids.add(effectiveClientOid);
    return Single.fromCallable(
        () -> {
          try {
            action.place(effectiveClientOid);
            return 0;
          } catch (IOException e) {
            pendingClientOids.remove(effectiveClientOid);
            throw new ExchangeException("Failed to place order on Bitget UTA v3", e);
          }
        });
  }

  private String placeMarketOrderRest(MarketOrder marketOrder, String clientOid)
      throws IOException {
    if (clientOid.equals(marketOrder.getUserReference())) {
      return restTradeService.placeMarketOrder(marketOrder);
    }
    return restTradeService.placeMarketOrder(
        MarketOrder.Builder.from(marketOrder).userReference(clientOid).build());
  }

  private String placeLimitOrderRest(LimitOrder limitOrder, String clientOid) throws IOException {
    if (clientOid.equals(limitOrder.getUserReference())) {
      return restTradeService.placeLimitOrder(limitOrder);
    }
    return restTradeService.placeLimitOrder(
        LimitOrder.Builder.from(limitOrder).userReference(clientOid).build());
  }

  private void failPendingPlacements() {
    BitgetUtaV3Exception disconnect =
        BitgetUtaV3Exception.builder()
            .code("WS_DISCONNECT")
            .message(
                "Private WebSocket disconnected while order was pending; outcome unknown, "
                    + "reconcile by order id via trade/order-info")
            .build();
    pendingClientOids.stream()
        .map(clientOid -> new BitgetUtaV3UnknownOutcomeException(disconnect, clientOid))
        .forEach(placementFailures::onNext);
    pendingClientOids.clear();
  }

  @FunctionalInterface
  private interface PlaceOrder {
    void place(String clientOid) throws IOException;
  }

  private static <K, V> Map<K, V> boundedLru() {
    return new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > DEDUPE_CAPACITY;
      }
    };
  }
}
