package org.knowm.xchange.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.exceptions.ExchangeException;

/** Rate policy parsing and registry wiring: opt-in, immutable, fail-fast. */
public class CryptoComRatePolicyTest {

  @Test
  public void emptyPolicy_isEffectivelyDisabled() {
    CryptoComRatePolicy policy = CryptoComRatePolicy.parse(null);

    assertThat(policy.limitsPerMinute()).isEmpty();

    CryptoComRatePolicy blank = CryptoComRatePolicy.parse("  ");
    assertThat(blank.limitsPerMinute()).isEmpty();
  }

  @Test
  public void parsesPerMethodLimitsAndRegistersLimiters() {
    CryptoComRatePolicy policy =
        CryptoComRatePolicy.parse("private/create-order:120, private/get-order-history:600");

    assertThat(policy.limitsPerMinute())
        .containsEntry("private/create-order", 120)
        .containsEntry("private/get-order-history", 600);

    ResilienceRegistries registries = new ResilienceRegistries();
    policy.registerRateLimiters(registries);

    assertThat(registries.rateLimiters().rateLimiter("private/create-order")).isNotNull();
    assertThat(registries.rateLimiters().rateLimiter("private/get-order-history")).isNotNull();
  }

  @Test
  public void malformedEntry_failsFast() {
    assertThatThrownBy(() -> CryptoComRatePolicy.parse("private/create-order"))
        .isInstanceOf(ExchangeException.class);
    assertThatThrownBy(() -> CryptoComRatePolicy.parse(":10"))
        .isInstanceOf(ExchangeException.class);
    assertThatThrownBy(() -> CryptoComRatePolicy.parse("private/create-order:abc"))
        .isInstanceOf(ExchangeException.class);
    assertThatThrownBy(() -> CryptoComRatePolicy.parse("private/create-order:0"))
        .isInstanceOf(ExchangeException.class);
  }

  @Test
  public void policyIsImmutable() {
    CryptoComRatePolicy policy = CryptoComRatePolicy.parse("private/user-balance:10");

    assertThatThrownBy(() -> policy.limitsPerMinute().put("x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}