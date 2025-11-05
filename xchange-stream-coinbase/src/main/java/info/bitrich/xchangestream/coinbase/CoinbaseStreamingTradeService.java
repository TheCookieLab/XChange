package info.bitrich.xchangestream.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseFuturesBalanceSummary;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseUserOrderEvent;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoinbaseStreamingTradeService implements StreamingTradeService {

  private static final Logger LOG =
      LoggerFactory.getLogger(CoinbaseStreamingTradeService.class);

  private final CoinbaseStreamingService streamingService;
  private final org.knowm.xchange.ExchangeSpecification specification;
  
  // Track last seen cumulative quantity per order ID to calculate deltas
  private final ConcurrentHashMap<String, BigDecimal> lastCumulativeQuantity = new ConcurrentHashMap<>();

  CoinbaseStreamingTradeService(
      CoinbaseStreamingService streamingService,
      org.knowm.xchange.ExchangeSpecification specification) {
    this.streamingService = streamingService;
    this.specification = specification;
  }

  void resubscribe() {
    // No-op: streaming service handles reconnection automatically.
  }

  public Observable<CoinbaseUserOrderEvent> getUserOrderEvents(List<String> productIds) {
    ensureAuthenticated();
    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(
            CoinbaseChannel.USER,
            productIds == null ? Collections.emptyList() : productIds,
            Collections.emptyMap());

    return streamingService
        .observeChannel(request)
        .flatMapIterable(this::extractOrderEvents);
  }

  public Observable<CoinbaseFuturesBalanceSummary> getFuturesBalanceSummary() {
    ensureAuthenticated();
    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(
            CoinbaseChannel.FUTURES_BALANCE_SUMMARY, Collections.emptyList(), Collections.emptyMap());

    return streamingService
        .observeChannel(request)
        .flatMapIterable(this::extractBalanceSummaries);
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    ensureAuthenticated();
    String productId = CoinbaseProductIds.productId(currencyPair);
    return getUserOrderEvents(Collections.singletonList(productId))
        .filter(event -> currencyPair.equals(event.getProduct()))
        .map(CoinbaseStreamingTradeService::toOrder);
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    if (instrument instanceof CurrencyPair) {
      return getOrderChanges((CurrencyPair) instrument, args);
    }
    return StreamingTradeService.super.getOrderChanges(instrument, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    ensureAuthenticated();
    String productId = CoinbaseProductIds.productId(currencyPair);
    return streamFilledTrades(Collections.singletonList(productId))
        .filter(event -> currencyPair.equals(event.getProduct()))
        .map(this::toUserTrade);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    if (instrument instanceof CurrencyPair) {
      return getUserTrades((CurrencyPair) instrument, args);
    }
    return StreamingTradeService.super.getUserTrades(instrument, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades() {
    ensureAuthenticated();
    return streamFilledTrades(Collections.emptyList()).map(this::toUserTrade);
  }

  private Observable<CoinbaseUserOrderEvent> streamFilledTrades(List<String> productIds) {
    return getUserOrderEvents(productIds)
        .filter(
            event -> {
              if (event.getFilledSize() == null
                  || event.getFilledSize().compareTo(BigDecimal.ZERO) <= 0) {
                return false;
              }
              
              // Check if there's a new fill (delta > 0)
              // We'll calculate the actual delta in toUserTrade to avoid double-processing
              String orderId = event.getOrderId();
              BigDecimal currentCumulative = event.getFilledSize();
              BigDecimal lastCumulative = lastCumulativeQuantity.get(orderId);
              
              if (lastCumulative == null) {
                // First time seeing this order - emit if it has any fills
                return currentCumulative.compareTo(BigDecimal.ZERO) > 0;
              }
              
              // Only emit if there's a positive delta (new fill occurred)
              BigDecimal delta = currentCumulative.subtract(lastCumulative);
              return delta.compareTo(BigDecimal.ZERO) > 0;
            });
  }

  private List<CoinbaseUserOrderEvent> extractOrderEvents(JsonNode message) {
    JsonNode events = message.path("events");
    if (!events.isArray()) {
      return Collections.emptyList();
    }
    return stream(events)
        .filter(event -> event.path("orders").isArray())
        .flatMap(
            event ->
                stream(event.path("orders"))
                    .map(orderNode -> toUserOrderEvent(orderNode, event)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private List<CoinbaseFuturesBalanceSummary> extractBalanceSummaries(JsonNode message) {
    JsonNode events = message.path("events");
    if (!events.isArray()) {
      return Collections.emptyList();
    }
    return stream(events)
        .map(event -> event.path("fcm_balance_summary"))
        .filter(JsonNode::isObject)
        .map(this::toBalanceSummary)
        .collect(Collectors.toList());
  }

  private CoinbaseFuturesBalanceSummary toBalanceSummary(JsonNode summaryNode) {
    return new CoinbaseFuturesBalanceSummary(
        CoinbaseStreamingAdapters.asBigDecimal(summaryNode, "futures_buying_power"),
        CoinbaseStreamingAdapters.asBigDecimal(summaryNode, "total_usd_balance"),
        CoinbaseStreamingAdapters.asBigDecimal(summaryNode, "unrealized_pnl"),
        CoinbaseStreamingAdapters.asBigDecimal(summaryNode, "daily_realized_pnl"),
        CoinbaseStreamingAdapters.asBigDecimal(summaryNode, "initial_margin"),
        CoinbaseStreamingAdapters.asBigDecimal(summaryNode, "available_margin"));
  }

  private Optional<CoinbaseUserOrderEvent> toUserOrderEvent(JsonNode orderNode, JsonNode event) {
    try {
      String productId = orderNode.path("product_id").asText(null);
      CurrencyPair pair = CoinbaseStreamingAdapters.toCurrencyPair(productId);
      Order.OrderType side =
          CoinbaseStreamingAdapters.parseOrderSide(orderNode.path("order_side").asText(null));
      BigDecimal orderSize =
          Optional.ofNullable(CoinbaseStreamingAdapters.asBigDecimal(orderNode, "size"))
              .orElse(Optional.ofNullable(CoinbaseStreamingAdapters.asBigDecimal(orderNode, "order_total")).orElse(null));
      return Optional.of(
          new CoinbaseUserOrderEvent(
              orderNode.path("order_id").asText(null),
              orderNode.path("client_order_id").asText(null),
              pair,
              side,
              orderNode.path("order_type").asText(null),
              CoinbaseStreamingAdapters.asBigDecimal(orderNode, "limit_price"),
              CoinbaseStreamingAdapters.asBigDecimal(orderNode, "avg_price"),
              orderSize,
              CoinbaseStreamingAdapters.asBigDecimal(orderNode, "cumulative_quantity"),
              CoinbaseStreamingAdapters.asBigDecimal(orderNode, "leaves_quantity"),
              orderNode.path("status").asText(null),
              CoinbaseStreamingAdapters.asInstant(orderNode.path("event_time")).orElse(null)));
    } catch (Exception ex) {
      LOG.warn("Failed to map user order event: {}", orderNode, ex);
      return Optional.empty();
    }
  }

  private static Order toOrder(CoinbaseUserOrderEvent event) {
    BigDecimal amount =
        Optional.ofNullable(event.getRemainingSize()).orElse(event.getSize());
    BigDecimal price =
        Optional.ofNullable(event.getLimitPrice()).orElse(event.getAveragePrice());
    return new org.knowm.xchange.dto.trade.LimitOrder(
        event.getSide(),
        amount,
        event.getProduct(),
        event.getOrderId(),
        event.getEventTime() == null ? null : Date.from(event.getEventTime()),
        price);
  }

  private UserTrade toUserTrade(CoinbaseUserOrderEvent event) {
    BigDecimal price =
        Optional.ofNullable(event.getAveragePrice()).orElse(event.getLimitPrice());
    
    // Calculate delta: current cumulative - last cumulative
    // This represents only the incremental fill, not the total
    String orderId = event.getOrderId();
    BigDecimal currentCumulative = event.getFilledSize();
    BigDecimal lastCumulative = lastCumulativeQuantity.get(orderId);
    
    BigDecimal deltaAmount;
    if (lastCumulative == null) {
      // First event for this order - use the full cumulative amount
      deltaAmount = currentCumulative != null ? currentCumulative : BigDecimal.ZERO;
    } else {
      // Calculate the incremental fill amount (delta since last update)
      deltaAmount = currentCumulative != null 
          ? currentCumulative.subtract(lastCumulative) 
          : BigDecimal.ZERO;
    }
    
    // Update the tracking map with the new cumulative quantity
    // This must happen after calculating the delta
    if (currentCumulative != null) {
      lastCumulativeQuantity.put(orderId, currentCumulative);
    }
    
    return UserTrade.builder()
        .type(event.getSide())
        .instrument(event.getProduct())
        .price(price)
        .originalAmount(deltaAmount)
        .timestamp(event.getEventTime() == null ? null : Date.from(event.getEventTime()))
        .orderId(event.getOrderId())
        .build();
  }

  private void ensureAuthenticated() {
    if (specification == null
        || specification.getApiKey() == null
        || specification.getSecretKey() == null) {
      throw new ExchangeSecurityException(
          "Coinbase streaming private channels require API credentials");
    }
  }

  private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
    Iterable<JsonNode> iterable = array::elements;
    return java.util.stream.StreamSupport.stream(iterable.spliterator(), false);
  }
}
