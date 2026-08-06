package org.knowm.xchange.kalshi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.kalshi.dto.account.KalshiBalanceResponse;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse.KalshiMarketPosition;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket.KalshiPriceRange;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiOrderBookResponse.KalshiOrderBookLevels;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse.KalshiFill;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrder;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderFlags;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderRequest;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Truth tables for the named Kalshi provider rules in {@link KalshiAdapters}, pinned to the
 * current fixed-point wire schema (4-decimal dollar prices, 2-decimal fp counts, canonical
 * {@code book_side} direction). Every side/price conversion is pinned here exactly once; service
 * tests assert wiring only.
 */
class KalshiAdaptersTest {

  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract(
          "kalshi", "KXBTC-25DEC31", "KXBTC-25DEC31-T90000", "YES", Currency.USD);

  private static final KalshiPriceRange RANGE_EDGE =
      new KalshiPriceRange("0.0000", "0.1000", "0.0010");
  private static final KalshiPriceRange RANGE_CENTER =
      new KalshiPriceRange("0.1000", "0.9000", "0.0100");

  @Test
  void adaptContractKeepsEventAndMarketIdentity() {
    KalshiMarket market = market("0.5300", "0.5400", "0.5200", "1000.00", "500.00");
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
    assertEquals("0.56", request.price());
    assertEquals("10", request.count());
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
    assertEquals("0.4", request.price());
    assertEquals("2.5", request.count());
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

  /**
   * Subpenny and boundary prices are representable and must be preserved verbatim, never
   * rounded: prices carry up to 4 decimal places (Kalshi {@code *_dollars} strings), and the
   * exact grid edges 0.0001 and 0.9999 are valid.
   */
  @Test
  void subpennyAndBoundaryPricesArePreserved() {
    assertEquals("0.0001", priceString("0.0001"));
    assertEquals("0.9999", priceString("0.9999"));
    assertEquals("0.4217", priceString("0.4217"));
    assertEquals("0.42170", priceString("0.42170"));
    assertEquals("0.5", priceString("0.5"));
  }

  /** Fractional counts are representable and preserved: up to 2 decimal places. */
  @Test
  void fractionalCountsArePreserved() {
    assertEquals("12.50", countString("12.50"));
    assertEquals("12.5", countString("12.5"));
    assertEquals("13", countString("13"));
    assertEquals("0.01", countString("0.01"));
  }

  /**
   * Off-grid values that cannot be represented without rounding are rejected, never silently
   * rounded into a materially different instruction: 5-decimal prices and 3-decimal counts are
   * outside the fixed-point grid.
   */
  @Test
  void offGridPricesAndCountsAreRejectedWithoutRounding() {
    for (String price : List.of("0.42175", "0.00001", "0.99999")) {
      assertThrows(IllegalArgumentException.class, () -> priceString(price), price);
    }
    for (String count : List.of("12.555", "0.001", "1.999")) {
      assertThrows(IllegalArgumentException.class, () -> countString(count), count);
    }
  }

  @Test
  void zeroAndNegativeCountsAreRejected() {
    for (String count : List.of("0", "-1", "-0.01")) {
      assertThrows(IllegalArgumentException.class, () -> countString(count), count);
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
                List.of(List.of("0.5000", "10.00"), List.of("0.5300", "100.00")),
                List.of(List.of("0.4500", "80.00"))));
    OrderBook book = KalshiAdapters.adaptOrderBook("KXBTC-25DEC31-T90000", response);

    assertThat(book.getBids()).hasSize(2);
    assertThat(book.getAsks()).hasSize(1);

    // Bids are YES levels as [price_dollars, count_fp] pairs, sorted best-first.
    assertThat(book.getBids().get(0).getLimitPrice()).isEqualByComparingTo("0.5300");
    assertThat(book.getBids().get(0).getOriginalAmount()).isEqualByComparingTo("100.00");
    assertThat(book.getBids().get(1).getLimitPrice()).isEqualByComparingTo("0.5000");

    // The NO bid at 0.45 is a YES ask at 1 - 0.45 = 0.55 dollars.
    LimitOrder ask = book.getAsks().get(0);
    assertEquals(OrderType.ASK, ask.getType());
    assertThat(ask.getLimitPrice()).isEqualByComparingTo("0.5500");
    assertThat(ask.getOriginalAmount()).isEqualByComparingTo("80.00");
  }

  /**
   * Rule under test: {@link KalshiAdapters#RULE_BOOK_SIDE_DIRECTION}. Direction comes from the
   * canonical {@code book_side} ({@code bid} = buy YES, {@code ask} = sell YES) and the price is
   * always the YES-leg {@code yes_price_dollars}; a NO-positioned record at no-leg price q reads
   * as the YES complement (1 - q).
   */
  @Test
  void bookSideDirectionTruthTableOnOrders() {
    assertAdaptedOrder("bid", "0.5300", OrderType.BID, "0.5300");
    assertAdaptedOrder("ask", "0.5300", OrderType.ASK, "0.5300");
    // buy NO at 0.45 is economically an ASK YES at 0.55; the provider reports yes_price 0.5500.
    assertAdaptedOrder("ask", "0.5500", OrderType.ASK, "0.5500");
  }

  private void assertAdaptedOrder(
      String bookSide, String yesPrice, OrderType expectedType, String expectedPrice) {
    KalshiOrder order =
        new KalshiOrder(
            "ord-1",
            "ref-1",
            "KXBTC-25DEC31-T90000",
            bookSide,
            "resting",
            yesPrice,
            "10.00",
            "0.00",
            "10.00",
            "2026-01-01T00:00:00Z");
    LimitOrder adapted = KalshiAdapters.adaptOrder(order);
    assertEquals(expectedType, adapted.getType(), bookSide);
    assertThat(adapted.getLimitPrice()).isEqualByComparingTo(expectedPrice);
    assertThat(adapted.getOriginalAmount()).isEqualByComparingTo("10.00");
    assertEquals("ord-1", adapted.getId());
    assertEquals("ref-1", adapted.getUserReference());
    assertEquals(OrderStatus.OPEN, adapted.getStatus());
  }

  @Test
  void orderStatusMapping() {
    assertEquals(OrderStatus.OPEN, statusOf("resting", "0.00"));
    assertEquals(OrderStatus.PARTIALLY_FILLED, statusOf("resting", "2.50"));
    assertEquals(OrderStatus.CANCELED, statusOf("canceled", "0.00"));
    assertEquals(OrderStatus.FILLED, statusOf("executed", "10.00"));
    assertEquals(OrderStatus.UNKNOWN, statusOf("something-new", "0.00"));
  }

  private OrderStatus statusOf(String status, String fillCountFp) {
    return KalshiAdapters.adaptOrderStatus(
        new KalshiOrder(
            "ord-1", null, "T", "bid", status, "0.5000", "10.00", fillCountFp, "10.00", null));
  }

  /** Rule under test: {@link KalshiAdapters#RULE_BOOK_SIDE_DIRECTION} on the fills surface. */
  @Test
  void bookSideDirectionTruthTableOnFills() {
    KalshiFill buyYes =
        new KalshiFill(
            "fill-1", "ord-1", "KXBTC-25DEC31-T90000", "bid", "5.50", "0.5500",
            "2026-01-01T00:00:00Z");
    var trade = KalshiAdapters.adaptFill(buyYes);
    assertEquals(OrderType.BID, trade.getType());
    assertThat(trade.getPrice()).isEqualByComparingTo("0.5500");
    assertThat(trade.getOriginalAmount()).isEqualByComparingTo("5.50");
    assertEquals("fill-1", trade.getId());
    assertEquals("ord-1", trade.getOrderId());

    KalshiFill sellYes =
        new KalshiFill(
            "fill-2", "ord-1", "KXBTC-25DEC31-T90000", "ask", "3.00", "0.4217",
            "2026-01-01T00:00:00Z");
    var sell = KalshiAdapters.adaptFill(sellYes);
    assertEquals(OrderType.ASK, sell.getType());
    assertThat(sell.getPrice()).isEqualByComparingTo("0.4217");
  }

  @Test
  void balanceAdaptsDollarsString() {
    AccountInfo info =
        KalshiAdapters.adaptAccountInfo(
            new KalshiBalanceResponse("152.30", 15230L, 5000L, 1754230000000L));
    var balance = info.getWallet().getBalance(Currency.USD);
    assertThat(balance.getTotal()).isEqualByComparingTo("152.30");
    assertThat(balance.getAvailable()).isEqualByComparingTo("152.30");
  }

  @Test
  void negativePositionsAdaptToShort() {
    List<OpenPosition> positions =
        KalshiAdapters.adaptPositions(
            List.of(
                new KalshiMarketPosition("LONG-MKT", "5.50", "260.00"),
                new KalshiMarketPosition("SHORT-MKT", "-3.25", "120.00")));
    assertEquals(OpenPosition.Type.LONG, positions.get(0).getType());
    assertThat(positions.get(0).getSize()).isEqualByComparingTo("5.50");
    assertEquals(OpenPosition.Type.SHORT, positions.get(1).getType());
    assertThat(positions.get(1).getSize()).isEqualByComparingTo("3.25");
  }

  /**
   * {@link KalshiAdapters#adaptMetadata} must derive the price grid from the market's {@code
   * price_ranges} (finest band step), not assume a one-cent tick; fractional contracts make the
   * amount step and minimum 0.01, and the contract value comes from {@code
   * notional_value_dollars}.
   */
  @Test
  void adaptMetadataDerivesGridFromPriceRanges() {
    KalshiMarket market =
        new KalshiMarket(
            "KXBTC-25DEC31-T90000",
            "KXBTC-25DEC31",
            "BTC above 90000?",
            "active",
            "0.4217",
            "0.4400",
            "0.4300",
            "1000.50",
            "500.00",
            "1.0000",
            List.of(RANGE_EDGE, RANGE_CENTER));

    InstrumentMetaData metadata = KalshiAdapters.adaptMetadata(market);
    assertEquals(4, metadata.getPriceScale());
    assertEquals(2, metadata.getVolumeScale());
    assertThat(metadata.getPriceStepSize()).isEqualByComparingTo("0.0010");
    assertThat(metadata.getAmountStepSize()).isEqualByComparingTo("0.01");
    assertThat(metadata.getMinimumAmount()).isEqualByComparingTo("0.01");
    assertThat(metadata.getContractValue()).isEqualByComparingTo("1.0000");
  }

  /** Without {@code price_ranges} the metadata falls back to the whole-cent tick. */
  @Test
  void adaptMetadataFallsBackToCentTickWithoutPriceRanges() {
    KalshiMarket bareMarket =
        new KalshiMarket(
            "KXBTC-25DEC31-T90000",
            "KXBTC-25DEC31",
            "BTC above 90000?",
            "active",
            "0.5300",
            "0.5400",
            "0.5200",
            "1000.00",
            "500.00",
            null,
            null);
    InstrumentMetaData metadata = KalshiAdapters.adaptMetadata(bareMarket);
    assertThat(metadata.getPriceStepSize()).isEqualByComparingTo("0.01");
    assertThat(metadata.getContractValue()).isEqualByComparingTo("1");
  }

  private static String priceString(String price) {
    return KalshiAdapters.toKalshiPriceString(new BigDecimal(price));
  }

  private static String countString(String count) {
    return KalshiAdapters.toKalshiCountString(new BigDecimal(count));
  }

  private static KalshiMarket market(
      String yesBid, String yesAsk, String last, String volumeFp, String openInterestFp) {
    return new KalshiMarket(
        "KXBTC-25DEC31-T90000",
        "KXBTC-25DEC31",
        "BTC above 90000?",
        "active",
        yesBid,
        yesAsk,
        last,
        volumeFp,
        openInterestFp,
        "1.0000",
        List.of(RANGE_EDGE, RANGE_CENTER));
  }
}
