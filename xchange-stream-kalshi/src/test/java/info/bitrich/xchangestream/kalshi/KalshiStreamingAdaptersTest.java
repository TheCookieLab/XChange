package info.bitrich.xchangestream.kalshi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import info.bitrich.xchangestream.kalshi.dto.KalshiWsFill;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsTicker;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsTrade;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsUserOrder;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Side/price truth tables for {@link KalshiStreamingAdapters}. The WebSocket payloads are
 * dollar-denominated, but the YES-leg side rules must equal the named REST adapter rules; these
 * tables pin that equivalence per the PRD's no-silent-complement rule.
 */
class KalshiStreamingAdaptersTest {

  private static final String TICKER = "KXSB-26";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("kalshi", null, TICKER, "YES", Currency.USD);

  @Test
  void publicTradeTakerSideTruthTable() {
    assertEquals(OrderType.BID, adaptTradeType("yes"));
    assertEquals(OrderType.ASK, adaptTradeType("no"));
  }

  @Test
  void fillSideTruthTableMatchesTheRestLegacyNoComplementRule() {
    assertEquals(OrderType.BID, adaptFillType("buy", "yes"));
    assertEquals(OrderType.ASK, adaptFillType("sell", "yes"));
    assertEquals(OrderType.ASK, adaptFillType("buy", "no"));
    assertEquals(OrderType.BID, adaptFillType("sell", "no"));
  }

  @Test
  void fillIsAlwaysPricedAtTheYesLegAndCarriesUsdFees() {
    UserTrade fill =
        KalshiStreamingAdapters.adaptFill(
            new KalshiWsFill(
                "t-1", "o-1", TICKER, true, "no", "buy", "0.750", "10.00", "0.25", null,
                1671899397000L));
    assertEquals(new BigDecimal("0.750"), fill.getPrice(), "always yes_price_dollars");
    assertEquals(new BigDecimal("0.25"), fill.getFeeAmount());
    assertEquals(Currency.USD, fill.getFeeCurrency());
    assertEquals(CONTRACT, fill.getInstrument());
    assertEquals(new Date(1671899397000L), fill.getTimestamp());
  }

  @Test
  void userOrderBookSideTruthTable() {
    assertEquals(OrderType.BID, adaptUserOrderType("bid"));
    assertEquals(OrderType.ASK, adaptUserOrderType("ask"));
  }

  @Test
  void unrecognizedBookSideIsRejectedNeverGuessed() {
    ExchangeException error =
        assertThrows(ExchangeException.class, () -> adaptUserOrderType("straddle"));
    assertEquals("Kalshi user order has unrecognized book_side: straddle", error.getMessage());
  }

  @Test
  void userOrderStatusTruthTable() {
    assertEquals(OrderStatus.OPEN, adaptStatus("resting", "0.00"));
    assertEquals(OrderStatus.PARTIALLY_FILLED, adaptStatus("resting", "4.00"));
    assertEquals(OrderStatus.CANCELED, adaptStatus("canceled", "4.00"));
    assertEquals(OrderStatus.FILLED, adaptStatus("executed", "10.00"));
    assertEquals(OrderStatus.UNKNOWN, adaptStatus("pending", "0.00"));
  }

  @Test
  void userOrderCarriesReferenceAndCumulativeFill() {
    LimitOrder order =
        KalshiStreamingAdapters.adaptUserOrder(
            new KalshiWsUserOrder(
                "o-1", TICKER, "resting", "yes", "bid", "0.3500", "4.00", "6.00", "10.00",
                "ref-9", 1733047200000L));
    assertEquals(new BigDecimal("10.00"), order.getOriginalAmount());
    assertEquals(new BigDecimal("4.00"), order.getCumulativeAmount());
    assertEquals("ref-9", order.getUserReference());
    assertEquals(new Date(1733047200000L), order.getTimestamp());
  }

  @Test
  void tickerPinsTopOfBookFields() {
    Ticker ticker =
        KalshiStreamingAdapters.adaptTicker(
            new KalshiWsTicker(TICKER, "0.480", "0.450", "0.530", "33896.00", 1669149841000L));
    assertEquals(new BigDecimal("0.450"), ticker.getBid());
    assertEquals(new BigDecimal("0.530"), ticker.getAsk());
    assertEquals(new BigDecimal("0.480"), ticker.getLast());
    assertEquals(new BigDecimal("33896.00"), ticker.getVolume());
    assertEquals(CONTRACT, ticker.getInstrument());
    assertEquals(new Date(1669149841000L), ticker.getTimestamp());
  }

  @Test
  void blankAndNullNumbersStayNullRatherThanExploding() {
    Ticker ticker =
        KalshiStreamingAdapters.adaptTicker(
            new KalshiWsTicker(TICKER, null, "", "0.530", null, null));
    assertNull(ticker.getLast());
    assertNull(ticker.getBid());
    assertNull(ticker.getVolume());
    assertNull(ticker.getTimestamp());
    assertEquals(new BigDecimal("0.530"), ticker.getAsk());
  }

  private static OrderType adaptTradeType(String takerSide) {
    return KalshiStreamingAdapters.adaptTrade(
            new KalshiWsTrade("t-1", TICKER, "0.360", "100.00", takerSide, 1669149841000L))
        .getType();
  }

  private static OrderType adaptFillType(String action, String side) {
    return KalshiStreamingAdapters.adaptFill(
            new KalshiWsFill(
                "t-1", "o-1", TICKER, true, side, action, "0.500", "1.00", null, null, null))
        .getType();
  }

  private static OrderType adaptUserOrderType(String bookSide) {
    return KalshiStreamingAdapters.adaptUserOrder(
            new KalshiWsUserOrder(
                "o-1", TICKER, "resting", "yes", bookSide, "0.500", "0.00", "1.00", "1.00", null,
                null))
        .getType();
  }

  private static OrderStatus adaptStatus(String status, String filled) {
    return KalshiStreamingAdapters.adaptUserOrder(
            new KalshiWsUserOrder(
                "o-1", TICKER, status, "yes", "bid", "0.500", filled, "6.00", "10.00", null,
                null))
        .getStatus();
  }
}
