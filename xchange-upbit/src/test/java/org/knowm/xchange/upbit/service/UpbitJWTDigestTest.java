package org.knowm.xchange.upbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.QueryParam;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
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
  public void benchmarkObjectMapperInstantiation() throws Exception {
    String body = "{\"market\":\"KRW-BTC\",\"side\":\"bid\",\"volume\":\"0.01\",\"price\":\"10000000\"}";

    // Benchmark 1: Uncached ObjectMapper (creating new ObjectMapper every call)
    int iterations = 10000;

    // Warmup uncached
    for (int i = 0; i < 1000; i++) {
      ObjectMapper mapper = new ObjectMapper();
      mapper.readValue(body, Map.class);
    }

    long startUncached = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      ObjectMapper mapper = new ObjectMapper();
      mapper.readValue(body, Map.class);
    }
    long elapsedUncachedNs = System.nanoTime() - startUncached;

    // Benchmark 2: Cached ObjectMapper
    ObjectMapper sharedMapper = new ObjectMapper();
    // Warmup cached
    for (int i = 0; i < 1000; i++) {
      sharedMapper.readValue(body, Map.class);
    }

    long startCached = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      sharedMapper.readValue(body, Map.class);
    }
    long elapsedCachedNs = System.nanoTime() - startCached;

    double uncachedMs = elapsedUncachedNs / 1_000_000.0;
    double cachedMs = elapsedCachedNs / 1_000_000.0;
    double speedup = uncachedMs / cachedMs;

    System.out.println(String.format("=== ObjectMapper Benchmark (%d iterations) ===", iterations));
    System.out.println(String.format("Uncached (new ObjectMapper()): %.2f ms", uncachedMs));
    System.out.println(String.format("Cached (shared MAPPER):        %.2f ms", cachedMs));
    System.out.println(String.format("Speedup:                       %.2fx faster", speedup));

    assertThat(cachedMs).isLessThan(uncachedMs);
  }
}
