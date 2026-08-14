package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.Test;

/** Offline tests for the immutable typed rate-limit policy. */
public class OkxRateLimitPolicyTest {

  @Test
  public void testBuilderBuildsImmutablePolicy() {
    OkxRateLimitPolicy policy =
        OkxRateLimitPolicy.builder()
            .limit("/account/balance", 5, 1)
            .limit("/trade/order", 60, 2)
            .build();

    assertThat(policy.asMap()).containsOnlyKeys("/account/balance", "/trade/order");
    assertThat(policy.rateLimitFor("/account/balance"))
        .contains(new OkxRateLimitPolicy.OkxRateLimit(5, 1));
    assertThat(policy.rateLimitFor("/trade/order"))
        .contains(new OkxRateLimitPolicy.OkxRateLimit(60, 2));
    assertThat(policy.rateLimitFor("/unknown")).isEmpty();
  }

  @Test
  public void testAsMapIsUnmodifiable() {
    OkxRateLimitPolicy policy =
        OkxRateLimitPolicy.builder().limit("/account/balance", 5, 1).build();

    assertThatThrownBy(() -> policy.asMap().put("/new", new OkxRateLimitPolicy.OkxRateLimit(1, 1)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void testBuilderSnapshotIsIsolated() {
    OkxRateLimitPolicy.Builder builder = OkxRateLimitPolicy.builder().limit("/a", 1, 1);
    OkxRateLimitPolicy policy = builder.build();

    builder.limit("/b", 2, 2).limit("/a", 9, 9);

    assertThat(policy.asMap()).containsOnlyKeys("/a");
    assertThat(policy.rateLimitFor("/a")).contains(new OkxRateLimitPolicy.OkxRateLimit(1, 1));
  }

  @Test
  public void testRateLimitRejectsNonPositiveValues() {
    assertThatThrownBy(() -> new OkxRateLimitPolicy.OkxRateLimit(0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OkxRateLimitPolicy.OkxRateLimit(1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OkxRateLimitPolicy.builder().limit("/a", -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testPublicPolicyPreservesExistingLimits() {
    Map<String, OkxRateLimitPolicy.OkxRateLimit> limits = Okx.publicPathRateLimits.asMap();

    assertThat(limits.get(Okx.instrumentsPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(8, 1));
    assertThat(limits.get(Okx.tickerPath)).isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(8, 1));
    assertThat(limits.get(Okx.tickersPath)).isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(8, 1));
    assertThat(limits.get(Okx.fundingRateHistoryPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(4, 1));
    assertThat(limits.get(Okx.candlesHistoryPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(8, 1));
  }

  @Test
  public void testPrivatePolicyPreservesExistingLimits() {
    Map<String, OkxRateLimitPolicy.OkxRateLimit> limits =
        OkxAuthenticated.privatePathRateLimits.asMap();

    assertThat(limits.get(OkxAuthenticated.balancePath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(5, 1));
    assertThat(limits.get(OkxAuthenticated.tradeFeePath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(5, 2));
    assertThat(limits.get(OkxAuthenticated.configPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(5, 2));
    assertThat(limits.get(OkxAuthenticated.getBillsPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
    assertThat(limits.get(OkxAuthenticated.changeMarginPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(20, 2));
    assertThat(limits.get(OkxAuthenticated.currenciesPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
    assertThat(limits.get(OkxAuthenticated.assetBalancesPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
    assertThat(limits.get(OkxAuthenticated.positionsPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(5, 1));
    assertThat(limits.get(OkxAuthenticated.setLeveragePath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(20, 2));
    assertThat(limits.get(OkxAuthenticated.pendingOrdersPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(20, 2));
    assertThat(limits.get(OkxAuthenticated.orderDetailsPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(60, 2));
    assertThat(limits.get(OkxAuthenticated.placeOrderPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(60, 2));
    assertThat(limits.get(OkxAuthenticated.placeBatchOrderPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(300, 2));
    assertThat(limits.get(OkxAuthenticated.cancelOrderPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(60, 2));
    assertThat(limits.get(OkxAuthenticated.cancelBatchOrderPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(300, 2));
    assertThat(limits.get(OkxAuthenticated.amendOrderPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(60, 2));
    assertThat(limits.get(OkxAuthenticated.amendBatchOrderPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(300, 2));
    assertThat(limits.get(OkxAuthenticated.depositAddressPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
    assertThat(limits.get(OkxAuthenticated.ordersHistoryPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(40, 2));
    assertThat(limits.get(OkxAuthenticated.subAccountList))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(2, 2));
    assertThat(limits.get(OkxAuthenticated.subAccountBalance))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(2, 2));
    assertThat(limits.get(OkxAuthenticated.piggyBalance))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
  }

  @Test
  public void testNewEndpointsAreRegistered() {
    Map<String, OkxRateLimitPolicy.OkxRateLimit> limits =
        OkxAuthenticated.privatePathRateLimits.asMap();

    assertThat(limits.get(OkxAuthenticated.assetTransferPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
    assertThat(limits.get(OkxAuthenticated.positionsHistoryPath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(10, 2));
    assertThat(limits.get(OkxAuthenticated.billsArchivePath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(6, 1));
    assertThat(limits.get(OkxAuthenticated.setPositionModePath))
        .isEqualTo(new OkxRateLimitPolicy.OkxRateLimit(5, 2));
  }

  @Test
  public void testExtensionPointRegistersLimiter() {
    OkxRateLimitPolicy policy =
        OkxRateLimitPolicy.builder().limit("/phase/4/endpoint", 20, 2).build();

    assertThat(policy.rateLimitFor("/phase/4/endpoint"))
        .contains(new OkxRateLimitPolicy.OkxRateLimit(20, 2));
  }
}
