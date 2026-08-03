package org.knowm.xchange.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.polymarket.dto.account.PolymarketBalanceResponse;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataPosition;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataTrade;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse.PolymarketBookLevel;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOpenOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderFlags;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketUserTrade;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Truth tables for the named Polymarket adapter rules: {@link PolymarketAdapters#RULE_TOKEN_DIRECT},
 * {@link PolymarketAdapters#RULE_AMOUNT_ENCODING}, and
 * {@link PolymarketAdapters#RULE_NO_COMPLEMENT}.
 */
class PolymarketAdaptersTest {

  private static final String CONDITION_ID = "0xdd22472e";
  private static final String TOKEN_ID = "713210456792522125";
  private static final String OTHER_TOKEN_ID = "1041735572147445375";
  private static final String MAKER = "0x" + "11".repeat(20);
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, Currency.USD);

  // ---------- RULE_TOKEN_DIRECT ----------

  @Test
  void bidMapsToBuyOnTheContractToken() {
    PolymarketSignedOrder order =
        PolymarketAdapters.toSignedOrder(
            limitOrder(OrderType.BID, "10", "0.56"), MAKER, new BigDecimal("12345"), 1754230000000L);
    assertEquals("BUY", order.side());
    assertEquals(TOKEN_ID, order.tokenId());
    assertEquals(MAKER, order.maker());
    assertEquals(MAKER, order.signer());
  }

  @Test
  void askMapsToSellOnTheContractToken() {
    PolymarketSignedOrder order =
        PolymarketAdapters.toSignedOrder(
            limitOrder(OrderType.ASK, "10", "0.56"), MAKER, new BigDecimal("12345"), 1754230000000L);
    assertEquals("SELL", order.side());
    assertEquals(TOKEN_ID, order.tokenId());
  }

  // ---------- RULE_AMOUNT_ENCODING ----------

  @Test
  void buyPostsUsdcNotionalAsMakerAmount() {
    PolymarketSignedOrder order =
        PolymarketAdapters.toSignedOrder(
            limitOrder(OrderType.BID, "10", "0.56"), MAKER, new BigDecimal("12345"), 1754230000000L);
    assertEquals("5600000", order.makerAmount(), "10 x 0.56 USDC in micro-units");
    assertEquals("10000000", order.takerAmount(), "10 shares in micro-units");
    assertEquals("0", order.expiration());
    assertEquals("1754230000000", order.timestamp());
    assertEquals(0, order.signatureType());
    assertEquals("0x" + "00".repeat(32), order.builder());
    assertNull(order.metadata());
    assertNull(order.signature());
  }

  @Test
  void sellPostsSharesAsMakerAmount() {
    PolymarketSignedOrder order =
        PolymarketAdapters.toSignedOrder(
            limitOrder(OrderType.ASK, "10", "0.56"), MAKER, new BigDecimal("12345"), 1754230000000L);
    assertEquals("10000000", order.makerAmount(), "10 shares in micro-units");
    assertEquals("5600000", order.takerAmount(), "10 x 0.56 USDC in micro-units");
  }

  @Test
  void microUnitConversionRoundsHalfUp() {
    PolymarketSignedOrder order =
        PolymarketAdapters.toSignedOrder(
            limitOrder(OrderType.BID, "1.23456789", "0.5"),
            MAKER,
            new BigDecimal("12345"),
            1754230000000L);
    assertEquals("617284", order.makerAmount(), "617283.945 rounds half-up");
    assertEquals("1234568", order.takerAmount(), "1234567.89 rounds half-up");
  }

  @Test
  void pricesOutsideUnitIntervalAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PolymarketAdapters.toSignedOrder(
                limitOrder(OrderType.BID, "10", "0"), MAKER, BigDecimal.ONE, 0L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PolymarketAdapters.toSignedOrder(
                limitOrder(OrderType.BID, "10", "1"), MAKER, BigDecimal.ONE, 0L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PolymarketAdapters.toSignedOrder(
                limitOrder(OrderType.BID, "10", "-0.5"), MAKER, BigDecimal.ONE, 0L));
  }

  @Test
  void pricesFinerThanFourDecimalsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PolymarketAdapters.toSignedOrder(
                limitOrder(OrderType.BID, "10", "0.12345"), MAKER, BigDecimal.ONE, 0L));
  }

  @Test
  void nonPositiveSizesAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PolymarketAdapters.toSignedOrder(
                limitOrder(OrderType.BID, "0", "0.5"), MAKER, BigDecimal.ONE, 0L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PolymarketAdapters.toSignedOrder(
                limitOrder(OrderType.BID, "-1", "0.5"), MAKER, BigDecimal.ONE, 0L));
  }

  // ---------- RULE_NO_COMPLEMENT ----------

  @Test
  void readSideRecordsAdoptTheTokenTheyReference() {
    PolymarketOpenOrder openOrder =
        new PolymarketOpenOrder(
            "ord-1", "live", "owner", MAKER, CONDITION_ID, OTHER_TOKEN_ID, "No", "SELL", "10",
            "0", "0.44", "0", "GTC", "1754230000");
    LimitOrder adapted = PolymarketAdapters.adaptOrder(openOrder);
    PredictionMarketContract instrument = (PredictionMarketContract) adapted.getInstrument();
    assertEquals(
        OTHER_TOKEN_ID,
        instrument.getOutcomeId(),
        "the record's own token must be used, never a complement");
    assertEquals(OrderType.ASK, adapted.getType());
    assertEquals(new BigDecimal("0.44"), adapted.getLimitPrice());
  }

  @Test
  void userTradesAdoptTheTokenTheyReference() {
    PolymarketUserTrade fill =
        new PolymarketUserTrade(
            "fill-1", "ord-1", CONDITION_ID, OTHER_TOKEN_ID, "No", "SELL", "3", "0.44", "MATCHED",
            "1754230000", "TAKER", "owner");
    UserTrade adapted = PolymarketAdapters.adaptUserTrade(fill);
    assertEquals(
        OTHER_TOKEN_ID, ((PredictionMarketContract) adapted.getInstrument()).getOutcomeId());
    assertEquals(OrderType.ASK, adapted.getType());
    assertEquals(new Date(1754230000L * 1000L), adapted.getTimestamp());
  }

  // ---------- instrument validation ----------

  @Test
  void currencyPairsAreNotValidPolymarketInstruments() {
    assertThrows(
        InstrumentNotValidException.class,
        () -> PolymarketAdapters.tokenId(CurrencyPair.BTC_USD));
  }

  @Test
  void foreignProviderContractsAreRejected() {
    PredictionMarketContract kalshi =
        new PredictionMarketContract("kalshi", "EV", "MKT", "YES", Currency.USD);
    assertThrows(InstrumentNotValidException.class, () -> PolymarketAdapters.tokenId(kalshi));
  }

  @Test
  void tokenIdExtractsTheOutcomeId() {
    assertEquals(TOKEN_ID, PolymarketAdapters.tokenId(CONTRACT));
    assertEquals(CONDITION_ID, PolymarketAdapters.conditionId(CONTRACT));
  }

  // ---------- gamma discovery ----------

  @Test
  void tokenIdsParseStringifiedJsonArray() {
    assertEquals(List.of(TOKEN_ID, OTHER_TOKEN_ID), PolymarketAdapters.tokenIds(gammaMarket()));
    PolymarketGammaMarket blank =
        new PolymarketGammaMarket(
            "1", CONDITION_ID, "q?", null, null, " ", true, false, true,
            new BigDecimal("5"), new BigDecimal("0.001"), "1000");
    assertEquals(List.of(), PolymarketAdapters.tokenIds(blank));
  }

  @Test
  void unparseableTokenIdsRaiseExchangeException() {
    PolymarketGammaMarket broken =
        new PolymarketGammaMarket(
            "1", CONDITION_ID, "q?", null, null, "not-json", true, false, true,
            new BigDecimal("5"), new BigDecimal("0.001"), "1000");
    assertThrows(ExchangeException.class, () -> PolymarketAdapters.tokenIds(broken));
  }

  @Test
  void adaptContractBuildsOneContractPerOutcomeToken() {
    PredictionMarketContract first = PolymarketAdapters.adaptContract(gammaMarket(), 0);
    assertEquals(new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID,
        Currency.USD), first);
    PredictionMarketContract second = PolymarketAdapters.adaptContract(gammaMarket(), 1);
    assertEquals(OTHER_TOKEN_ID, second.getOutcomeId());
    assertThrows(
        IllegalArgumentException.class, () -> PolymarketAdapters.adaptContract(gammaMarket(), 2));
  }

  @Test
  void adaptMetadataMapsTickAndMinimums() {
    InstrumentMetaData meta = PolymarketAdapters.adaptMetadata(gammaMarket());
    assertEquals(4, meta.getPriceScale());
    assertEquals(new BigDecimal("0.001"), meta.getPriceStepSize());
    assertEquals(new BigDecimal("5"), meta.getMinimumAmount());
    assertEquals(6, meta.getVolumeScale());
  }

  // ---------- market data ----------

  @Test
  void orderBookLevelsAreReSortedBestFirst() {
    PolymarketBookResponse book =
        new PolymarketBookResponse(
            CONDITION_ID, TOKEN_ID, "1754230000000", "0xhash",
            List.of(new PolymarketBookLevel("0.55", "100"), new PolymarketBookLevel("0.56", "50")),
            List.of(new PolymarketBookLevel("0.60", "70"), new PolymarketBookLevel("0.59", "80")));
    OrderBook adapted = PolymarketAdapters.adaptOrderBook(book);
    assertEquals(new BigDecimal("0.56"), adapted.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.59"), adapted.getAsks().get(0).getLimitPrice());
    assertEquals(new Date(1754230000000L), adapted.getTimeStamp());
    assertEquals(CONTRACT, adapted.getBids().get(0).getInstrument());
  }

  @Test
  void dataTradesMapSellToAskAggressor() {
    PolymarketDataTrade trade =
        new PolymarketDataTrade(
            "0xproxy", "SELL", TOKEN_ID, CONDITION_ID, new BigDecimal("3"),
            new BigDecimal("0.56"), 1754230000L, "title", "Yes", 0, "0xtxhash");
    List<Trade> adapted = PolymarketAdapters.adaptTrades(List.of(trade)).getTrades();
    assertEquals(1, adapted.size());
    assertEquals(OrderType.ASK, adapted.get(0).getType());
    assertEquals(CONTRACT, adapted.get(0).getInstrument());
    assertEquals(new Date(1754230000L * 1000L), adapted.get(0).getTimestamp());
    assertEquals("0xtxhash", adapted.get(0).getId());
  }

  // ---------- order lifecycle ----------

  @Test
  void orderStatusTruthTable() {
    assertEquals(OrderStatus.OPEN, PolymarketAdapters.adaptOrderStatus("live", "0"));
    assertEquals(
        OrderStatus.PARTIALLY_FILLED, PolymarketAdapters.adaptOrderStatus("live", "3"));
    assertEquals(OrderStatus.FILLED, PolymarketAdapters.adaptOrderStatus("matched", "10"));
    assertEquals(OrderStatus.CANCELED, PolymarketAdapters.adaptOrderStatus("canceled", "0"));
    assertEquals(OrderStatus.PENDING_NEW, PolymarketAdapters.adaptOrderStatus("delayed", "0"));
    assertEquals(OrderStatus.UNKNOWN, PolymarketAdapters.adaptOrderStatus("surprise", "0"));
  }

  @Test
  void orderTypeFlagsMapToClobOrderTypes() {
    assertEquals("GTC", PolymarketAdapters.toOrderType(limitOrder(OrderType.BID, "1", "0.5")));
    LimitOrder fok = limitOrder(OrderType.BID, "1", "0.5");
    fok.addOrderFlag(PolymarketOrderFlags.FILL_OR_KILL);
    assertEquals("FOK", PolymarketAdapters.toOrderType(fok));
    LimitOrder ioc = limitOrder(OrderType.BID, "1", "0.5");
    ioc.addOrderFlag(PolymarketOrderFlags.IMMEDIATE_OR_CANCEL);
    assertEquals("FAK", PolymarketAdapters.toOrderType(ioc));
    LimitOrder both = limitOrder(OrderType.BID, "1", "0.5");
    both.addOrderFlag(PolymarketOrderFlags.FILL_OR_KILL);
    both.addOrderFlag(PolymarketOrderFlags.IMMEDIATE_OR_CANCEL);
    assertEquals("FOK", PolymarketAdapters.toOrderType(both), "FOK takes precedence");
  }

  // ---------- account / positions ----------

  @Test
  void collateralBalanceConvertsMicroUsdcToDollars() {
    var accountInfo =
        PolymarketAdapters.adaptAccountInfo(new PolymarketBalanceResponse("1234567", Map.of()));
    var balance = accountInfo.getWallet().getBalance(Currency.USD);
    assertEquals(new BigDecimal("1.234567"), balance.getAvailable());

    var empty = PolymarketAdapters.adaptAccountInfo(new PolymarketBalanceResponse(null, null));
    assertEquals(BigDecimal.ZERO, empty.getWallet().getBalance(Currency.USD).getAvailable());
  }

  @Test
  void positionsAreLongTheirOwnOutcomeToken() {
    PolymarketDataPosition position =
        new PolymarketDataPosition(
            "0xproxy", TOKEN_ID, CONDITION_ID, new BigDecimal("5"), new BigDecimal("0.40"),
            new BigDecimal("0.55"), new BigDecimal("2.75"), "Yes", 0, "999", "123", "title",
            false, false);
    List<OpenPosition> adapted = PolymarketAdapters.adaptPositions(List.of(position));
    assertEquals(1, adapted.size());
    assertEquals(OpenPosition.Type.LONG, adapted.get(0).getType());
    assertEquals(0, new BigDecimal("5").compareTo(adapted.get(0).getSize()));
    assertEquals(CONDITION_ID,
        ((PredictionMarketContract) adapted.get(0).getInstrument()).getMarketId());
    assertEquals(TOKEN_ID,
        ((PredictionMarketContract) adapted.get(0).getInstrument()).getOutcomeId());
  }

  private static LimitOrder limitOrder(OrderType type, String amount, String price) {
    return new LimitOrder.Builder(type, CONTRACT)
        .originalAmount(new BigDecimal(amount))
        .limitPrice(new BigDecimal(price))
        .build();
  }

  private static PolymarketGammaMarket gammaMarket() {
    return new PolymarketGammaMarket(
        "1", CONDITION_ID, "Will it rain?", "[\"Yes\",\"No\"]", "[\"0.5\",\"0.5\"]",
        "[\"" + TOKEN_ID + "\",\"" + OTHER_TOKEN_ID + "\"]", true, false, true,
        new BigDecimal("5"), new BigDecimal("0.001"), "1000");
  }
}
