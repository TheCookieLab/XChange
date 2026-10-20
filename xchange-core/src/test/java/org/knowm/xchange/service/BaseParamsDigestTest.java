package org.knowm.xchange.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import si.mazi.rescu.RestInvocation;

public class BaseParamsDigestTest {

  private static final String SECRET = "mySecretKeyForTestingMultiThreadedSafety";
  private static final String ALGORITHM = BaseParamsDigest.HMAC_SHA_256;

  private static class DummyDigest extends BaseParamsDigest {

    protected DummyDigest(String secretKey, String hmacString) {
      super(secretKey, hmacString);
    }

    @Override
    public String digestParams(RestInvocation restInvocation) {
      return null;
    }

    byte[] signAtomically(String message) {
      return hmac(message.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static byte[] reference(String secret, String message) throws Exception {
    Mac mac = Mac.getInstance(ALGORITHM);
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
    return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] bytes(String message) {
    return message.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  public void getMacReturnsIndependentInstancesOnSameThread() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    Mac first = digest.getMac();
    Mac second = digest.getMac();
    assertThat(second).isNotSameAs(first);

    first.update(bytes("message1"));
    second.update(bytes("message2"));

    assertThat(first.doFinal()).isEqualTo(reference(SECRET, "message1"));
    assertThat(second.doFinal()).isEqualTo(reference(SECRET, "message2"));
  }

  @Test
  public void getMacResultIsUnaffectedByLaterGetMacCalls() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    Mac first = digest.getMac();
    first.update(bytes("part1-"));
    Mac second = digest.getMac();
    second.update(bytes("other"));
    first.update(bytes("part2"));

    assertThat(first.doFinal()).isEqualTo(reference(SECRET, "part1-part2"));
  }

  @Test
  public void getMacResultIsReusableAfterDoFinal() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    Mac mac = digest.getMac();
    mac.update(bytes("message1"));
    byte[] first = mac.doFinal();
    mac.update(bytes("message1"));
    byte[] again = mac.doFinal();

    assertThat(again).isEqualTo(first);
    assertThat(first).isNotEqualTo(reference(SECRET, "message2"));
  }

  @Test
  public void repeatedAtomicSigningMatchesFreshReference() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    for (int i = 0; i < 50; i++) {
      String message = "message-" + i;
      assertThat(digest.signAtomically(message)).isEqualTo(reference(SECRET, message));
    }
  }

  @Test
  public void atomicSigningDoesNotDisturbOpenGetMacComputation() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    Mac open = digest.getMac();
    open.update(bytes("part1-"));
    byte[] interleaved = digest.signAtomically("interleaved");
    open.update(bytes("part2"));

    assertThat(interleaved).isEqualTo(reference(SECRET, "interleaved"));
    assertThat(open.doFinal()).isEqualTo(reference(SECRET, "part1-part2"));
  }

  @Test
  public void getMacDoesNotDisturbAtomicSigning() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    byte[] before = digest.signAtomically("message");
    Mac mac = digest.getMac();
    mac.update(bytes("noise"));
    byte[] after = digest.signAtomically("message");

    assertThat(after).isEqualTo(before);
    assertThat(after).isEqualTo(reference(SECRET, "message"));
  }

  @Test
  public void atomicSigningKeepsDigestsWithDifferentKeysSeparate() throws Exception {
    DummyDigest digestA = new DummyDigest("secretA", ALGORITHM);
    DummyDigest digestB = new DummyDigest("secretB", ALGORITHM);

    byte[] a = digestA.signAtomically("message");
    byte[] b = digestB.signAtomically("message");
    byte[] aAgain = digestA.signAtomically("message");

    assertThat(a).isEqualTo(reference("secretA", "message")).isEqualTo(aAgain);
    assertThat(b).isEqualTo(reference("secretB", "message")).isNotEqualTo(a);
  }

  @Test
  public void concurrentUseIsCorrect() throws Exception {
    DummyDigest digest = new DummyDigest(SECRET, ALGORITHM);

    int threadCount = 8;
    int iterationsPerThread = 500;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Callable<Boolean>> tasks = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int threadIdx = i;
        tasks.add(
            () -> {
              start.await();
              for (int j = 0; j < iterationsPerThread; j++) {
                String message = "test-message-" + threadIdx + "-" + j;
                byte[] expected = reference(SECRET, message);

                Mac open = digest.getMac();
                open.update(bytes(message.substring(0, 5)));
                byte[] atomic = digest.signAtomically(message);
                open.update(bytes(message.substring(5)));

                if (!java.util.Arrays.equals(expected, atomic)
                    || !java.util.Arrays.equals(expected, open.doFinal())) {
                  return false;
                }
              }
              return true;
            });
      }

      List<Future<Boolean>> futures = new ArrayList<>();
      for (Callable<Boolean> task : tasks) {
        futures.add(executor.submit(task));
      }
      start.countDown();
      for (Future<Boolean> future : futures) {
        assertThat(future.get()).isTrue();
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
