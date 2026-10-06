package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Test;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;

/** AC3 (shared and isolated scopes) and AC4 (no construction reset, conflicts rejected). */
public class RateLimitScopeTest {

  private static final long SEC = 1_000_000_000L;

  private RateLimitFixture fixture;

  @After
  public void tearDown() {
    if (fixture != null) {
      fixture.cleanup();
    }
  }

  private static RateLimitPolicy userPolicy() {
    return RateLimitFixture.policy(
        "t.scope",
        List.of(
            RateLimitBudget.tokenBucket("user", ScopeKind.USER, 1, 1, Duration.ofSeconds(10)),
            RateLimitBudget.tokenBucket("public", ScopeKind.EGRESS, 1, 1, Duration.ofSeconds(5))),
        RateLimitOperation.unitCost("private", RateLimitPriority.EXECUTION, true, "user"),
        RateLimitOperation.unitCost("both", RateLimitPriority.EXECUTION, true, "user", "public"),
        RateLimitOperation.unitCost("pub", RateLimitPriority.EXECUTION, true, "public"))
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofHours(1));
  }

  // ---- AC3 ------------------------------------------------------------------------------

  @Test
  public void twoExchangesSharingContextAndUserScopeConsumeOneUserAllocation() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy exchangeOnePolicy = userPolicy();
    RateLimitPolicy exchangeTwoPolicy = userPolicy(); // an equal but distinct policy object
    List<Long> dispatches = new ArrayList<>();
    fixture.run(exchangeOnePolicy, "private", "coinbase-user-1", dispatches);
    fixture.run(exchangeTwoPolicy, "private", "coinbase-user-1", dispatches);
    assertThat(dispatches).containsExactly(0L, 10 * SEC);
  }

  @Test
  public void differentApiKeysOfOneUserScopeDoNotMultiplyTheAllocation() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = userPolicy();
    List<Long> dispatches = new ArrayList<>();
    // The key is deliberately never part of the scope: both keys pass the same user binding.
    for (String ignoredApiKey : List.of("key-a", "key-b", "key-c")) {
      fixture.run(policy, "private", "coinbase-user-1", dispatches);
    }
    assertThat(dispatches).containsExactly(0L, 10 * SEC, 20 * SEC);
  }

  @Test
  public void aDifferentUserScopeIsIndependent() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = userPolicy();
    List<Long> dispatches = new ArrayList<>();
    fixture.run(policy, "private", "user-1", dispatches);
    fixture.run(policy, "private", "user-2", dispatches);
    fixture.run(policy, "private", "user-3", dispatches);
    assertThat(dispatches).containsExactly(0L, 0L, 0L);
  }

  @Test
  public void anUnspecifiedUserScopeIsTheConservativeSharedDefaultNeverPerKey() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = userPolicy();
    List<Long> dispatches = new ArrayList<>();
    fixture.run(policy, "private", null, dispatches);
    fixture.run(policy, "private", null, dispatches);
    assertThat(dispatches).containsExactly(0L, 10 * SEC);
  }

  @Test
  public void sharedPublicRequirementStillAppliesAcrossIndependentUsers() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy policy = userPolicy();
    List<Long> dispatches = new ArrayList<>();
    fixture.run(policy, "both", "user-1", dispatches);
    fixture.run(policy, "both", "user-2", dispatches); // own user allocation, shared egress budget
    fixture.run(policy, "pub", "user-3", dispatches);
    assertThat(dispatches).containsExactly(0L, 5 * SEC, 10 * SEC);
  }

  // ---- AC4 ------------------------------------------------------------------------------

  @Test
  public void reRegisteringAnIdenticalPolicyNeverResetsConsumedCapacity() throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    RateLimitPolicy first = userPolicy();
    fixture.context.register(first);
    List<Long> dispatches = new ArrayList<>();
    fixture.run(first, "private", "u", dispatches); // consumes the single token at 0
    // a later view / spec copy / credential rotation constructs and registers an equal policy
    for (int i = 0; i < 3; i++) {
      RateLimitPolicy copy = userPolicy();
      fixture.context.register(copy);
      fixture.context.register(copy.withMaxAttempts(5)); // same budgets, tuned queue behavior
    }
    fixture.run(userPolicy(), "private", "u", dispatches);
    assertThat(dispatches).containsExactly(0L, 10 * SEC);
    assertThat(fixture.context.diagnostics().getPolicyVersions())
        .containsEntry("t.scope", "test-1")
        .hasSize(1);
  }

  @Test
  public void concurrentRegistrationOfEqualPoliciesIsSafeAndKeepsOneState() throws Exception {
    fixture = RateLimitFixture.blocking();
    int threads = 8;
    CountDownLatch go = new CountDownLatch(1);
    List<RateLimitFixture.Task<Void>> tasks = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      tasks.add(
          fixture.start(
              () -> {
                go.await();
                fixture.context.register(userPolicy());
                return null;
              }));
    }
    go.countDown();
    for (RateLimitFixture.Task<Void> task : tasks) {
      assertThat(task.failure()).isNull();
    }
    assertThat(fixture.context.diagnostics().getPolicyVersions()).hasSize(1);
  }

  @Test
  public void aConflictingVersionOfARegisteredNamespaceIsRejected() {
    fixture = RateLimitFixture.autoAdvance();
    fixture.context.register(userPolicy());
    RateLimitPolicy otherVersion =
        new RateLimitPolicy(
            "t.scope",
            "test-2",
            "other",
            userPolicy().getBudgets(),
            request -> null,
            RateLimitFeedbackInterpreter.none());
    assertThatThrownBy(() -> fixture.context.register(otherVersion))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("t.scope");
  }

  @Test
  public void aConflictingBudgetDefinitionIsRejectedAndRegistersNothingOfThatPolicy()
      throws Exception {
    fixture = RateLimitFixture.autoAdvance();
    fixture.context.register(userPolicy());
    RateLimitBudget fresh =
        RateLimitBudget.tokenBucket("fresh", ScopeKind.EGRESS, 1, 1, Duration.ofSeconds(1));
    RateLimitBudget conflicting =
        RateLimitBudget.tokenBucket("public", ScopeKind.EGRESS, 99, 1, Duration.ofSeconds(5));
    RateLimitPolicy bad =
        new RateLimitPolicy(
            "t.other",
            "v1",
            "other",
            List.of(fresh, conflicting),
            request -> null,
            RateLimitFeedbackInterpreter.none());
    assertThatThrownBy(() -> fixture.context.register(bad))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("public");
    // all-or-none: neither the namespace nor the fresh budget was captured
    assertThat(fixture.context.diagnostics().getPolicyVersions()).doesNotContainKey("t.other");
    RateLimitBudget freshRedefined =
        RateLimitBudget.tokenBucket("fresh", ScopeKind.EGRESS, 7, 7, Duration.ofSeconds(1));
    fixture.context.register(
        new RateLimitPolicy(
            "t.other",
            "v1",
            "other",
            List.of(freshRedefined),
            request -> null,
            RateLimitFeedbackInterpreter.none()));
    assertThat(fixture.context.diagnostics().getPolicyVersions()).containsKey("t.other");
  }

  @Test
  public void aValidatedOverrideOfASharedBudgetCannotSilentlyReplaceAnotherSharersDefinition() {
    fixture = RateLimitFixture.autoAdvance();
    fixture.context.register(userPolicy());
    RateLimitPolicy raised =
        userPolicy()
            .withBudget(
                RateLimitBudget.tokenBucket("user", ScopeKind.USER, 50, 50, Duration.ofSeconds(10)));
    assertThatThrownBy(() -> fixture.context.register(raised))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void anExplicitlyDisabledPolicyIsRecordedForDiagnosticsOnly() {
    fixture = RateLimitFixture.autoAdvance();
    fixture.context.registerDisabled(userPolicy());
    assertThat(fixture.context.diagnostics().getDisabledNamespaces()).containsExactly("t.scope");
    assertThat(fixture.context.diagnostics().getPolicyVersions()).isEmpty();
  }
}
