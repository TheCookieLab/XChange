package info.bitrich.xchangestream.coinbasederivatives;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPosition.MarginMode;
import org.knowm.xchange.dto.account.OpenPosition.Type;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.FundingRate.FundingRateInterval;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/** Exact-decimal adapters for Coinbase derivatives subscription payloads. */
public final class CoinbaseDerivativesStreamingAdapters {

  private CoinbaseDerivativesStreamingAdapters() {}

  /** Maps the provider's discovered native perpetual name to an XChange futures contract. */
  public static FuturesContract toInstrument(String nativeName) {
    return (FuturesContract) CoinbaseDerivativesAdapters.toInstrument(nativeName);
  }

  /** Maps an XChange futures contract to the provider's native channel symbol. */
  public static String toNativeName(Instrument instrument) {
    if (!(instrument instanceof FuturesContract)) {
      throw new IllegalArgumentException(
          "Coinbase derivatives streaming requires a FuturesContract");
    }
    return CoinbaseDerivativesAdapters.toNativeName(instrument);
  }

  static Ticker toTicker(JsonNode data) {
    return new Ticker.Builder()
        .instrument(toInstrument(requiredText(data, "instrument_name")))
        .last(decimal(data, "last_price"))
        .bid(decimal(data, "best_bid_price"))
        .ask(decimal(data, "best_ask_price"))
        .bidSize(decimal(data, "best_bid_amount"))
        .askSize(decimal(data, "best_ask_amount"))
        .high(decimal(data.path("stats"), "high"))
        .low(decimal(data.path("stats"), "low"))
        .volume(decimal(data.path("stats"), "volume"))
        .quoteVolume(decimal(data.path("stats"), "volume_usd"))
        .percentageChange(decimal(data.path("stats"), "price_change"))
        .timestamp(date(data, "timestamp"))
        .build();
  }

  static List<Trade> toTrades(JsonNode data) {
    List<Trade> trades = new ArrayList<>();
    Iterable<JsonNode> events = data.isArray() ? data : List.of(data);
    for (JsonNode event : events) {
      trades.add(
          Trade.builder()
              .type(side(event.path("direction").asText()))
              .originalAmount(decimalRequired(event, "amount"))
              .instrument(toInstrument(requiredText(event, "instrument_name")))
              .price(decimalRequired(event, "price"))
              .timestamp(date(event, "timestamp"))
              .id(requiredText(event, "trade_id"))
              .build());
    }
    return trades;
  }

  static List<OrderBookUpdate> toOrderBookUpdates(JsonNode data) {
    Instrument instrument = toInstrument(requiredText(data, "instrument_name"));
    Date timestamp = date(data, "timestamp");
    List<OrderBookUpdate> updates = new ArrayList<>();
    addBookSide(updates, data.path("asks"), OrderType.ASK, instrument, timestamp);
    addBookSide(updates, data.path("bids"), OrderType.BID, instrument, timestamp);
    return updates;
  }

  private static void addBookSide(
      List<OrderBookUpdate> updates,
      JsonNode levels,
      OrderType type,
      Instrument instrument,
      Date timestamp) {
    if (!levels.isArray()) {
      return;
    }
    for (JsonNode level : levels) {
      if (!level.isArray() || level.size() < 3) {
        throw new CoinbaseDerivativesStreamException("Malformed order-book level");
      }
      BigDecimal price = exactDecimal(level.get(1));
      BigDecimal amount =
          "delete".equals(level.get(0).asText()) ? BigDecimal.ZERO : exactDecimal(level.get(2));
      updates.add(new OrderBookUpdate(type, amount, instrument, price, timestamp, amount));
    }
  }

  static FundingRate toFundingRate(JsonNode data) {
    return new FundingRate.Builder()
        .instrument(toInstrument(requiredText(data, "instrument_name")))
        .fundingRate(decimal(data, "funding_8h"))
        .fundingRate1h(decimal(data, "current_funding"))
        .fundingRateInterval(FundingRateInterval.H8)
        .fundingRateDate(date(data, "timestamp"))
        .build();
  }

  static CandleStickData toCandleStickData(Instrument instrument, JsonNode data) {
    CandleStick candle =
        new CandleStick.Builder()
            .timestamp(instant(data, "tick"))
            .open(decimalRequired(data, "open"))
            .last(decimalRequired(data, "close"))
            .high(decimalRequired(data, "high"))
            .low(decimalRequired(data, "low"))
            .close(decimalRequired(data, "close"))
            .volume(decimalRequired(data, "volume"))
            .quotaVolume(decimal(data, "cost"))
            .completed(false)
            .build();
    return new CandleStickData(instrument, List.of(candle));
  }

  static Order toOrder(JsonNode order) {
    BigDecimal amount = decimalRequired(order, "amount");
    OrderType side = side(order.path("direction").asText());
    Instrument instrument = toInstrument(requiredText(order, "instrument_name"));
    String id = requiredText(order, "order_id");
    Date timestamp = date(order, "last_update_timestamp");
    BigDecimal averagePrice = decimal(order, "average_price");
    BigDecimal filledAmount = decimal(order, "filled_amount");
    BigDecimal fee = decimal(order, "commission");
    OrderStatus status = orderStatus(order.path("order_state").asText());
    String label = text(order, "label");
    String orderType = order.path("order_type").asText();
    if (orderType.contains("stop") || order.hasNonNull("trigger_price")) {
      return new StopOrder(
          side,
          amount,
          instrument,
          id,
          timestamp,
          decimal(order, "trigger_price"),
          orderType.contains("limit") ? decimal(order, "price") : null,
          averagePrice,
          filledAmount,
          fee,
          status,
          label,
          null,
          null);
    }
    if ("market".equals(orderType)) {
      return new MarketOrder(
          side, amount, instrument, id, timestamp, averagePrice, filledAmount, fee, status, label);
    }
    return new LimitOrder(
        side,
        amount,
        instrument,
        id,
        timestamp,
        decimal(order, "price"),
        averagePrice,
        filledAmount,
        fee,
        status,
        label);
  }

  static UserTrade toUserTrade(JsonNode trade) {
    return UserTrade.builder()
        .orderId(requiredText(trade, "order_id"))
        .feeAmount(decimal(trade, "fee"))
        .feeCurrency(
            trade.hasNonNull("fee_currency")
                ? Currency.getInstance(trade.get("fee_currency").asText())
                : null)
        .orderUserReference(text(trade, "label"))
        .type(side(trade.path("direction").asText()))
        .originalAmount(decimalRequired(trade, "amount"))
        .instrument(toInstrument(requiredText(trade, "instrument_name")))
        .price(decimalRequired(trade, "price"))
        .timestamp(date(trade, "timestamp"))
        .id(requiredText(trade, "trade_id"))
        .build();
  }

  static OpenPosition toPosition(JsonNode position) {
    BigDecimal size =
        position.hasNonNull("size_currency")
            ? decimalRequired(position, "size_currency")
            : decimalRequired(position, "size");
    String direction = position.path("direction").asText();
    return OpenPosition.builder()
        .instrument(toInstrument(requiredText(position, "instrument_name")))
        .type("sell".equalsIgnoreCase(direction) ? Type.SHORT : Type.LONG)
        .marginMode(MarginMode.CROSS)
        .size(size)
        .price(decimal(position, "average_price"))
        .liquidationPrice(decimal(position, "estimated_liquidation_price"))
        .unRealisedPnl(decimal(position, "floating_profit_loss"))
        .updatedAt(instant(position, "timestamp"))
        .build();
  }

  static Balance toBalance(JsonNode portfolio) {
    Currency currency = Currency.getInstance(requiredText(portfolio, "currency"));
    BigDecimal total =
        portfolio.hasNonNull("equity")
            ? decimalRequired(portfolio, "equity")
            : decimalRequired(portfolio, "balance");
    BigDecimal available = decimalRequired(portfolio, "available_funds");
    return new Balance.Builder()
        .currency(currency)
        .total(total)
        .available(available)
        .frozen(total.subtract(available))
        .build();
  }

  static JsonNode data(JsonNode notification) {
    return notification.path("data");
  }

  private static OrderType side(String direction) {
    return "sell".equalsIgnoreCase(direction) ? OrderType.ASK : OrderType.BID;
  }

  private static OrderStatus orderStatus(String status) {
    switch (status.toLowerCase(Locale.ROOT)) {
      case "open":
      case "untriggered":
        return OrderStatus.NEW;
      case "filled":
        return OrderStatus.FILLED;
      case "cancelled":
        return OrderStatus.CANCELED;
      case "rejected":
        return OrderStatus.REJECTED;
      default:
        return OrderStatus.UNKNOWN;
    }
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    return node != null && node.hasNonNull(field) ? exactDecimal(node.get(field)) : null;
  }

  private static BigDecimal decimalRequired(JsonNode node, String field) {
    if (node == null || !node.hasNonNull(field)) {
      throw new CoinbaseDerivativesStreamException("Subscription payload is missing " + field);
    }
    return exactDecimal(node.get(field));
  }

  private static BigDecimal exactDecimal(JsonNode value) {
    if (!value.isNumber()) {
      throw new CoinbaseDerivativesStreamException("Wire numeric is not a finite decimal");
    }
    try {
      return value.decimalValue();
    } catch (NumberFormatException failure) {
      throw new CoinbaseDerivativesStreamException("Wire numeric is not a finite decimal", failure);
    }
  }

  private static Date date(JsonNode node, String field) {
    Instant instant = instant(node, field);
    return instant == null ? null : Date.from(instant);
  }

  private static Instant instant(JsonNode node, String field) {
    if (node == null || !node.hasNonNull(field)) {
      return null;
    }
    JsonNode value = node.get(field);
    return value.isNumber() ? Instant.ofEpochMilli(value.asLong()) : Instant.parse(value.asText());
  }

  private static String requiredText(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || value.isBlank()) {
      throw new CoinbaseDerivativesStreamException("Subscription payload is missing " + field);
    }
    return value;
  }

  private static String text(JsonNode node, String field) {
    return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
  }
}
