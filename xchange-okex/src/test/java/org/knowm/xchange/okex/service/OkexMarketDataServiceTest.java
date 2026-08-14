package org.knowm.xchange.okex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.okx.dto.OkxInstType;

/** Compatibility-surface tests for {@link OkexMarketDataService}. */
public class OkexMarketDataServiceTest {

  @Test
  public void legacyTickerParamsAreConvertedBeforeDelegation() {
    // The canonical delegate requires OkxInstType; the shim must convert the legacy OkexInstType
    // (with its to()) instead of forwarding it and hitting the IllegalArgumentException guard.
    assertThat(OkexMarketDataService.convertTickerParams(OkexInstType.SPOT))
        .isEqualTo(OkxInstType.SPOT);
    assertThat(OkexMarketDataService.convertTickerParams(OkexInstType.SWAP))
        .isEqualTo(OkxInstType.SWAP);
    assertThat(OkexMarketDataService.convertTickerParams(OkexInstType.SPOT))
        .isNotInstanceOf(OkexInstType.class);
    assertThat(OkexMarketDataService.convertTickerParams(OkexInstType.SPOT))
        .isInstanceOf(OkxInstType.class);
    assertThat(OkexMarketDataService.convertTickerParams(null)).isNull();
  }

  @Test
  public void legacyFundingRateHistoryMappingProducesLegacyElementType() {
    org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory canonical =
        new org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory(
            "SWAP",
            "BTC-USDT",
            new BigDecimal("0.0001"),
            new BigDecimal("0.0002"),
            1700000000000L,
            "derivatives");

    OkxFundingRateHistory legacy = OkexMarketDataServiceRaw.toLegacyFundingRateHistory(canonical);

    assertThat(legacy).isInstanceOf(OkxFundingRateHistory.class);
    assertThat(legacy.getInstType()).isEqualTo("SWAP");
    assertThat(legacy.getInstrument()).isEqualTo(new CurrencyPair("BTC", "USDT"));
    assertThat(legacy.getPredictedFundingRate()).isEqualByComparingTo("0.0001");
    assertThat(legacy.getFundingRate()).isEqualByComparingTo("0.0002");
    assertThat(legacy.getFundingTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
    assertThat(legacy.getMethod()).isEqualTo("derivatives");
  }

  @Test
  public void legacyFundingRateHistorySignaturesReturnLegacyElementType() throws Exception {
    // The pre-rename element type must survive on both legacy public signatures so already
    // compiled callers keep receiving the original class (erased List descriptors otherwise
    // surface ClassCastException on element access).
    Method raw =
        OkexMarketDataServiceRaw.class.getMethod(
            "getOkxFundingRateHistoryRaw", String.class, Long.class, Long.class, Integer.class);
    assertThat(raw.getGenericReturnType().getTypeName())
        .isEqualTo("java.util.List<org.knowm.xchange.okex.dto.marketdata.OkxFundingRateHistory>");

    Method service =
        OkexMarketDataService.class.getMethod(
            "getFundingRateHistory", Instrument.class, Long.class, Long.class, Integer.class);
    assertThat(service.getGenericReturnType().getTypeName())
        .isEqualTo("java.util.List<org.knowm.xchange.okex.dto.marketdata.OkxFundingRateHistory>");
  }
}
