package org.knowm.xchange.okex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okx.dto.OkxResponse;

/**
 * Verifies that the deprecated legacy raw services preserve the response envelope when a delegated
 * canonical endpoint returns a top-level business failure with {@code null} data. All tests are
 * offline.
 */
public class OkexRawWrapTest {

  @Test
  public void accountRawWrapPreservesEnvelopeWhenDataIsNull() {
    OkxResponse<List<String>> failed =
        new OkxResponse<>(null, "50111", "Invalid OK Access Key", null);

    OkexResponse<List<String>> wrapped = OkexAccountServiceRaw.wrap(failed, Function.identity());

    assertThat(wrapped.getCode()).isEqualTo("50111");
    assertThat(wrapped.getMsg()).isEqualTo("Invalid OK Access Key");
    assertThat(wrapped.getData()).isEmpty();
  }

  @Test
  public void marketDataRawWrapPreservesEnvelopeWhenDataIsNull() {
    OkxResponse<List<String>> failed = new OkxResponse<>(null, "50011", "Invalid API Key", null);

    OkexResponse<List<String>> wrapped = OkexMarketDataServiceRaw.wrap(failed, Function.identity());

    assertThat(wrapped.getCode()).isEqualTo("50011");
    assertThat(wrapped.getMsg()).isEqualTo("Invalid API Key");
    assertThat(wrapped.getData()).isEmpty();
  }

  @Test
  public void tradeRawWrapMapsEntriesWhenDataIsPresent() {
    OkxResponse<List<String>> ok = new OkxResponse<>(null, "0", null, Arrays.asList("a", "b"));

    OkexResponse<List<String>> wrapped = OkexTradeServiceRaw.wrap(ok, String::toUpperCase);

    assertThat(wrapped.getCode()).isEqualTo("0");
    assertThat(wrapped.getData()).containsExactly("A", "B");
  }
}
