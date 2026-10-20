package org.knowm.xchange.upbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.ws.rs.QueryParam;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.Test;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

public class UpbitJWTDigestTest {

  private static final String ACCESS_KEY = "testAccessKey";
  private static final String SECRET_KEY = "testSecretKeySecretKeySecretKeySecretKey";

  @Test
  public void testDigestParamsWithRequestBody() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);

    RestInvocation invocation = mock(RestInvocation.class);
    Map<Class<? extends Annotation>, Params> paramsMap = new HashMap<>();
    when(invocation.getParamsMap()).thenReturn(paramsMap);
    when(invocation.getRequestBody()).thenReturn("{\"market\":\"KRW-BTC\",\"side\":\"bid\"}");

    String header = digest.digestParams(invocation);
    assertThat(header).startsWith("Bearer ");

    String token = header.substring(7);
    DecodedJWT jwt = JWT.decode(token);
    assertThat(jwt.getClaim("access_key").asString()).isEqualTo(ACCESS_KEY);
    assertThat(jwt.getClaim("nonce").asString()).isNotNull();
    assertThat(jwt.getClaim("query").asString()).isEqualTo("market=KRW-BTC&side=bid");
  }

  @Test
  public void testDigestParamsWithQueryParams() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);

    RestInvocation invocation = mock(RestInvocation.class);
    Params queryParams = mock(Params.class);
    when(queryParams.isEmpty()).thenReturn(false);
    when(queryParams.toString()).thenReturn("market=KRW-BTC");

    Map<Class<? extends Annotation>, Params> paramsMap = new HashMap<>();
    paramsMap.put(QueryParam.class, queryParams);

    when(invocation.getParamsMap()).thenReturn(paramsMap);
    when(invocation.getRequestBody()).thenReturn(null);

    String header = digest.digestParams(invocation);
    assertThat(header).startsWith("Bearer ");

    String token = header.substring(7);
    DecodedJWT jwt = JWT.decode(token);
    assertThat(jwt.getClaim("access_key").asString()).isEqualTo(ACCESS_KEY);
    assertThat(jwt.getClaim("query").asString()).isEqualTo("market=KRW-BTC");
  }

  @Test
  public void testDigestParamsNoParams() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);

    RestInvocation invocation = mock(RestInvocation.class);
    Map<Class<? extends Annotation>, Params> paramsMap = new HashMap<>();
    when(invocation.getParamsMap()).thenReturn(paramsMap);
    when(invocation.getRequestBody()).thenReturn(null);

    String header = digest.digestParams(invocation);
    assertThat(header).startsWith("Bearer ");

    String token = header.substring(7);
    DecodedJWT jwt = JWT.decode(token);
    assertThat(jwt.getClaim("access_key").asString()).isEqualTo(ACCESS_KEY);
    assertThat(jwt.getClaim("query").asString()).isNull();
  }

  @Test
  public void testTokenHeaderAndPayloadShape() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);

    DecodedJWT jwt = decode(digest.digestParams(bodyInvocation("{\"market\":\"KRW-BTC\"}")));

    assertThat(jwt.getAlgorithm()).isEqualTo("HS256");
    assertThat(jwt.getType()).isEqualTo("JWT");
    assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("access_key", "nonce", "query");
    assertThat(UUID.fromString(jwt.getClaim("nonce").asString())).isNotNull();
  }

  @Test
  public void testTokenIsSignedWithSecretKey() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    String token = tokenOf(digest.digestParams(bodyInvocation("{\"market\":\"KRW-BTC\"}")));

    DecodedJWT verified = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token);
    assertThat(verified.getClaim("query").asString()).isEqualTo("market=KRW-BTC");

    JWTVerifier wrongKeyVerifier = JWT.require(Algorithm.HMAC256("anotherSecretKey")).build();
    assertThatThrownBy(() -> wrongKeyVerifier.verify(token))
        .isInstanceOf(SignatureVerificationException.class);
  }

  @Test
  public void testRequestBodyKeepsFieldOrderInQuery() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);

    DecodedJWT jwt =
        decode(
            digest.digestParams(
                bodyInvocation(
                    "{\"market\":\"KRW-BTC\",\"side\":\"bid\",\"volume\":\"0.01\",\"price\":\"10000000\"}")));

    assertThat(jwt.getClaim("query").asString())
        .isEqualTo("market=KRW-BTC&side=bid&volume=0.01&price=10000000");
  }

  @Test
  public void testInvalidRequestBodyFailsFast() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);

    assertThatThrownBy(() -> digest.digestParams(bodyInvocation("{not json")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testRepeatedInvocationsDoNotLeakStateBetweenCalls() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    String bodyA = "{\"market\":\"KRW-BTC\",\"side\":\"bid\"}";
    String bodyB = "{\"uuid\":\"abc\"}";

    DecodedJWT firstA = decode(digest.digestParams(bodyInvocation(bodyA)));
    DecodedJWT b = decode(digest.digestParams(bodyInvocation(bodyB)));
    DecodedJWT secondA = decode(digest.digestParams(bodyInvocation(bodyA)));
    DecodedJWT none = decode(digest.digestParams(bodyInvocation(null)));

    assertThat(firstA.getClaim("query").asString()).isEqualTo("market=KRW-BTC&side=bid");
    assertThat(b.getClaim("query").asString()).isEqualTo("uuid=abc");
    assertThat(secondA.getClaim("query").asString()).isEqualTo("market=KRW-BTC&side=bid");
    assertThat(none.getClaim("query").isMissing()).isTrue();
    assertThat(
            Stream.of(firstA, b, secondA, none)
                .map(jwt -> jwt.getClaim("nonce").asString())
                .distinct())
        .hasSize(4);
  }

  @Test
  public void testConcurrentInvocationsProduceValidIndependentTokens() throws Exception {
    int threads = 8;
    int callsPerThread = 50;
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<List<String>>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        int threadIndex = t;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  List<String> tokens = new ArrayList<>();
                  for (int i = 0; i < callsPerThread; i++) {
                    String body =
                        "{\"market\":\"KRW-BTC\",\"thread\":\""
                            + threadIndex
                            + "\",\"call\":\""
                            + i
                            + "\"}";
                    tokens.add(digest.digestParams(bodyInvocation(body)));
                  }
                  return tokens;
                }));
      }
      start.countDown();

      Set<String> nonces = new HashSet<>();
      JWTVerifier verifier = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build();
      for (int t = 0; t < threads; t++) {
        List<String> tokens = futures.get(t).get(60, TimeUnit.SECONDS);
        assertThat(tokens).hasSize(callsPerThread);
        for (int i = 0; i < callsPerThread; i++) {
          DecodedJWT jwt = verifier.verify(tokenOf(tokens.get(i)));
          assertThat(jwt.getClaim("access_key").asString()).isEqualTo(ACCESS_KEY);
          assertThat(jwt.getClaim("query").asString())
              .isEqualTo("market=KRW-BTC&thread=" + t + "&call=" + i);
          nonces.add(jwt.getClaim("nonce").asString());
        }
      }
      assertThat(nonces).hasSize(threads * callsPerThread);
    } finally {
      executor.shutdownNow();
    }
  }

  private static RestInvocation bodyInvocation(String body) {
    RestInvocation invocation = mock(RestInvocation.class);
    when(invocation.getParamsMap()).thenReturn(new HashMap<>());
    when(invocation.getRequestBody()).thenReturn(body);
    return invocation;
  }

  private static String tokenOf(String header) {
    assertThat(header).startsWith("Bearer ");
    return header.substring("Bearer ".length());
  }

  private static DecodedJWT decode(String header) {
    return JWT.decode(tokenOf(header));
  }
}
