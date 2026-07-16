package org.knowm.xchange.coinbasederivatives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbasederivatives.dto.account.CoinbaseDerivativesPosition;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesOrder;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.instrument.Instrument;

class CoinbaseDerivativesAdaptersTest {
  @Test
  void discoveredPerpetualMapsToFuturesContractAndExactMetadata() {
    CoinbaseDerivativesInstrument provider =
        new CoinbaseDerivativesInstrument(
            "BTC_USDC-PERPETUAL",
            "future",
            "BTC",
            "USDC",
            "USDC",
            true,
            new BigDecimal("0.00000001"),
            new BigDecimal("0.000000001"),
            BigDecimal.ONE,
            new BigDecimal("-0.0001"),
            new BigDecimal("0.00035"));

    Instrument instrument = CoinbaseDerivativesAdapters.registerInstrument(provider);

    assertEquals(new FuturesContract("BTC/USDC/PERPETUAL"), instrument);
    assertEquals("BTC_USDC-PERPETUAL", CoinbaseDerivativesAdapters.toNativeName(instrument));
    assertEquals(
        new BigDecimal("0.00000001"),
        CoinbaseDerivativesAdapters.adaptMetadata(provider).getPriceStepSize());
  }

  @Test
  void duplicateLabelsRemainDistinctOrders() {
    CoinbaseDerivativesAdapters.registerInstrument(
        new CoinbaseDerivativesInstrument(
            "BTC_USDC-PERPETUAL",
            "future",
            "BTC",
            "USDC",
            "USDC",
            true,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    CoinbaseDerivativesOrder first = order("order-1", "duplicate");
    CoinbaseDerivativesOrder second = order("order-2", "duplicate");

    Order adaptedFirst = CoinbaseDerivativesAdapters.adaptOrder(first);
    Order adaptedSecond = CoinbaseDerivativesAdapters.adaptOrder(second);

    assertEquals("duplicate", adaptedFirst.getUserReference());
    assertEquals("duplicate", adaptedSecond.getUserReference());
    assertNotSame(adaptedFirst, adaptedSecond);
    assertEquals("order-1", adaptedFirst.getId());
    assertEquals("order-2", adaptedSecond.getId());
  }

  @Test
  void placementResultDefensivelyCopiesRelatedIds() {
    java.util.ArrayList<String> related = new java.util.ArrayList<>(List.of("child"));
    CoinbaseDerivativesPlacementResult result =
        new CoinbaseDerivativesPlacementResult(
            "primary",
            related,
            7,
            "BTC_USDC-PERPETUAL",
            "buy",
            "stop_limit",
            BigDecimal.ONE,
            BigDecimal.TEN,
            true,
            "duplicate",
            "open");
    related.add("later");

    assertEquals(List.of("child"), result.relatedOrderIds());
    assertThrows(UnsupportedOperationException.class, () -> result.relatedOrderIds().add("x"));
  }

  @Test
  void positionPreservesSignedSizeAndDirection() {
    CoinbaseDerivativesAdapters.registerInstrument(
        new CoinbaseDerivativesInstrument(
            "BTC_USDC-PERPETUAL",
            "future",
            "BTC",
            "USDC",
            "USDC",
            true,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    CoinbaseDerivativesPosition provider =
        new CoinbaseDerivativesPosition(
            "BTC_USDC-PERPETUAL",
            "sell",
            new BigDecimal("-2.500"),
            null,
            new BigDecimal("100000"),
            new BigDecimal("99000"),
            new BigDecimal("120000"),
            new BigDecimal("-12.34"),
            new BigDecimal("5.67"),
            new BigDecimal("-6.67"),
            new BigDecimal("0.123"),
            new BigDecimal("10"),
            new BigDecimal("8"),
            "cross",
            1L);

    var adapted = CoinbaseDerivativesAdapters.adaptPosition(provider);

    assertEquals(new BigDecimal("-2.500"), adapted.getSize());
    assertEquals(org.knowm.xchange.dto.account.OpenPosition.Type.SHORT, adapted.getType());
  }

  private static CoinbaseDerivativesOrder order(String id, String label) {
    return new CoinbaseDerivativesOrder(
        id,
        null,
        List.of(),
        "BTC_USDC-PERPETUAL",
        "buy",
        "limit",
        new BigDecimal("0.000001"),
        null,
        new BigDecimal("100000.123456789"),
        null,
        false,
        label,
        "open",
        1L,
        1L,
        BigDecimal.ZERO,
        null,
        BigDecimal.ZERO);
  }
}
