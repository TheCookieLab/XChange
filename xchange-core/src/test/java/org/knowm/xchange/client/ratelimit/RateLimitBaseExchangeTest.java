package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;

/** BaseExchange wiring of the policy and context into the specification (AC4, AC20 core part). */
public class RateLimitBaseExchangeTest {

  /** Minimal exchange: no services, no metadata, no remote init. */
  private static final class TestExchange extends BaseExchange {
    @Override
    protected void initServices() {}

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
      ExchangeSpecification specification = new ExchangeSpecification(TestExchange.class);
      specification.setShouldLoadRemoteMetaData(false);
      return specification;
    }
  }

  private static RateLimitPolicy policy(long capacity) {
    return new RateLimitPolicy(
        "t.exchange",
        "v1",
        "unit test",
        List.of(
            RateLimitBudget.tokenBucket(
                "b", ScopeKind.EGRESS, capacity, capacity, Duration.ofSeconds(1))),
        request -> null,
        RateLimitFeedbackInterpreter.none());
  }

  private static ExchangeSpecification specification(
      RateLimitPolicy policy, boolean enabled, RateLimitContext context) {
    ExchangeSpecification specification = new ExchangeSpecification(TestExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    specification.getResilience().setRateLimiterEnabled(enabled);
    specification.getResilience().setRateLimitPolicy(policy);
    specification.getResilience().setRateLimitContext(context);
    return specification;
  }

  @Test
  public void anEnabledPolicyWithoutContextCreatesAnOwnedContextAndWritesItBack() {
    ExchangeSpecification specification = specification(policy(2), true, null);
    new TestExchange().applySpecification(specification);
    RateLimitContext effective = specification.getResilience().getRateLimitContext();
    assertThat(effective).isNotNull();
    assertThat(effective.diagnostics().getPolicyVersions()).containsEntry("t.exchange", "v1");
  }

  @Test
  public void aSuppliedContextIsSharedAcrossExchangesAndKeepsIdentity() {
    try (RateLimitContext shared = new RateLimitContext()) {
      ExchangeSpecification one = specification(policy(2), true, shared);
      ExchangeSpecification two = specification(policy(2), true, shared);
      new TestExchange().applySpecification(one);
      new TestExchange().applySpecification(two);
      assertThat(one.getResilience().getRateLimitContext()).isSameAs(shared);
      assertThat(two.getResilience().getRateLimitContext()).isSameAs(shared);
      assertThat(shared.diagnostics().getPolicyVersions()).hasSize(1);
    }
  }

  @Test
  public void aConflictingPolicyInASharedContextFailsConstructionBeforeAnyService() {
    try (RateLimitContext shared = new RateLimitContext()) {
      new TestExchange().applySpecification(specification(policy(2), true, shared));
      ExchangeSpecification conflicting = specification(policy(9), true, shared);
      assertThatThrownBy(() -> new TestExchange().applySpecification(conflicting))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("b");
    }
  }

  @Test
  public void anExplicitlyDisabledPolicyCreatesNoContextAndRecordsDisabledInASuppliedOne() {
    ExchangeSpecification withoutContext = specification(policy(2), false, null);
    new TestExchange().applySpecification(withoutContext);
    assertThat(withoutContext.getResilience().getRateLimitContext()).isNull();

    try (RateLimitContext shared = new RateLimitContext()) {
      ExchangeSpecification withContext = specification(policy(2), false, shared);
      new TestExchange().applySpecification(withContext);
      assertThat(shared.diagnostics().getDisabledNamespaces()).containsExactly("t.exchange");
      assertThat(shared.diagnostics().getPolicyVersions()).isEmpty();
    }
  }

  @Test
  public void aSpecificationWithoutPolicyIsUnchanged() {
    ExchangeSpecification specification = specification(null, true, null);
    new TestExchange().applySpecification(specification);
    assertThat(specification.getResilience().getRateLimitContext()).isNull();
  }

  @Test
  public void theUserScopeNeverAppearsInTheSpecificationToString() {
    ExchangeSpecification specification = specification(policy(2), true, null);
    specification.getResilience().setRateLimitUserScope("secret-scope-value");
    assertThat(specification.getResilience().toString()).doesNotContain("secret-scope-value");
  }
}
