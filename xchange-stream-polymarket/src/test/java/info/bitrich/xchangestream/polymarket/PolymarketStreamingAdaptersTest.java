package info.bitrich.xchangestream.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsBook;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsLastTradePrice;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsOrder;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsPriceChange;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsTrade;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Field-level truth tables for {@link PolymarketStreamingAdapters}: side rules, status mapping,
 * taker/maker fill expansion, and null/blank pass-through, driven by parsed wire payloads.
 */
class PolymarketStreamingAdaptersTest {

  private static final String CONDITION_ID =
      "0x9b0f6b43e1a44c2fb2d3a1e5c7d8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6";
  private static final String TOKEN_ID =
      "104173557214744537570424345347209544585775842950109756851652855913015295508992";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, PolymarketAdapters.COLLATERAL);
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  @Test
  void bookAdapterSortsWorstFirstLevelsIntoBestFirstGenericDepth() throws Exception {
    PolymarketWsBook book =
        MAPPER.treeToValue(
            MAPPER.readTree(
                "{\"market\":\""
                    + CONDITION_ID
                    + "\",\"asset_id\":\""
                    + TOKEN_ID
                    + "\",\"timestamp\":\"1669149841000\",\"hash\":\"0xaaa\","
                    + "\"bids\":[{\"price\":\"0.40\",\"size\":\"300\"},"
                    + "{\"price\":\"0.44\",\"size\":\"100\"}],"
                    + "\"asks\":[{\"price\":\"0.60\",\"size\":\"250\"},"
                    + "{\"price\":\"0.56\",\"size\":\"150\"}]}"),
            PolymarketWsBook.class);

    OrderBook adapted = PolymarketStreamingAdapters.adaptBook(book);

    assertEquals(CONTRACT, adapted.getBids().get(0).getInstrument());
    assertEquals(new Date(1669149841000L), adapted.getTimeStamp());
    assertEquals(new BigDecimal("0.44"), adapted.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.40"), adapted.getBids().get(1).getLimitPrice());
    assertEquals(new BigDecimal("0.56"), adapted.getAsks().get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.60"), adapted.getAsks().get(1).getLimitPrice());

    Ticker ticker = PolymarketStreamingAdapters.adaptTicker(book);
    assertEquals(new BigDecimal("0.44"), ticker.getBid());
    assertEquals(new BigDecimal("0.56"), ticker.getAsk());
  }

  @Test
  void tickerFromPriceChangeReadsTheBestBidAskFields() throws Exception {
    PolymarketWsPriceChange.Change change =
        new PolymarketWsPriceChange.Change(
            TOKEN_ID, "0.44", "10", "BUY", "0xhash", "0.45", "0.55");

    Ticker ticker =
        PolymarketStreamingAdapters.adaptTicker(change, CONDITION_ID, "1669149842000");

    assertEquals(CONTRACT, ticker.getInstrument());
    assertEquals(new BigDecimal("0.45"), ticker.getBid());
    assertEquals(new BigDecimal("0.55"), ticker.getAsk());
    assertEquals(new Date(1669149842000L), ticker.getTimestamp());
  }

  @Test
  void lastTradePriceFollowsTheRestSideRule() {
    Trade sell =
        PolymarketStreamingAdapters.adaptLastTradePrice(
            new PolymarketWsLastTradePrice(
                CONDITION_ID, TOKEN_ID, "0.56", "4", "0", "SELL", "1669149841500", "0xtx"));
    assertEquals(OrderType.ASK, sell.getType(), "a SELL aggressor reads as ask-side");
    assertEquals(CONTRACT, sell.getInstrument());
    assertEquals("0xtx", sell.getId());

    Trade buy =
        PolymarketStreamingAdapters.adaptLastTradePrice(
            new PolymarketWsLastTradePrice(
                CONDITION_ID, TOKEN_ID, "0.56", "4", "0", "buy", "1669149841500", null));
    assertEquals(OrderType.BID, buy.getType(), "side matching is case-insensitive");
    assertNull(buy.getId(), "a null transaction hash stays a null id");
  }

  @Test
  void orderSideAndStatusTruthTable() {
    assertEquals(OrderType.BID, PolymarketStreamingAdapters.adaptOrder(order("BUY", "LIVE", "0")).getType());
    assertEquals(
        OrderType.ASK, PolymarketStreamingAdapters.adaptOrder(order("SELL", "LIVE", "0")).getType());

    assertEquals(
        OrderStatus.OPEN, PolymarketStreamingAdapters.adaptOrder(order("BUY", "LIVE", "0")).getStatus());
    assertEquals(
        OrderStatus.PARTIALLY_FILLED,
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "LIVE", "3")).getStatus());
    assertEquals(
        OrderStatus.FILLED,
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "MATCHED", "10")).getStatus());
    assertEquals(
        OrderStatus.CANCELED,
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "CANCELED", "2")).getStatus());
    assertEquals(
        OrderStatus.PENDING_NEW,
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "DELAYED", "0")).getStatus());
    assertEquals(
        OrderStatus.UNKNOWN,
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "SETTLING", "0")).getStatus());
    assertEquals(
        OrderStatus.OPEN,
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "live", "0")).getStatus(),
        "status matching is case-insensitive");
  }

  @Test
  void unfilledOrdersHaveNoCumulativeAmountAndBlankNumbersStayNull() {
    LimitOrder unfilled = PolymarketStreamingAdapters.adaptOrder(order("BUY", "LIVE", "0"));
    assertNull(unfilled.getCumulativeAmount());

    LimitOrder blankAmounts =
        PolymarketStreamingAdapters.adaptOrder(order("BUY", "LIVE", ""));
    assertNull(blankAmounts.getCumulativeAmount());
  }

  @Test
  void userTradesExpandByTraderSide() {
    PolymarketWsTrade taker =
        new PolymarketWsTrade(
            "trade-1", "order-9", CONDITION_ID, TOKEN_ID, "BUY", "6", "0", "0.56", "MATCHED",
            "1669149841", "TAKER", "1669149841000",
            List.of(new PolymarketWsTrade.MakerOrder("order-7", "6", "0.56", "SELL")));
    List<UserTrade> takerFills = PolymarketStreamingAdapters.adaptUserTrades(taker);
    assertEquals(1, takerFills.size());
    assertEquals("order-9", takerFills.get(0).getOrderId());
    assertEquals(OrderType.BID, takerFills.get(0).getType());

    PolymarketWsTrade maker =
        new PolymarketWsTrade(
            "trade-2", "order-x", CONDITION_ID, TOKEN_ID, "SELL", "9", "0", "0.56", "MATCHED",
            "1669149842", "MAKER", "1669149842000",
            List.of(
                new PolymarketWsTrade.MakerOrder("order-a", "5", "0.56", "BUY"),
                new PolymarketWsTrade.MakerOrder("order-b", "4", "0.55", "BUY")));
    List<UserTrade> makerFills = PolymarketStreamingAdapters.adaptUserTrades(maker);
    assertEquals(2, makerFills.size());
    assertEquals("order-a", makerFills.get(0).getOrderId());
    assertEquals(new BigDecimal("5"), makerFills.get(0).getOriginalAmount());
    assertEquals("order-b", makerFills.get(1).getOrderId());

    PolymarketWsTrade makerWithoutLegs =
        new PolymarketWsTrade(
            "trade-3", "order-x", CONDITION_ID, TOKEN_ID, "SELL", "1", "0", "0.56", "MATCHED",
            "1669149843", "MAKER", "1669149843000", null);
    assertTrue(
        PolymarketStreamingAdapters.adaptUserTrades(makerWithoutLegs).isEmpty(),
        "a maker event without legs yields no fills");

    PolymarketWsTrade unknown =
        new PolymarketWsTrade(
            "trade-4", "order-x", CONDITION_ID, TOKEN_ID, "BUY", "1", "0", "0.56", "MATCHED",
            "1669149844", "ARBITER", "1669149844000", null);
    ExchangeException error =
        assertThrows(
            ExchangeException.class, () -> PolymarketStreamingAdapters.adaptUserTrades(unknown));
    assertEquals(
        "Polymarket user trade has unrecognized trader_side: ARBITER", error.getMessage());
  }

  private static PolymarketWsOrder order(String side, String status, String sizeMatched) {
    return new PolymarketWsOrder(
        "order-1", CONDITION_ID, TOKEN_ID, side, "10", sizeMatched, "0.56", "PLACEMENT", status,
        "GTC", "1669149841", "1669149841000");
  }
}
