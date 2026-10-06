package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.knowm.xchange.client.ratelimit.RateLimitedProxyFixture.SCOPE;
import static org.knowm.xchange.client.ratelimit.RateLimitedProxyFixture.SEC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.client.ratelimit.LocalHttpServer.Reply;
import org.knowm.xchange.client.ratelimit.LocalHttpServer.Seen;
import org.knowm.xchange.client.ratelimit.RateLimitedProxyFixture.Api;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Dispatch;
import org.knowm.xchange.exceptions.RateLimitTerminatedException.Reason;
import si.mazi.rescu.ClientConfig;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RateLimitedRestProxies;
import si.mazi.rescu.RestInvocation;

/**
 * AC9 (HTTP feedback observed before rescu translation), AC13 (admission precedes signing and
 * every replay signs afresh) and AC15 (wire attempts equal admissions) against a loopback server.
 */
public class RateLimitedRestProxyTest {

  private RateLimitFixture fixture;
  private LocalHttpServer server;
  private List<Reply> script;
  private final AtomicInteger served = new AtomicInteger();
  private final List<RateLimitRequest> seen = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    fixture = RateLimitFixture.autoAdvance();
    script = Collections.synchronizedList(new ArrayList<>());
    server =
        new LocalHttpServer(
            request -> {
              int index = served.getAndIncrement();
              return index < script.size() ? script.get(index) : Reply.json(200, "\"ok\"");
            });
  }

  @After
  public void tearDown() throws IOException {
    server.close();
    fixture.cleanup();
  }

  private Api proxy(long capacity) {
    RateLimitPolicy policy = RateLimitedProxyFixture.policy(seen, capacity);
    fixture.context.register(policy);
    return RateLimitedRestProxies.createProxy(
        Api.class, server.baseUrl(), new ClientConfig(), policy, fixture.context, SCOPE);
  }

  private long admissions() {
    return fixture.context.diagnostics().getAdmissions();
  }

  // ---- AC9 ------------------------------------------------------------------------------

  @Test
  public void retryAfterSecondsIsObservedBeforeTranslationAndTheReplayWaitsForIt()
      throws Exception {
    script.add(Reply.json(429, "{\"error\":\"slow down\"}").header("Retry-After", "10"));
    Api api = proxy(100);

    assertThat(api.list()).isEqualTo("ok");

    assertThat(server.requests()).hasSize(2);
    assertThat(fixture.now()).isEqualTo(10 * SEC);
    assertThat(fixture.context.diagnostics().getRetries()).isEqualTo(1);
  }

  @Test
  public void httpDateRetryAfterIsObservedWithAnyHeaderNameCase() throws Exception {
    // the fake wall clock starts at 2026-01-01T00:00:00Z
    script.add(
        Reply.json(429, "{}").header("rEtRy-AfTeR", "Thu, 01 Jan 2026 00:00:07 GMT"));
    Api api = proxy(100);

    assertThat(api.list()).isEqualTo("ok");

    assertThat(server.requests()).hasSize(2);
    assertThat(fixture.now()).isEqualTo(7 * SEC);
  }

  @Test
  public void aNonReplayable429TerminatesButItsCooldownDelaysEverySharer() throws Exception {
    script.add(Reply.json(429, "{}").header("Retry-After", "10"));
    Api api = proxy(100);

    assertThatThrownBy(() -> api.create("1"))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.REMOTE_PRESSURE_EXHAUSTED);
              assertThat(e.getDispatch()).isEqualTo(Dispatch.REJECTED);
            });
    assertThat(server.requests()).hasSize(1);
    assertThat(fixture.now()).isZero();

    assertThat(api.one("a")).isEqualTo("ok");

    assertThat(server.requests()).hasSize(2);
    assertThat(fixture.now()).isEqualTo(10 * SEC);
  }

  // ---- operation key and authentication rule ----------------------------------------------

  @Test
  public void operationKeysAreMethodPlusPathTemplateAndAuthenticationFollowsTheDigestArgument()
      throws Exception {
    Api api = proxy(100);
    ParamsDigest digest = digestRecording(new ArrayList<>(), new AtomicInteger());

    api.list();
    api.one("btc usd");
    api.signed(digest);
    api.signed(null);
    api.redirect();

    synchronized (seen) {
      assertThat(seen)
          .contains(
              new RateLimitRequest("GET api/v3/things", false),
              new RateLimitRequest("GET api/v3/things/{id}", false, Map.of("id", "btc usd")),
              new RateLimitRequest("GET api/v3/signed", true),
              new RateLimitRequest("GET api/v3/signed", false),
              new RateLimitRequest("GET api/v3/redirect", false));
    }
    assertThat(server.requests().get(1).target).isEqualTo("/api/v3/things/btc+usd");
  }

  @Test
  public void queryFormAndPathArgumentsReachTheClassifierButHeadersAndNullsDoNot()
      throws Exception {
    Api api = proxy(100);

    api.page(500, "secret-key");
    api.page(null, "secret-key");
    api.create("7");

    synchronized (seen) {
      assertThat(seen)
          .containsExactly(
              new RateLimitRequest("GET api/v3/things", false, Map.of("limit", "500")),
              new RateLimitRequest("GET api/v3/things", false),
              new RateLimitRequest("POST api/v3/orders", false, Map.of("n", "7")));
    }
  }

  // ---- AC13 -----------------------------------------------------------------------------

  @Test
  public void paramsDigestRunsOnlyAfterAdmissionAndFreshOnEveryReplay() throws Exception {
    // capacity 1, refill 1/s: the second call must queue until fake second 1
    script.add(Reply.json(200, "\"first\""));
    script.add(Reply.json(429, "{}").header("Retry-After", "3"));
    Api api = proxy(1);
    List<Long> digestTimes = Collections.synchronizedList(new ArrayList<>());
    ParamsDigest digest = digestRecording(digestTimes, new AtomicInteger());

    assertThat(api.list()).isEqualTo("first");
    assertThat(fixture.now()).isZero();
    assertThat(api.signed(digest)).isEqualTo("ok");

    assertThat(digestTimes).containsExactly(1 * SEC, 4 * SEC);
    List<Seen> wire = server.requests();
    assertThat(wire).hasSize(3);
    assertThat(wire.get(1).header("X-Sign")).isEqualTo("sig-1");
    assertThat(wire.get(2).header("X-Sign")).isEqualTo("sig-2");
    assertThat(admissions()).isEqualTo(3);
  }

  private ParamsDigest digestRecording(List<Long> times, AtomicInteger counter) {
    return new ParamsDigest() {
      @Override
      public String digestParams(RestInvocation restInvocation) {
        times.add(fixture.now());
        return "sig-" + counter.incrementAndGet();
      }
    };
  }

  // ---- AC15 -----------------------------------------------------------------------------

  @Test
  public void everyWireAttemptIsAdmittedExactlyOnce() throws Exception {
    script.add(Reply.json(429, "{}").header("Retry-After", "2"));
    Api api = proxy(100);

    api.list();
    api.one("a");
    api.create("1");

    assertThat(server.requests()).hasSize(4);
    assertThat(admissions()).isEqualTo(4);
  }

  @Test
  public void aRedirectIsReturnedToTheCallerAndNeverFollowed() throws Exception {
    script.add(
        Reply.json(302, "").header("Location", server.baseUrl() + "/elsewhere"));
    Api api = proxy(100);

    assertThatThrownBy(api::redirect).isInstanceOf(Exception.class);

    assertThat(server.requests()).hasSize(1);
    assertThat(server.requests().get(0).target).isEqualTo("/api/v3/redirect/");
    assertThat(admissions()).isEqualTo(1);
  }

  @Test
  public void aDroppedPostIsNeverResentAndNoConnectionIsReused()
      throws Exception {
    script.add(Reply.json(200, "\"one\""));
    script.add(Reply.drop());
    Api api = proxy(100);

    assertThat(api.create("1")).isEqualTo("one");
    assertThatThrownBy(() -> api.create("2")).isInstanceOf(IOException.class);
    assertThat(api.create("3")).isEqualTo("ok");

    List<Seen> wire = server.requests();
    assertThat(wire).extracting(r -> r.body).containsExactly("n=1", "n=2", "n=3");
    assertThat(wire).extracting(r -> r.connectionId).doesNotHaveDuplicates();
    assertThat(admissions()).isEqualTo(3);
  }
}
