package org.knowm.xchange.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.knowm.xchange.client.ratelimit.RateLimitedProxyFixture.SCOPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ratelimit.LocalHttpServer.Reply;
import org.knowm.xchange.client.ratelimit.RateLimitedProxyFixture.Api;
import si.mazi.rescu.ClientConfig;
import si.mazi.rescu.Interceptor;
import si.mazi.rescu.RestProxyFactoryImpl;
import si.mazi.rescu.clients.HttpConnectionType;

/** AC10: every proxy built from an enabled specification is admitted; bypass shapes are rejected. */
public class ExchangeRestProxyBuilderRateLimitTest {

  private RateLimitFixture fixture;
  private LocalHttpServer server;
  private RateLimitPolicy policy;
  private ExchangeSpecification spec;
  private final List<RateLimitRequest> seen = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    fixture = RateLimitFixture.autoAdvance();
    server = new LocalHttpServer(request -> Reply.json(200, "\"ok\""));
    policy = RateLimitedProxyFixture.policy(seen, 100);
    fixture.context.register(policy);
    spec = new ExchangeSpecification(org.knowm.xchange.Exchange.class);
    spec.setPlainTextUri(server.baseUrl());
    ExchangeSpecification.ResilienceSpecification resilience = spec.getResilience();
    resilience.setRateLimiterEnabled(true);
    resilience.setRateLimitPolicy(policy);
    resilience.setRateLimitContext(fixture.context);
    resilience.setRateLimitUserScope(SCOPE);
  }

  @After
  public void tearDown() throws IOException {
    server.close();
    fixture.cleanup();
  }

  private long admissions() {
    return fixture.context.diagnostics().getAdmissions();
  }

  @Test
  public void aStandardProxyAndASecondMethodCoveredByAFamilyRuleAreAdmitted() throws Exception {
    Api api = ExchangeRestProxyBuilder.forInterface(Api.class, spec).build();

    assertThat(api.list()).isEqualTo("ok");
    assertThat(api.one("btc")).isEqualTo("ok");
    assertThat(api.one("eth")).isEqualTo("ok");

    assertThat(server.requests()).hasSize(3);
    assertThat(admissions()).isEqualTo(3);
    synchronized (seen) {
      assertThat(seen)
          .contains(
              new RateLimitRequest("GET api/v3/things", false),
              new RateLimitRequest("GET api/v3/things/{id}", false, Map.of("id", "btc")));
    }
  }

  @Test
  public void aCustomInterceptorWrapsAdmissionAndCanNeitherBypassNorEscapeIt() throws Exception {
    AtomicInteger intercepted = new AtomicInteger();
    Interceptor once =
        (handler, proxy, method, args) -> {
          intercepted.incrementAndGet();
          return handler.invoke(proxy, method, args);
        };
    Interceptor twice =
        (handler, proxy, method, args) -> {
          handler.invoke(proxy, method, args);
          return handler.invoke(proxy, method, args);
        };

    Api single = ExchangeRestProxyBuilder.forInterface(Api.class, spec).customInterceptor(once).build();
    single.list();
    assertThat(intercepted).hasValue(1);
    assertThat(admissions()).isEqualTo(1);

    Api doubled = ExchangeRestProxyBuilder.forInterface(Api.class, spec).customInterceptor(twice).build();
    doubled.one("btc");
    assertThat(server.requests()).hasSize(3);
    assertThat(admissions()).isEqualTo(3);
  }

  @Test
  public void aCustomRestProxyFactoryIsRejectedBecauseItWouldBypassAdmission() {
    assertThatThrownBy(
            () ->
                ExchangeRestProxyBuilder.forInterface(Api.class, spec)
                    .restProxyFactory(new RestProxyFactoryImpl())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("custom IRestProxyFactory")
        .hasMessageContaining("test.rescu");
  }

  @Test
  public void theApacheConnectionTypeIsRejected() {
    ClientConfig apache = new ClientConfig();
    apache.setConnectionType(HttpConnectionType.apache);
    assertThatThrownBy(
            () -> ExchangeRestProxyBuilder.forInterface(Api.class, spec).clientConfig(apache).build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apache");
  }

  @Test
  public void settingsTheSingleAttemptTransportCannotHonourAreRejected() {
    ClientConfig verifier = new ClientConfig();
    verifier.setHostnameVerifier((host, session) -> true);
    assertThatThrownBy(
            () -> ExchangeRestProxyBuilder.forInterface(Api.class, spec).clientConfig(verifier).build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HostnameVerifier");
  }

  @Test
  public void anEnabledPolicyWithoutAContextIsRejected() {
    spec.getResilience().setRateLimitContext(null);
    assertThatThrownBy(() -> ExchangeRestProxyBuilder.forInterface(Api.class, spec).build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no rate-limit context");
  }

  @Test
  public void aDisabledLimiterKeepsThePlainProxyAndAllowsACustomFactory() throws Exception {
    spec.getResilience().setRateLimiterEnabled(false);
    Api api =
        ExchangeRestProxyBuilder.forInterface(Api.class, spec)
            .restProxyFactory(new RestProxyFactoryImpl())
            .build();

    assertThat(api.list()).isEqualTo("ok");

    assertThat(server.requests()).hasSize(1);
    assertThat(admissions()).isZero();
  }
}
