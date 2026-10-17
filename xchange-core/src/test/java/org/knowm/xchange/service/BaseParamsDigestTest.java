package org.knowm.xchange.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import org.junit.Test;
import si.mazi.rescu.RestInvocation;

public class BaseParamsDigestTest {

  private static class DummyDigest extends BaseParamsDigest {

    protected DummyDigest(String secretKey, String hmacString) {
      super(secretKey, hmacString);
    }

    @Override
    public String digestParams(RestInvocation restInvocation) {
      return null;
    }
  }

  @Test
  public void testDigestCorrectnessAndReset() {
    String secret = "secretKey123456";
    DummyDigest digest = new DummyDigest(secret, BaseParamsDigest.HMAC_SHA_256);

    String input1 = "message1";
    Mac mac1 = digest.getMac();
    mac1.update(input1.getBytes(StandardCharsets.UTF_8));
    String result1Hex = HexFormat.of().formatHex(mac1.doFinal());

    String input2 = "message2";
    Mac mac2 = digest.getMac();
    mac2.update(input2.getBytes(StandardCharsets.UTF_8));
    String result2Hex = HexFormat.of().formatHex(mac2.doFinal());

    // Call mac1 again for input1 to verify reset works
    Mac mac1Again = digest.getMac();
    mac1Again.update(input1.getBytes(StandardCharsets.UTF_8));
    String result1HexAgain = HexFormat.of().formatHex(mac1Again.doFinal());

    assertThat(result1HexAgain).isEqualTo(result1Hex);
    assertThat(result1Hex).isNotEqualTo(result2Hex);
  }

  @Test
  public void testMultiThreadedCorrectness() throws Exception {
    String secret = "mySecretKeyForTestingMultiThreadedSafety";
    DummyDigest digest = new DummyDigest(secret, BaseParamsDigest.HMAC_SHA_256);

    int threadCount = 10;
    int iterationsPerThread = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      final int threadIdx = i;
      tasks.add(() -> {
        for (int j = 0; j < iterationsPerThread; j++) {
          String msg = "test-message-" + threadIdx + "-" + j;
          Mac mac = digest.getMac();
          mac.update(msg.getBytes(StandardCharsets.UTF_8));
          byte[] output = mac.doFinal();

          // Verify with a fresh standalone Mac instance
          DummyDigest referenceDigest = new DummyDigest(secret, BaseParamsDigest.HMAC_SHA_256);
          Mac refMac = referenceDigest.getMac();
          refMac.update(msg.getBytes(StandardCharsets.UTF_8));
          byte[] refOutput = refMac.doFinal();

          assertThat(output).isEqualTo(refOutput);
        }
        return true;
      });
    }

    List<Future<Boolean>> futures = executor.invokeAll(tasks);
    for (Future<Boolean> future : futures) {
      assertThat(future.get()).isTrue();
    }

    executor.shutdown();
  }
}
