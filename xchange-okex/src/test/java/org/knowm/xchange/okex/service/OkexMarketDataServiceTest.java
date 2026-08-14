package org.knowm.xchange.okex.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.knowm.xchange.okex.dto.OkexInstType;
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
}
