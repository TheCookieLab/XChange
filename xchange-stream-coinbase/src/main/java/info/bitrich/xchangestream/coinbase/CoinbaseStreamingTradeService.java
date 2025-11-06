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
import java.util.concurrent.atomic.AtomicLong;
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
  
  // Track terminal orders that have already been processed to prevent duplicate emissions
  // when duplicate or late events arrive after the entry has been removed from the map.
  // Maps order ID to timestamp (milliseconds since epoch) when the terminal order was processed.
  // Entries are pruned periodically to prevent unbounded memory growth.
  private final ConcurrentHashMap<String, Long> processedTerminalOrders = new ConcurrentHashMap<>();
  
  // Counter for opportunistic cleanup - trigger cleanup every N operations
  private final AtomicLong operationCount = new AtomicLong(0);
  private static final long CLEANUP_INTERVAL = 100; // Cleanup every 100 operations
  private static final long TERMINAL_ORDER_TTL_MS = 10 * 60 * 1000; // 10 minutes TTL

  CoinbaseStreamingTradeService(
      CoinbaseStreamingService streamingService,
      org.knowm.xchange.ExchangeSpecification specification) {
    this.streamingService = streamingService;
    this.specification = specification;
  }

  void resubscribe() {
    // Clear the tracking map on reconnection to prevent memory leaks.
    // The streaming service will send a fresh snapshot with current state,
    // so we don't need to maintain stale cumulative quantity data.
    lastCumulativeQuantity.clear();
    processedTerminalOrders.clear();
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
        .flatMap(event -> toUserTrade(event).map(Observable::just).orElse(Observable.empty()));
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
    return streamFilledTrades(Collections.emptyList())
        .flatMap(event -> toUserTrade(event).map(Observable::just).orElse(Observable.empty()));
  }

  private Observable<CoinbaseUserOrderEvent> streamFilledTrades(List<String> productIds) {
    return getUserOrderEvents(productIds)
        .filter(
            event -> {
              // Basic validation: only process events with filled size > 0
              // The actual delta calculation and filtering happens atomically in toUserTrade
              return event.getFilledSize() != null
                  && event.getFilledSize().compareTo(BigDecimal.ZERO) > 0;
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
      String orderId = orderNode.path("order_id").asText(null);
      if (orderId == null || orderId.isEmpty()) {
        LOG.warn("Skipping Coinbase user order event without order_id: {}", orderNode);
        return Optional.empty();
      }

      String productId = orderNode.path("product_id").asText(null);
      CurrencyPair pair = CoinbaseStreamingAdapters.toCurrencyPair(productId);
      if (pair == null) {
        LOG.warn("Skipping Coinbase user order event with unknown product_id: {}", orderNode);
        return Optional.empty();
      }

      Order.OrderType side =
          CoinbaseStreamingAdapters.parseOrderSide(orderNode.path("order_side").asText(null));
      BigDecimal orderSize =
          Optional.ofNullable(CoinbaseStreamingAdapters.asBigDecimal(orderNode, "size"))
              .orElse(Optional.ofNullable(CoinbaseStreamingAdapters.asBigDecimal(orderNode, "order_total")).orElse(null));
      return Optional.of(
          new CoinbaseUserOrderEvent(
              orderId,
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

  /**
   * Converts a CoinbaseUserOrderEvent to a UserTrade, atomically calculating the delta
   * to prevent race conditions. Returns Optional.empty() if the delta is zero or negative
   * (no new fill occurred).
   */
  private Optional<UserTrade> toUserTrade(CoinbaseUserOrderEvent event) {
    BigDecimal price =
        Optional.ofNullable(event.getAveragePrice()).orElse(event.getLimitPrice());
    
    // Atomically calculate delta and update the tracking map
    // This prevents race conditions where multiple events for the same orderId
    // are processed concurrently between filter and map operations
    String orderId = event.getOrderId();
    BigDecimal currentCumulative = event.getFilledSize();
    String status = event.getStatus();
    boolean isTerminal = status != null && isTerminalStatus(status);
    
    // Use compute() to atomically get-calculate-put and handle terminal status cleanup
    // This ensures the delta calculation, map update, and removal (if terminal) happen atomically
    // per orderId, preventing race conditions where removal happens between compute() operations
    BigDecimal deltaAmount = calculateAndUpdateDelta(orderId, currentCumulative, isTerminal);
    
    // Filter out events with no new fill (delta <= 0)
    // This prevents emitting duplicate or zero-amount trades
    if (deltaAmount == null || deltaAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return Optional.empty();
    }
    
    return Optional.of(
        UserTrade.builder()
            .type(event.getSide())
            .instrument(event.getProduct())
            .price(price)
            .originalAmount(deltaAmount)
            .timestamp(event.getEventTime() == null ? null : Date.from(event.getEventTime()))
            .orderId(event.getOrderId())
            .build());
  }
  
  /**
   * Atomically calculates the delta (incremental fill amount) and updates the tracking map.
   * This method uses ConcurrentHashMap.compute() to ensure thread-safety when multiple events
   * for the same orderId are processed concurrently. If the order is in a terminal status,
   * the entry is removed atomically as part of the compute operation to prevent race conditions.
   * 
   * This method also tracks terminal orders that have already been processed to prevent
   * duplicate emissions when duplicate or late events arrive after the entry has been
   * removed from the tracking map. The tracking uses timestamps with periodic cleanup
   * to prevent unbounded memory growth.
   * 
   * @param orderId The order ID
   * @param currentCumulative The current cumulative filled quantity
   * @param isTerminal Whether the order is in a terminal status (should be removed after processing)
   * @return The delta amount (incremental fill since last update)
   */
  private BigDecimal calculateAndUpdateDelta(String orderId, BigDecimal currentCumulative, boolean isTerminal) {
    if (currentCumulative == null) {
      return BigDecimal.ZERO;
    }
    
    // For terminal orders, check if we've already processed this order.
    // If putIfAbsent() returns a non-null value, it means the order was already in the map
    // (already processed), so we should return zero delta to prevent duplicate emissions.
    // This check must happen BEFORE compute() to prevent race conditions where multiple threads
    // could both pass the check and proceed to compute() concurrently.
    // By checking and adding atomically (via putIfAbsent() which returns existing value if present),
    // we ensure only one thread processes a terminal order.
    if (isTerminal) {
      long now = System.currentTimeMillis();
      Long existingTimestamp = processedTerminalOrders.putIfAbsent(orderId, now);
      if (existingTimestamp != null) {
        // Order was already in the map (already processed) - return zero delta
        // Trigger periodic cleanup even for duplicate checks to ensure old entries are pruned
        maybeCleanupProcessedTerminalOrders();
        return BigDecimal.ZERO;
      }
      // Trigger opportunistic cleanup periodically for new terminal orders
      maybeCleanupProcessedTerminalOrders();
    }
    
    // Use compute() to atomically capture the old value, conditionally update, and handle removal
    // The lambda receives the existing value (oldValue) and returns the new value (or null to remove)
    // Moving the removal inside compute() ensures it's atomic with the delta calculation,
    // preventing race conditions where another thread's compute() would see null as existingValue
    final BigDecimal[] oldValue = new BigDecimal[1];
    
    lastCumulativeQuantity.compute(
        orderId,
        (key, existingValue) -> {
          oldValue[0] = existingValue; // Capture the old value before updating
          
          // Only update if this is the first event or if new value is greater than old value
          // This prevents updating the map with decreasing or equal cumulative quantities,
          // which would cause incorrect delta calculations for subsequent events
          BigDecimal newValue;
          if (existingValue == null) {
            newValue = currentCumulative; // First event - always update
          } else if (currentCumulative.compareTo(existingValue) > 0) {
            newValue = currentCumulative; // New value is greater - update
          } else {
            newValue = existingValue; // New value is less or equal - keep old value
          }
          
          // If order is in terminal status, remove the entry to prevent memory leaks
          // Returning null from compute() removes the entry atomically
          return isTerminal ? null : newValue;
        });
    
    // Calculate delta using the captured old value
    BigDecimal delta;
    if (oldValue[0] == null) {
      // First event for this order - delta is the full cumulative amount
      delta = currentCumulative;
    } else {
      // Calculate the incremental fill amount (delta since last update)
      delta = currentCumulative.subtract(oldValue[0]);
      // Only return positive deltas (filter out zero or negative deltas)
      if (delta.compareTo(BigDecimal.ZERO) <= 0) {
        delta = BigDecimal.ZERO;
      }
    }
    
    // Note: Terminal orders are tracked in processedTerminalOrders with timestamps.
    // Periodic cleanup removes entries older than TERMINAL_ORDER_TTL_MS to prevent
    // unbounded memory growth while still preventing duplicate emissions for a reasonable
    // time window (10 minutes). Duplicate events from Coinbase should not arrive that late.
    
    return delta;
  }
  
  /**
   * Periodically cleans up old entries from processedTerminalOrders to prevent unbounded
   * memory growth. This method is called opportunistically (every CLEANUP_INTERVAL operations)
   * to avoid overhead on every operation.
   */
  private void maybeCleanupProcessedTerminalOrders() {
    long count = operationCount.incrementAndGet();
    if (count % CLEANUP_INTERVAL == 0) {
      long now = System.currentTimeMillis();
      long cutoffTime = now - TERMINAL_ORDER_TTL_MS;
      
      // Remove entries older than the TTL
      processedTerminalOrders.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }
  }

  private void ensureAuthenticated() {
    if (specification == null
        || specification.getApiKey() == null
        || specification.getSecretKey() == null) {
      throw new ExchangeSecurityException(
          "Coinbase streaming private channels require API credentials");
    }
  }

  /**
   * Checks if an order status indicates the order is in a terminal state.
   * Terminal orders will no longer receive updates, so we can safely remove
   * their tracking data to prevent memory leaks.
   */
  private static boolean isTerminalStatus(String status) {
    if (status == null) {
      return false;
    }
    String upperStatus = status.toUpperCase();
    return upperStatus.equals("FILLED")
        || upperStatus.equals("CANCELLED")
        || upperStatus.equals("CANCELED")
        || upperStatus.equals("REJECTED")
        || upperStatus.equals("EXPIRED")
        || upperStatus.equals("SETTLED");
  }

  private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
    Iterable<JsonNode> iterable = array::elements;
    return java.util.stream.StreamSupport.stream(iterable.spliterator(), false);
  }
}
