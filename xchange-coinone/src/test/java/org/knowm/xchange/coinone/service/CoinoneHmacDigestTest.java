package org.knowm.xchange.coinone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import si.mazi.rescu.RestInvocation;

class CoinoneHmacDigestTest {

  private static final String NONCE_BODY = "{\"nonce\":\"12345\"}";
  private static final String UNICODE_BODY = "{\"memo\":\"서울 €\"}";
  private static final String UNICODE_SECRET = "sëcret";
  private static final String NONCE_SIGNATURE =
      "4cf20467978e287790b48c03ce4dd6a797d4de7365dcb3b6e7f7ba3ad92819a4"
          + "a20c14a653910d40814ae54875c892424658238b6a79354fe01eb09a67e748c3";
  private static final String UNICODE_SIGNATURE =
      "d14ce8dd2fa7d3f58d4f9f4a113d7e846886a272a11c5b0532878b7419ea5166"
          + "3a381b736c26e40da4f7e360819cb2eb3c1bcaed0b3a041b3a66e867a48bec4c";

  @Test
  void signsTheSameUtf8PayloadSentInTheHeader() {
    RestInvocation request = invocation(UNICODE_BODY);

    assertThat(CoinonePayloadDigest.createInstance().digestParams(request))
        .isEqualTo("eyJtZW1vIjoi7ISc7Jq4IOKCrCJ9");
    assertThat(CoinoneHmacDigest.createInstance(UNICODE_SECRET).digestParams(request))
        .isEqualTo(UNICODE_SIGNATURE);
  }

  @Test
  void preservesAsciiSignatureVector() {
    RestInvocation request = invocation(NONCE_BODY);

    assertThat(CoinonePayloadDigest.createInstance().digestParams(request))
        .isEqualTo("eyJub25jZSI6IjEyMzQ1In0=");
    assertThat(CoinoneHmacDigest.createInstance("secretKey").digestParams(request))
        .isEqualTo(
            "1f16113ea85d2dd505fcb58d5fce94339776c11c9911f74570eb5c8563ef30f3"
                + "d8713b456b5b2b18e1d614705f4765dd2eb7a7e375e4031cf45aabb2099cc466");
  }

  @Test
  void concurrentRequestsDoNotShareMacState() throws Exception {
    CoinoneHmacDigest digest = CoinoneHmacDigest.createInstance(UNICODE_SECRET);
    RestInvocation nonceRequest = invocation(NONCE_BODY);
    RestInvocation unicodeRequest = invocation(UNICODE_BODY);
    int threadCount = 8;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int threadIndex = i;
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  for (int j = 0; j < 200; j++) {
                    boolean unicode = (threadIndex + j) % 2 == 0;
                    String actual = digest.digestParams(unicode ? unicodeRequest : nonceRequest);
                    if (!(unicode ? UNICODE_SIGNATURE : NONCE_SIGNATURE).equals(actual)) {
                      return false;
                    }
                  }
                  return true;
                }));
      }
      assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<Boolean> future : futures) {
        assertThat(future.get(15, TimeUnit.SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static RestInvocation invocation(String body) {
    RestInvocation request = mock(RestInvocation.class);
    when(request.getRequestBody()).thenReturn(body);
    return request;
  }
}
