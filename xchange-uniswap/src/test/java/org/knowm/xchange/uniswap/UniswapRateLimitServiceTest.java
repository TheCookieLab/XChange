package org.knowm.xchange.uniswap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.uniswap.RpcStub.Reply;
import org.knowm.xchange.uniswap.client.UniswapRateLimitPolicy;

/**
 * The public account service reaches the node only through the universal rate limiter: one
 * admission per JSON-RPC wire attempt, rate rejections replayed by the core, and no module-owned
 * limiter on the path.
 */
class UniswapRateLimitServiceTest {

  @TempDir Path tempDir;

  private RpcStub stub;
  private UniswapExchange exchange;
  private ExchangeSpecification specification;

  @BeforeEach
  void setUp() throws Exception {
    stub = new RpcStub();
    specification =
        TestFixtures.specification(TestFixtures.keystore(tempDir, "s3cret".toCharArray()));
    specification.setSslUri(stub.url());
    specification
        .getResilience()
        .setRateLimitPolicy(
            UniswapRateLimitPolicy.defaultPolicy()
                .withFallbackBackoff(Duration.ofMillis(5), Duration.ofMillis(5), 0.0));
    specification.getResilience().setRateLimiterEnabled(true);
    exchange = new UniswapExchange();
    exchange.applySpecification(specification);
  }

  @AfterEach
  void tearDown() {
    exchange.close();
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    if (context != null) {
      context.close();
    }
    stub.close();
  }

  @Test
  void defaultSpecificationEnablesTheUniversalLimiterWithThePolicy() {
    ExchangeSpecification defaults = new UniswapExchange().getDefaultExchangeSpecification();

    assertThat(defaults.getResilience().isRateLimiterEnabled()).isTrue();
    assertThat(defaults.getResilience().getRateLimitPolicy().getNamespace())
        .isEqualTo(UniswapRateLimitPolicy.NAMESPACE);
  }

  @Test
  void accountInfoAdmitsEveryJsonRpcAttemptOnceThroughTheSharedContext() throws Exception {
    AccountInfo info = exchange.getAccountService().getAccountInfo();

    assertThat(info.getWallet().getBalances()).hasSize(3);
    assertThat(stub.methods())
        .containsExactly("eth_blockNumber", "eth_getBalance", "eth_call", "eth_call");
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(stub.arrivals());
    assertThat(context.diagnostics().getPolicyVersions())
        .containsEntry(UniswapRateLimitPolicy.NAMESPACE, UniswapRateLimitPolicy.VERSION);
  }

  @Test
  void rateLimitedBalanceReadIsReplayedByTheCoreWithFreshAdmission() throws Exception {
    stub.respondWith((arrival, method) -> arrival == 2 ? Reply.tooManyRequests() : Reply.success());

    AccountInfo info = exchange.getAccountService().getAccountInfo();

    assertThat(info.getWallet().getBalances()).hasSize(3);
    assertThat(stub.methods())
        .containsExactly(
            "eth_blockNumber", "eth_getBalance", "eth_getBalance", "eth_call", "eth_call");
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(5);
    assertThat(context.diagnostics().getRatePressureEvents()).isEqualTo(1);
    assertThat(context.diagnostics().getRetries()).isEqualTo(1);
  }

  @Test
  void aDisabledLimiterLeavesTheTransportUnmetered() throws Exception {
    specification.getResilience().getRateLimitContext().close();
    specification.getResilience().setRateLimiterEnabled(false);
    specification.getResilience().setRateLimitContext(null);
    UniswapExchange unmetered = new UniswapExchange();
    unmetered.applySpecification(specification);
    try {
      unmetered.getAccountService().getAccountInfo();
    } finally {
      unmetered.close();
    }

    assertThat(stub.arrivals()).isEqualTo(4);
    assertThat(specification.getResilience().getRateLimitContext()).isNull();
  }
}
