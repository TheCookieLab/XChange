package info.bitrich.xchangestream.kucoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

/** Deterministic matrix for the UTA order-book calibration procedure. */
class UtaOrderBookAssemblerTest {

  private static List<List<BigDecimal>> levels(String... priceSize) {
    java.util.List<List<BigDecimal>> out = new java.util.ArrayList<>();
    for (int i = 0; i < priceSize.length; i += 2) {
      out.add(List.of(new BigDecimal(priceSize[i]), new BigDecimal(priceSize[i + 1])));
    }
    return out;
  }

  @Test
  void snapshotBuildsBook() {
    UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    var result =
        assembler.onUpdate(
            true,
            100,
            100,
            levels("100", "5", "99", "3"),
            levels("101", "7", "102", "1"));
    assertEquals(UtaOrderBookAssembler.Result.APPLIED, result);
    assertTrue(assembler.isSynced());
    assertEquals(100L, assembler.getLastSequence());
    OrderBook book = assembler.toOrderBook(CurrencyPair.BTC_USDT, new java.util.Date());
    assertEquals(2, book.getBids().size());
    assertEquals(2, book.getAsks().size());
    // bids descending, asks ascending
    assertTrue(book.getBids().get(0).getLimitPrice().compareTo(book.getBids().get(1).getLimitPrice()) > 0);
    assertTrue(book.getAsks().get(0).getLimitPrice().compareTo(book.getAsks().get(1).getLimitPrice()) < 0);
  }

  @Test
  void deltasApplyWithContinuity() {
    UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    assembler.onUpdate(true, 100, 100, levels("100", "5"), levels("101", "7"));
    var delta =
        assembler.onUpdate(
            false, 101, 101, levels("100", "0"), levels("101", "8", "102", "2"));
    assertEquals(UtaOrderBookAssembler.Result.APPLIED, delta);
    assertEquals(101L, assembler.getLastSequence());
    OrderBook book = assembler.toOrderBook(CurrencyPair.BTC_USDT, new java.util.Date());
    // level 100 removed by size 0; 101 updated; 102 added
    assertEquals(0, book.getBids().size());
    assertEquals(2, book.getAsks().size());
  }

  @Test
  void overlappingDeltaIsDroppedSilently() {
    UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    assembler.onUpdate(true, 100, 100, levels("100", "5"), levels("101", "7"));
    var stale = assembler.onUpdate(false, 99, 100, levels("100", "9"), levels("101", "9"));
    assertEquals(UtaOrderBookAssembler.Result.STALE_DROPPED, stale);
    OrderBook book = assembler.toOrderBook(CurrencyPair.BTC_USDT, new java.util.Date());
    assertEquals(new BigDecimal("5"), book.getBids().get(0).getOriginalAmount());
  }

  @Test
  void deltasBeforeSnapshotAreDropped() {
    UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    var early = assembler.onUpdate(false, 1, 2, levels("100", "5"), null);
    assertEquals(UtaOrderBookAssembler.Result.AWAITING_SNAPSHOT, early);
    assertFalse(assembler.isSynced());
  }

  @Test
  void gapClearsStateAndRequiresRebuild() {
    UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    assembler.onUpdate(true, 100, 100, levels("100", "5"), levels("101", "7"));
    var gap = assembler.onUpdate(false, 105, 106, levels("100", "1"), levels("101", "1"));
    assertEquals(UtaOrderBookAssembler.Result.GAP, gap);
    assertFalse(assembler.isSynced());
    assertEquals(-1L, assembler.getLastSequence());
    // a fresh snapshot rebuilds
    var rebuild =
        assembler.onUpdate(true, 200, 200, levels("100", "9"), levels("101", "1"));
    assertEquals(UtaOrderBookAssembler.Result.APPLIED, rebuild);
    OrderBook book = assembler.toOrderBook(CurrencyPair.BTC_USDT, new java.util.Date());
    assertEquals(new BigDecimal("9"), book.getBids().get(0).getOriginalAmount());
  }

  @Test
  void futuresRpiTriplesAreTolerated() {
    UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    assembler.onUpdate(true, 100, 100, levels("100", "5"), levels("101", "7"));
    // rpiFilter frames may carry [price, noneRPISize, RPISize]; only first two used
    var delta =
        assembler.onUpdate(
            false,
            101,
            101,
            List.of(java.util.Arrays.asList(new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("2"))),
            null);
    assertEquals(UtaOrderBookAssembler.Result.APPLIED, delta);
    OrderBook book = assembler.toOrderBook(CurrencyPair.BTC_USDT, new java.util.Date());
    assertEquals(0, book.getBids().size());
  }
}
