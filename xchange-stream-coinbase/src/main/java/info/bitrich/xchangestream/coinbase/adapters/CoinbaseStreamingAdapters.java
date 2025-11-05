package info.bitrich.xchangestream.coinbase.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

public final class CoinbaseStreamingAdapters {

    private CoinbaseStreamingAdapters() {
    }

    public static CurrencyPair toCurrencyPair(String productId) {
        if (productId == null) {
            return null;
        }
        String[] tokens = productId.split("-");
        if (tokens.length != 2) {
            return null;
        }
        return new CurrencyPair(tokens[0], tokens[1]);
    }

    public static List<Ticker> adaptTickers(JsonNode message) {
        JsonNode events = message.path("events");
        if (!events.isArray()) {
            return Collections.emptyList();
        }
        List<Ticker> tickers = new ArrayList<>();
        for (JsonNode event : events) {
            JsonNode tickerNodes = event.path("tickers");
            if (!tickerNodes.isArray()) {
                continue;
            }
            for (JsonNode node : tickerNodes) {
                CurrencyPair pair = toCurrencyPair(node.path("product_id").asText(null));
                if (pair == null) {
                    continue;
                }
                Ticker.Builder builder = new Ticker.Builder();
                builder.instrument(pair);
                builder.last(asBigDecimal(node, "price"));
                builder.volume(asBigDecimal(node, "volume_24_h"));
                builder.high(asBigDecimal(node, "high_24_h"));
                builder.low(asBigDecimal(node, "low_24_h"));
                builder.open(asBigDecimal(node, "open_24_h"));
                builder.bid(asBigDecimal(node, "best_bid"));
                builder.ask(asBigDecimal(node, "best_ask"));
                builder.timestamp(asInstant(node.path("time")).map(java.util.Date::from).orElse(null));
                tickers.add(builder.build());
            }
        }
        return tickers;
    }

    public static List<Trade> adaptTrades(JsonNode message) {
        JsonNode events = message.path("events");
        if (!events.isArray()) {
            return Collections.emptyList();
        }
        List<Trade> trades = new ArrayList<>();
        for (JsonNode event : events) {
            JsonNode tradeNodes = event.path("trades");
            if (!tradeNodes.isArray()) {
                continue;
            }
            for (JsonNode node : tradeNodes) {
                CurrencyPair pair = toCurrencyPair(node.path("product_id").asText(null));
                if (pair == null) {
                    continue;
                }
                Trade trade = UserTrade.builder().instrument(pair).id(node.path("trade_id").asText(null)).price(asBigDecimal(node, "price")).originalAmount(asBigDecimal(node, "size")).timestamp(asInstant(node.path("time")).map(java.util.Date::from).orElse(null)).type(parseOrderSide(node.path("side").asText(null))).build();
                trades.add(trade);
            }
        }
        return trades;
    }

    public static List<CandleStick> adaptCandles(JsonNode message, CurrencyPair targetPair) {
        JsonNode events = message.path("events");
        if (!events.isArray()) {
            return Collections.emptyList();
        }
        List<CandleStick> candlesList = new ArrayList<>();
        for (JsonNode event : events) {
            ArrayNode candles = (ArrayNode) event.path("candles");
            if (candles == null) {
                continue;
            }
            for (JsonNode candleNode : candles) {
                CurrencyPair pair = toCurrencyPair(candleNode.path("product_id").asText(null));
                if (pair == null) {
                    continue;
                }
                if (targetPair != null && !targetPair.equals(pair)) {
                    continue;
                }
                candlesList.add(new CandleStick.Builder().open(asBigDecimal(candleNode, "open")).close(asBigDecimal(candleNode, "close")).high(asBigDecimal(candleNode, "high")).low(asBigDecimal(candleNode, "low")).volume(asBigDecimal(candleNode, "volume")).timestamp(parseUnixTimestamp(candleNode.path("start")).map(java.util.Date::from).orElse(null)).build());
            }
        }
        return candlesList;
    }

    public static List<LimitOrder> adaptLevel2Updates(JsonNode eventNode, Order.OrderType side) {
        List<LimitOrder> orders = new ArrayList<>();
        ArrayNode updates = (ArrayNode) eventNode.path("updates");
        if (updates == null) {
            return orders;
        }
        String productId = eventNode.path("product_id").asText(null);
        CurrencyPair pair = toCurrencyPair(productId);
        if (pair == null) {
            return orders;
        }
        Iterator<JsonNode> iterator = updates.iterator();
        while (iterator.hasNext()) {
            JsonNode update = iterator.next();
            Order.OrderType orderSide = parseOrderSide(update.path("side").asText(null));
            if (orderSide != side) {
                continue;
            }
            BigDecimal price = asBigDecimal(update, "price_level");
            BigDecimal amount = asBigDecimal(update, "new_quantity");
            if (price == null || amount == null) {
                continue;
            }
            orders.add(new LimitOrder(orderSide, amount, pair, null, null, price));
        }
        return orders;
    }

    public static Optional<Instant> asInstant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        String text = node.asText(null);
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(text));
        } catch (Exception e) {
            try {
                return Optional.of(ZonedDateTime.parse(text).toInstant());
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
    }

    public static Optional<Instant> parseUnixTimestamp(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        String text = node.asText(null);
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        try {
            long epochSeconds = Long.parseLong(text);
            return Optional.of(Instant.ofEpochSecond(epochSeconds));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static BigDecimal asBigDecimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String text = valueNode.asText(null);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Order.OrderType parseOrderSide(String side) {
        if (side == null) {
            return null;
        }
        switch (side.toLowerCase(Locale.ROOT)) {
            case "buy":
            case "bid":
                return Order.OrderType.BID;
            case "sell":
            case "ask":
                return Order.OrderType.ASK;
            default:
                return null;
        }
    }

}
