package org.knowm.xchange.upbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.QueryParam;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

class UpbitJWTDigestTest {

  private static final String ACCESS_KEY = "testAccessKey";
  private static final String SECRET_KEY = "testSecretKeySecretKeySecretKeySecretKey";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void createsValidHs256Token() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    Map<String, String> claims = new LinkedHashMap<>();
    claims.put("access_key", ACCESS_KEY);
    claims.put("nonce", "nonce-1");
    claims.put("query", "market=KRW-BTC&side=bid");

    String token = digest.createToken(claims);
    JsonNode header = tokenPart(token, 0);
    assertThat(header.get("alg").asText()).isEqualTo("HS256");
    assertThat(header.get("typ").asText()).isEqualTo("JWT");
    JsonNode payload = tokenPart(token, 1);
    assertThat(payload.get("access_key").asText()).isEqualTo(ACCESS_KEY);
    assertThat(payload.get("nonce").asText()).isEqualTo("nonce-1");
    assertThat(payload.get("query").asText()).isEqualTo("market=KRW-BTC&side=bid");
    assertThat(isCorrectlySigned(token, SECRET_KEY)).isTrue();
    assertThat(token).doesNotContain("=");
  }

  @Test
  void testDigestParamsWithRequestBody() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    JsonNode jwt = decode(digest.digestParams(bodyInvocation("{\"market\":\"KRW-BTC\",\"side\":\"bid\"}")));
    assertThat(jwt.get("access_key").asText()).isEqualTo(ACCESS_KEY);
    assertThat(jwt.get("nonce").asText()).isNotNull();
    assertThat(jwt.get("query").asText()).isEqualTo("market=KRW-BTC&side=bid");
  }

  @Test
  void testDigestParamsWithQueryParams() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    RestInvocation invocation = mock(RestInvocation.class);
    Params queryParams = mock(Params.class);
    when(queryParams.isEmpty()).thenReturn(false);
    when(queryParams.toString()).thenReturn("market=KRW-BTC");
    Map<Class<? extends Annotation>, Params> paramsMap = new HashMap<>();
    paramsMap.put(QueryParam.class, queryParams);
    when(invocation.getParamsMap()).thenReturn(paramsMap);
    when(invocation.getRequestBody()).thenReturn(null);

    JsonNode jwt = decode(digest.digestParams(invocation));
    assertThat(jwt.get("access_key").asText()).isEqualTo(ACCESS_KEY);
    assertThat(jwt.get("query").asText()).isEqualTo("market=KRW-BTC");
  }

  @Test
  void testDigestParamsNoParams() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    JsonNode jwt = decode(digest.digestParams(bodyInvocation(null)));
    assertThat(jwt.get("access_key").asText()).isEqualTo(ACCESS_KEY);
    assertThat(jwt.has("query")).isFalse();
  }

  @Test
  void testTokenHeaderAndPayloadShape() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    String token = tokenOf(digest.digestParams(bodyInvocation("{\"market\":\"KRW-BTC\"}")));
    JsonNode header = tokenPart(token, 0);
    JsonNode jwt = tokenPart(token, 1);
    assertThat(header.get("alg").asText()).isEqualTo("HS256");
    assertThat(header.get("typ").asText()).isEqualTo("JWT");
    Set<String> claimNames = new HashSet<>();
    jwt.fieldNames().forEachRemaining(claimNames::add);
    assertThat(claimNames).containsExactlyInAnyOrder("access_key", "nonce", "query");
    assertThat(UUID.fromString(jwt.get("nonce").asText())).isNotNull();
  }

  @Test
  void testTokenIsSignedWithSecretKey() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    String token = tokenOf(digest.digestParams(bodyInvocation("{\"market\":\"KRW-BTC\"}")));
    assertThat(isCorrectlySigned(token, SECRET_KEY)).isTrue();
    assertThat(tokenPart(token, 1).get("query").asText()).isEqualTo("market=KRW-BTC");
    assertThat(isCorrectlySigned(token, "anotherSecretKey")).isFalse();
  }

  @Test
  void testTokenPreservesUtf8AndEscapedClaims() throws Exception {
    String accessKey = "key-\uD55C-\"quoted\"";
    String secretKey = "secret-\uBE44";
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(accessKey, secretKey);
    String token =
        tokenOf(
            digest.digestParams(
                bodyInvocation("{\"market\":\"KRW-BTC\",\"memo\":\"caf\\u00E9\"}")));

    assertThat(isCorrectlySigned(token, secretKey)).isTrue();
    JsonNode jwt = tokenPart(token, 1);
    assertThat(jwt.get("access_key").asText()).isEqualTo(accessKey);
    assertThat(jwt.get("query").asText()).isEqualTo("market=KRW-BTC&memo=caf\u00E9");
  }

  @Test
  void testRequestBodyKeepsFieldOrderInQuery() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    JsonNode jwt =
        decode(
            digest.digestParams(
                bodyInvocation(
                    "{\"market\":\"KRW-BTC\",\"side\":\"bid\",\"volume\":\"0.01\",\"price\":\"10000000\"}")));
    assertThat(jwt.get("query").asText())
        .isEqualTo("market=KRW-BTC&side=bid&volume=0.01&price=10000000");
  }

  @Test
  void testInvalidRequestBodyFailsFast() {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    assertThatThrownBy(() -> digest.digestParams(bodyInvocation("{not json")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testRepeatedInvocationsDoNotLeakStateBetweenCalls() throws Exception {
    UpbitJWTDigest digest = UpbitJWTDigest.createInstance(ACCESS_KEY, SECRET_KEY);
    String bodyA = "{\"market\":\"KRW-BTC\",\"side\":\"bid\"}";
    String bodyB = "{\"uuid\":\"abc\"}";
    JsonNode firstA = decode(digest.digestParams(bodyInvocation(bodyA)));
    JsonNode b = decode(digest.digestParams(bodyInvocation(bodyB)));
    JsonNode secondA = decode(digest.digestParams(bodyInvocation(bodyA)));
    JsonNode none = decode(digest.digestParams(bodyInvocation(null)));
    assertThat(firstA.get("query").asText()).isEqualTo("market=KRW-BTC&side=bid");
    assertThat(b.get("query").asText()).isEqualTo("uuid=abc");
    assertThat(secondA.get("query").asText()).isEqualTo("market=KRW-BTC&side=bid");
    assertThat(none.has("query")).isFalse();
    assertThat(Stream.of(firstA, b, secondA, none).map(jwt -> jwt.get("nonce").asText()).distinct())
        .hasSize(4);
  }

  @Test
  void testConcurrentInvocationsProduceValidIndependentTokens() throws Exception {
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
      for (int t = 0; t < threads; t++) {
        List<String> tokens = futures.get(t).get(60, TimeUnit.SECONDS);
        assertThat(tokens).hasSize(callsPerThread);
        for (int i = 0; i < callsPerThread; i++) {
          String token = tokenOf(tokens.get(i));
          assertThat(isCorrectlySigned(token, SECRET_KEY)).isTrue();
          JsonNode jwt = tokenPart(token, 1);
          assertThat(jwt.get("access_key").asText()).isEqualTo(ACCESS_KEY);
          assertThat(jwt.get("query").asText())
              .isEqualTo("market=KRW-BTC&thread=" + t + "&call=" + i);
          nonces.add(jwt.get("nonce").asText());
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

  private static JsonNode decode(String header) throws IOException {
    return tokenPart(tokenOf(header), 1);
  }

  private static JsonNode tokenPart(String token, int index) throws IOException {
    String[] parts = token.split("\\.");
    assertThat(parts).hasSize(3);
    return MAPPER.readTree(Base64.getUrlDecoder().decode(parts[index]));
  }

  private static boolean isCorrectlySigned(String token, String secret)
      throws GeneralSecurityException {
    String[] parts = token.split("\\.");
    assertThat(parts).hasSize(3);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
    return MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]));
  }
}
