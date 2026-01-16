package info.bitrich.xchangestream.coinbase.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingCandle;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingEvent;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingLevel2Update;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingMessage;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingTicker;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingTrade;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
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

    private static final ObjectMapper OBJECT_MAPPER = StreamingObjectMapperHelper.getObjectMapper();
    private static final CoinbaseStreamingMessage EMPTY_MESSAGE =
        new CoinbaseStreamingMessage(null, Collections.emptyList());

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
        return adaptTickers(toStreamingMessage(message));
    }

    public static List<Ticker> adaptTickers(CoinbaseStreamingMessage message) {
        if (message == null || message.getEvents().isEmpty()) {
            return Collections.emptyList();
        }
        List<Ticker> tickers = new ArrayList<>();
        for (CoinbaseStreamingEvent event : message.getEvents()) {
            for (CoinbaseStreamingTicker ticker : event.getTickers()) {
                CurrencyPair pair = toCurrencyPair(ticker.getProductId());
                if (pair == null) {
                    continue;
                }
                Ticker.Builder builder = new Ticker.Builder();
                builder.instrument(pair);
                builder.last(ticker.getPrice());
                builder.volume(ticker.getVolume24H());
                builder.high(ticker.getHigh24H());
                builder.low(ticker.getLow24H());
                builder.open(ticker.getOpen24H());
                builder.bid(ticker.getBestBid());
                builder.ask(ticker.getBestAsk());
                builder.timestamp(parseInstant(ticker.getTime()).map(java.util.Date::from).orElse(null));
                tickers.add(builder.build());
            }
        }
        return tickers;
    }

    public static List<Trade> adaptTrades(JsonNode message) {
        return adaptTrades(toStreamingMessage(message));
    }

    public static List<Trade> adaptTrades(CoinbaseStreamingMessage message) {
        if (message == null || message.getEvents().isEmpty()) {
            return Collections.emptyList();
        }
        List<Trade> trades = new ArrayList<>();
        for (CoinbaseStreamingEvent event : message.getEvents()) {
            for (CoinbaseStreamingTrade trade : event.getTrades()) {
                CurrencyPair pair = toCurrencyPair(trade.getProductId());
                if (pair == null) {
                    continue;
                }
                Trade mappedTrade = UserTrade.builder()
                    .instrument(pair)
                    .id(trade.getTradeId())
                    .price(trade.getPrice())
                    .originalAmount(trade.getSize())
                    .timestamp(parseInstant(trade.getTime()).map(java.util.Date::from).orElse(null))
                    .type(parseOrderSide(trade.getSide()))
                    .build();
                trades.add(mappedTrade);
            }
        }
        return trades;
    }

    public static List<CandleStick> adaptCandles(JsonNode message, CurrencyPair targetPair) {
        return adaptCandles(toStreamingMessage(message), targetPair);
    }

    public static List<CandleStick> adaptCandles(CoinbaseStreamingMessage message, CurrencyPair targetPair) {
        if (message == null || message.getEvents().isEmpty()) {
            return Collections.emptyList();
        }
        List<CandleStick> candlesList = new ArrayList<>();
        for (CoinbaseStreamingEvent event : message.getEvents()) {
            for (CoinbaseStreamingCandle candle : event.getCandles()) {
                CurrencyPair pair = toCurrencyPair(candle.getProductId());
                if (pair == null) {
                    continue;
                }
                if (targetPair != null && !targetPair.equals(pair)) {
                    continue;
                }
                candlesList.add(new CandleStick.Builder()
                    .open(candle.getOpen())
                    .close(candle.getClose())
                    .high(candle.getHigh())
                    .low(candle.getLow())
                    .volume(candle.getVolume())
                    .timestamp(parseUnixTimestamp(candle.getStart()).map(java.util.Date::from).orElse(null))
                    .build());
            }
        }
        return candlesList;
    }

    public static List<LimitOrder> adaptLevel2Updates(JsonNode eventNode, Order.OrderType side) {
        CoinbaseStreamingEvent event = toStreamingEvent(eventNode);
        if (event == null) {
            return Collections.emptyList();
        }
        return adaptLevel2Updates(event, side);
    }

    public static List<LimitOrder> adaptLevel2Updates(CoinbaseStreamingEvent event, Order.OrderType side) {
        List<LimitOrder> orders = new ArrayList<>();
        if (event.getUpdates().isEmpty()) {
            return orders;
        }
        CurrencyPair pair = toCurrencyPair(event.getProductId());
        if (pair == null) {
            return orders;
        }
        for (CoinbaseStreamingLevel2Update update : event.getUpdates()) {
            Order.OrderType orderSide = parseOrderSide(update.getSide());
            if (orderSide != side) {
                continue;
            }
            BigDecimal price = update.getPriceLevel();
            BigDecimal amount = update.getNewQuantity();
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
        return parseInstant(text);
    }

    public static Optional<Instant> parseUnixTimestamp(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        return parseUnixTimestamp(node.asText(null));
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

    public static Optional<Instant> parseInstant(String text) {
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

    public static Optional<Instant> parseUnixTimestamp(String text) {
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

    public static CoinbaseStreamingMessage toStreamingMessage(JsonNode message) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return EMPTY_MESSAGE;
        }
        try {
            return OBJECT_MAPPER.treeToValue(message, CoinbaseStreamingMessage.class);
        } catch (JsonProcessingException e) {
            return EMPTY_MESSAGE;
        }
    }

    public static CoinbaseStreamingEvent toStreamingEvent(JsonNode event) {
        if (event == null || event.isMissingNode() || event.isNull()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.treeToValue(event, CoinbaseStreamingEvent.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
