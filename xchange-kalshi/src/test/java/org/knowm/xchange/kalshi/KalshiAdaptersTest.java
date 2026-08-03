package org.knowm.xchange.kalshi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.kalshi.dto.account.KalshiBalanceResponse;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse.KalshiMarketPosition;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse.KalshiOrderBookLevels;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse.KalshiFill;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrder;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderFlags;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderRequest;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Truth tables for the named Kalshi provider rules in {@link KalshiAdapters}. Every side/price
 * conversion is pinned here exactly once; service tests assert wiring only.
 */
class KalshiAdaptersTest {

  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract(
          "kalshi", "KXBTC-25DEC31", "KXBTC-25DEC31-T90000", "YES", Currency.USD);

  @Test
  void adaptContractKeepsEventAndMarketIdentity() {
    KalshiMarket market =
        new KalshiMarket(
            "KXBTC-25DEC31-T90000", "KXBTC-25DEC31", "BTC above 90000?", "open", 53, 54, 52, 1000L, 500L);
    PredictionMarketContract contract = KalshiAdapters.adaptContract(market);
    assertEquals("kalshi", contract.getProvider());
    assertEquals("KXBTC-25DEC31", contract.getEventId());
    assertEquals("KXBTC-25DEC31-T90000", contract.getMarketId());
    assertEquals("YES", contract.getOutcomeId());
    assertEquals(Currency.USD, contract.getCounter());
    assertEquals(
        "PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD", contract.toString());
  }

  /** Rule under test: {@link KalshiAdapters#RULE_YES_LEG_ONLY}. */
  @Test
  void toNativeSideMapsGenericBidAskToYesLegSides() {
    assertEquals("bid", KalshiAdapters.toNativeSide(OrderType.BID));
    assertEquals("ask", KalshiAdapters.toNativeSide(OrderType.ASK));
  }

  @Test
  void toCreateOrderRequestSerializesFixedPointDollarStrings() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(new BigDecimal("10"))
            .limitPrice(new BigDecimal("0.56"))
            .userReference("ref-1")
            .build();
    KalshiOrderRequest request = KalshiAdapters.toCreateOrderRequest(order);
    assertEquals("KXBTC-25DEC31-T90000", request.ticker());
    assertEquals("ref-1", request.clientOrderId());
    assertEquals("bid", request.side());
    assertEquals("0.5600", request.price());
    assertEquals("10.00", request.count());
    assertEquals("good_till_canceled", request.timeInForce());
    assertEquals("taker_at_cross", request.selfTradePreventionType());
    assertNull(request.postOnly());
    assertNull(request.cancelOrderOnPause());
    assertNull(request.reduceOnly());
  }

  @Test
  void toCreateOrderRequestMapsFlagsToNativeFields() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.ASK, CONTRACT)
            .originalAmount(new BigDecimal("2.5"))
            .limitPrice(new BigDecimal("0.4"))
            .flag(KalshiOrderFlags.POST_ONLY)
            .flag(KalshiOrderFlags.CANCEL_ON_PAUSE)
            .flag(KalshiOrderFlags.REDUCE_ONLY)
            .flag(KalshiOrderFlags.IMMEDIATE_OR_CANCEL)
            .build();
    KalshiOrderRequest request = KalshiAdapters.toCreateOrderRequest(order);
    assertEquals("ask", request.side());
    assertEquals("0.4000", request.price());
    assertEquals("2.50", request.count());
    assertEquals("immediate_or_cancel", request.timeInForce());
    assertEquals(Boolean.TRUE, request.postOnly());
    assertEquals(Boolean.TRUE, request.cancelOrderOnPause());
    assertEquals(Boolean.TRUE, request.reduceOnly());
  }

  @Test
  void fillOrKillTakesPrecedenceOverImmediateOrCancel() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(new BigDecimal("0.5"))
            .flag(KalshiOrderFlags.IMMEDIATE_OR_CANCEL)
            .flag(KalshiOrderFlags.FILL_OR_KILL)
            .build();
    assertEquals("fill_or_kill", KalshiAdapters.toCreateOrderRequest(order).timeInForce());
  }

  /** Rule under test: {@link KalshiAdapters#RULE_SIDE_NO_REJECTED}. */
  @Test
  void sideNoFlagIsRejectedAndNeverComplemented() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(new BigDecimal("0.5"))
            .flag(KalshiOrderFlags.SIDE_NO)
            .build();
    NotAvailableFromExchangeException exception =
        assertThrows(
            NotAvailableFromExchangeException.class,
            () -> KalshiAdapters.toCreateOrderRequest(order));
    assertEquals(KalshiAdapters.RULE_SIDE_NO_REJECTED, exception.getMessage());
  }

  @Test
  void limitPriceOutsideUnitIntervalIsRejected() {
    for (String price : List.of("0", "1", "1.5", "-0.1")) {
      LimitOrder order =
          new LimitOrder.Builder(OrderType.BID, CONTRACT)
              .originalAmount(BigDecimal.ONE)
              .limitPrice(new BigDecimal(price))
              .build();
      assertThrows(
          IllegalArgumentException.class, () -> KalshiAdapters.toCreateOrderRequest(order));
    }
  }

  @Test
  void nonKalshiInstrumentsAreRejected() {
    assertThrows(
        InstrumentNotValidException.class,
        () -> KalshiAdapters.marketTicker(CurrencyPair.BTC_USD));
    PredictionMarketContract otherProvider =
        new PredictionMarketContract("polymarket", null, "m-1", "YES", Currency.USD);
    assertThrows(
        InstrumentNotValidException.class, () -> KalshiAdapters.marketTicker(otherProvider));
  }

  /** Rule under test: {@link KalshiAdapters#RULE_NO_BID_COMPLEMENT}. */
  @Test
  void orderBookNoBidsBecomeYesAsksAtComplementPrice() {
    KalshiOrderBookResponse response =
        new KalshiOrderBookResponse(
            new KalshiOrderBookLevels(
                List.of(List.of(50, 10), List.of(53, 100)), List.of(List.of(45, 80))));
    OrderBook book = KalshiAdapters.adaptOrderBook("KXBTC-25DEC31-T90000", response);

    assertEquals(2, book.getBids().size());
    assertEquals(1, book.getAsks().size());

    // Bids are YES levels in cents, sorted best-first.
    assertEquals(new BigDecimal("0.53"), book.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("100"), book.getBids().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("0.50"), book.getBids().get(1).getLimitPrice());

    // The NO bid at 45c is a YES ask at (100 - 45) / 100 = 0.55 dollars.
    LimitOrder ask = book.getAsks().get(0);
    assertEquals(OrderType.ASK, ask.getType());
    assertEquals(new BigDecimal("0.55"), ask.getLimitPrice());
    assertEquals(new BigDecimal("80"), ask.getOriginalAmount());
  }

  /** Rule under test: {@link KalshiAdapters#RULE_LEGACY_NO_COMPLEMENT}. */
  @Test
  void legacyOrderTruthTable() {
    assertAdaptedOrder("buy", "yes", 53, 45, OrderType.BID, "0.53");
    assertAdaptedOrder("sell", "yes", 53, 45, OrderType.ASK, "0.53");
    assertAdaptedOrder("buy", "no", 53, 45, OrderType.ASK, "0.55");
    assertAdaptedOrder("sell", "no", 53, 45, OrderType.BID, "0.55");
  }

  private void assertAdaptedOrder(
      String action,
      String side,
      int yesPrice,
      int noPrice,
      OrderType expectedType,
      String expectedPrice) {
    KalshiOrder order =
        new KalshiOrder(
            "ord-1", "ref-1", "KXBTC-25DEC31-T90000", action, side, "resting",
            yesPrice, noPrice, 10, 0, 10, "2026-01-01T00:00:00Z");
    LimitOrder adapted = KalshiAdapters.adaptOrder(order);
    assertEquals(expectedType, adapted.getType(), action + "/" + side);
    assertEquals(new BigDecimal(expectedPrice), adapted.getLimitPrice(), action + "/" + side);
    assertEquals(new BigDecimal("10"), adapted.getOriginalAmount());
    assertEquals("ord-1", adapted.getId());
    assertEquals("ref-1", adapted.getUserReference());
    assertEquals(OrderStatus.OPEN, adapted.getStatus());
  }

  @Test
  void orderStatusMapping() {
    assertEquals(OrderStatus.OPEN, statusOf("resting", 0));
    assertEquals(OrderStatus.PARTIALLY_FILLED, statusOf("resting", 2));
    assertEquals(OrderStatus.CANCELED, statusOf("canceled", 0));
    assertEquals(OrderStatus.FILLED, statusOf("executed", 10));
    assertEquals(OrderStatus.PENDING_NEW, statusOf("pending", 0));
    assertEquals(OrderStatus.UNKNOWN, statusOf("something-new", 0));
  }

  private OrderStatus statusOf(String status, int fillCount) {
    return KalshiAdapters.adaptOrderStatus(
        new KalshiOrder(
            "ord-1", null, "T", "buy", "yes", status, 50, 50, 10, fillCount, 10, null));
  }

  /** Rule under test: {@link KalshiAdapters#RULE_LEGACY_NO_COMPLEMENT} on the fills surface. */
  @Test
  void legacyFillTruthTable() {
    KalshiFill buyNo =
        new KalshiFill(
            "fill-1", "ord-1", "KXBTC-25DEC31-T90000", "buy", "no", 3, 55, 45,
            "2026-01-01T00:00:00Z");
    var trade = KalshiAdapters.adaptFill(buyNo);
    assertEquals(OrderType.ASK, trade.getType());
    assertEquals(new BigDecimal("0.55"), trade.getPrice());
    assertEquals(new BigDecimal("3"), trade.getOriginalAmount());
    assertEquals("fill-1", trade.getId());
    assertEquals("ord-1", trade.getOrderId());

    KalshiFill sellNo =
        new KalshiFill(
            "fill-2", "ord-1", "KXBTC-25DEC31-T90000", "sell", "no", 3, 55, 45,
            "2026-01-01T00:00:00Z");
    assertEquals(OrderType.BID, KalshiAdapters.adaptFill(sellNo).getType());
    assertEquals(new BigDecimal("0.55"), KalshiAdapters.adaptFill(sellNo).getPrice());
  }

  @Test
  void balanceAdaptsCentsToDollars() {
    AccountInfo info = KalshiAdapters.adaptAccountInfo(new KalshiBalanceResponse(15230L, 5000L));
    var balance = info.getWallet().getBalance(Currency.USD);
    assertEquals(new BigDecimal("152.30"), balance.getTotal());
    assertEquals(new BigDecimal("152.30"), balance.getAvailable());
  }

  @Test
  void negativePositionsAdaptToShort() {
    List<OpenPosition> positions =
        KalshiAdapters.adaptPositions(
            List.of(
                new KalshiMarketPosition("LONG-MKT", 5L, 260L),
                new KalshiMarketPosition("SHORT-MKT", -3L, 120L)));
    assertEquals(OpenPosition.Type.LONG, positions.get(0).getType());
    assertEquals(new BigDecimal("5"), positions.get(0).getSize());
    assertEquals(OpenPosition.Type.SHORT, positions.get(1).getType());
    assertEquals(new BigDecimal("3"), positions.get(1).getSize());
  }
}
