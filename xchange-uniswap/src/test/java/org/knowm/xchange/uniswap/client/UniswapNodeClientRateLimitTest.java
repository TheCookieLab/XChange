package org.knowm.xchange.uniswap.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.uniswap.RpcStub;
import org.knowm.xchange.uniswap.RpcStub.Reply;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.exceptions.ClientConnectionException;

/**
 * Loopback proof that the JSON-RPC transport owns exactly one rate admission per wire attempt, that
 * the HTTP status of a rejection reaches the limiter, and that replay is bounded and never applies
 * to the transaction broadcast.
 */
class UniswapNodeClientRateLimitTest {

  private static final String ADDRESS = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf";
  private static final BigInteger BLOCK = BigInteger.valueOf(100);

  private RpcStub stub;
  private RateLimitContext context;
  private RateLimitPolicy policy;
  private UniswapNodeClient client;

  @BeforeEach
  void setUp() throws IOException {
    stub = new RpcStub();
    context = new RateLimitContext();
    // a tiny fallback backoff keeps replays fast without relying on provider headers
    policy =
        UniswapRateLimitPolicy.defaultPolicy()
            .withFallbackBackoff(Duration.ofMillis(5), Duration.ofMillis(5), 0.0);
    client = UniswapNodeClient.create(stub.url(), 2_000, 2_000, policy, context, null);
  }

  @AfterEach
  void tearDown() {
    client.close();
    context.close();
    stub.close();
  }

  @Test
  void everyClientCallIsAdmittedExactlyOncePerWireAttempt() throws Exception {
    Map<String, Callback> calls = clientCalls();
    assertThat(new TreeSet<>(calls.keySet())).isEqualTo(publicClientMethodNames());

    for (Callback call : calls.values()) {
      call.run(client);
    }
    // eth_getBlockByNumber is only reached when the node reports no fee history
    stub.respondWith(
        (arrival, method) ->
            "eth_feeHistory".equals(method)
                ? Reply.result("{\"oldestBlock\":\"0x1\",\"baseFeePerGas\":[],\"gasUsedRatio\":[]}")
                : Reply.success());
    assertThat(client.baseFeePerGas()).isEqualTo(BigInteger.valueOf(7));

    assertThat(stub.methods()).containsAll(UniswapRateLimitPolicy.methods());
    assertThat(UniswapRateLimitPolicy.methods()).containsAll(stub.methods());
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(stub.arrivals());
    assertThat(context.diagnostics().getRatePressureEvents()).isZero();
    assertThat(context.diagnostics().getRetries()).isZero();
  }

  @Test
  void rateLimitedReadIsReplayedWithFreshAdmissionAndFeedbackIsObservedOnce() throws Exception {
    stub.respondWith((arrival, method) -> arrival == 1 ? Reply.tooManyRequests() : Reply.success());

    assertThat(client.chainId()).isEqualTo(BigInteger.ONE);

    assertThat(stub.methods()).containsExactly("eth_chainId", "eth_chainId");
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(2);
    assertThat(context.diagnostics().getRatePressureEvents()).isEqualTo(1);
    assertThat(context.diagnostics().getRetries()).isEqualTo(1);
  }

  @Test
  void jsonRpcLimitExceededOnAnOkResponseIsARateRejection() throws Exception {
    stub.respondWith(
        (arrival, method) ->
            arrival == 1
                ? Reply.jsonRpcError(UniswapRateLimitPolicy.LIMIT_EXCEEDED_CODE, "Limit exceeded")
                : Reply.success());

    assertThat(client.blockNumber()).isEqualTo(BLOCK);

    assertThat(stub.arrivals()).isEqualTo(2);
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(2);
    assertThat(context.diagnostics().getRatePressureEvents()).isEqualTo(1);
  }

  @Test
  void persistentRateLimitingIsBoundedByMaxAttempts() {
    stub.respondWith((arrival, method) -> Reply.tooManyRequests());
    client.close();
    client =
        UniswapNodeClient.create(
            stub.url(), 2_000, 2_000, policy.withMaxAttempts(3), context, null);

    assertThatThrownBy(() -> client.chainId())
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            terminated -> {
              assertThat(terminated.getDispatch())
                  .isEqualTo(RateLimitTerminatedException.Dispatch.REJECTED);
              assertThat(terminated.getReason())
                  .isEqualTo(RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED);
            });

    assertThat(stub.arrivals()).isEqualTo(3);
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(3);
    assertThat(context.diagnostics().getRatePressureEvents()).isEqualTo(3);
  }

  @Test
  void rejectedBroadcastIsSentOnceAndNeverReplayed() {
    stub.respondWith((arrival, method) -> Reply.tooManyRequests());

    assertThatThrownBy(() -> client.sendRawTransaction(new byte[] {1, 2, 3}))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            terminated ->
                assertThat(terminated.getDispatch())
                    .isEqualTo(RateLimitTerminatedException.Dispatch.REJECTED));

    assertThat(stub.methods()).containsExactly("eth_sendRawTransaction");
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(1);
    assertThat(context.diagnostics().getRetries()).isZero();
  }

  @Test
  void redirectsAreNotFollowedBecauseTheyWouldBypassAdmission() {
    stub.respondWith(
        (arrival, method) -> Reply.status(307, Map.of("Location", stub.url() + "/elsewhere")));

    assertThatThrownBy(() -> client.chainId())
        .isInstanceOfSatisfying(
            ClientConnectionException.class,
            failure -> assertThat(failure.getMessage()).contains("307"));

    assertThat(stub.arrivals()).isEqualTo(1);
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(1);
    assertThat(context.diagnostics().getRatePressureEvents()).isZero();
  }

  @Test
  void serverErrorIsNotAdmittedAgainByTheLimiter() {
    stub.respondWith((arrival, method) -> Reply.status(503, Map.of()));

    assertThatThrownBy(() -> client.chainId()).isInstanceOf(ClientConnectionException.class);

    assertThat(stub.arrivals()).isEqualTo(1);
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(1);
  }

  @Test
  void aServiceUnavailableWithRetryAfterZeroIsNotFollowedUpByOkHttp() {
    stub.respondWith((arrival, method) -> Reply.status(503, Map.of("Retry-After", "0")));

    assertThatThrownBy(() -> client.chainId())
        .isInstanceOfSatisfying(
            ClientConnectionException.class,
            failure -> assertThat(failure.getMessage()).contains("503"));

    assertThat(stub.arrivals()).isEqualTo(1);
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(1);
  }

  @Test
  void aBroadcastAnsweredServiceUnavailableWithRetryAfterZeroReachesTheWireOnce() {
    stub.respondWith((arrival, method) -> Reply.status(503, Map.of("Retry-After", "0")));

    assertThatThrownBy(() -> client.sendRawTransaction(new byte[] {1, 2, 3}))
        .isInstanceOf(ClientConnectionException.class);

    assertThat(stub.methods()).containsExactly("eth_sendRawTransaction");
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(1);
  }

  @Test
  void aConnectionDroppedAfterTheRequestWasSentIsNotRetriedOnTheWire() throws Exception {
    AtomicInteger accepted = new AtomicInteger();
    try (ServerSocket dropper = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      Thread acceptor =
          new Thread(
              () -> {
                while (!dropper.isClosed()) {
                  try (Socket socket = dropper.accept()) {
                    accepted.incrementAndGet();
                    socket.getInputStream().read(new byte[4096]);
                  } catch (IOException closed) {
                    return;
                  }
                }
              });
      acceptor.setDaemon(true);
      acceptor.start();
      client.close();
      client =
          UniswapNodeClient.create(
              "http://127.0.0.1:" + dropper.getLocalPort(), 2_000, 2_000, policy, context, null);

      assertThatThrownBy(() -> client.sendRawTransaction(new byte[] {1}))
          .isInstanceOf(IOException.class);

      dropper.close();
      acceptor.join();
    }
    assertThat(accepted.get()).isEqualTo(1);
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(1);
    assertThat(context.diagnostics().getRetries()).isZero();
  }

  @Test
  void unclassifiedMethodsFailBeforeAnythingIsSent() throws Exception {
    MeteredHttpService service =
        new MeteredHttpService(stub.url(), new okhttp3.OkHttpClient(), policy, context, null);
    try {
      assertThatThrownBy(
              () -> new Request<>("eth_gasPrice", List.of(), service, EthGasPrice.class).send())
          .isInstanceOfSatisfying(
              RateLimitTerminatedException.class,
              terminated -> {
                assertThat(terminated.getReason())
                    .isEqualTo(RateLimitTerminatedException.Reason.UNCLASSIFIED_OPERATION);
                assertThat(terminated.getDispatch())
                    .isEqualTo(RateLimitTerminatedException.Dispatch.NOT_SENT);
              });
      assertThat(stub.arrivals()).isZero();
    } finally {
      service.close();
    }
  }

  @Test
  void anUnmeteredClientSendsWithoutAdmission() throws Exception {
    try (UniswapNodeClient unmetered =
        UniswapNodeClient.createUnmetered(stub.url(), 2_000, 2_000)) {
      assertThat(unmetered.chainId()).isEqualTo(BigInteger.ONE);
    }
    assertThat(stub.methods()).containsExactly("eth_chainId");
    assertThat(context.diagnostics().getAdmissions()).isZero();
  }

  private interface Callback {
    void run(UniswapNodeClient client) throws Exception;
  }

  /** One invocation per public instance method of the client, keyed by method name. */
  private static Map<String, Callback> clientCalls() {
    Map<String, Callback> calls = new LinkedHashMap<>();
    calls.put("chainId", UniswapNodeClient::chainId);
    calls.put("blockNumber", UniswapNodeClient::blockNumber);
    calls.put("codeAt", c -> c.codeAt(ADDRESS, BLOCK));
    calls.put("call", c -> c.call(ADDRESS, ADDRESS, new byte[] {1}, BLOCK));
    calls.put("nativeBalance", c -> c.nativeBalance(ADDRESS, BLOCK));
    calls.put("tokenBalance", c -> c.tokenBalance(ADDRESS, ADDRESS, BLOCK));
    calls.put("pendingTransactionCount", c -> c.pendingTransactionCount(ADDRESS));
    calls.put("sendRawTransaction", c -> c.sendRawTransaction(new byte[] {1}));
    calls.put("transactionByHash", c -> c.transactionByHash("0x" + "ab".repeat(32)));
    calls.put("transactionReceipt", c -> c.transactionReceipt("0x" + "ab".repeat(32)));
    calls.put("baseFeePerGas", UniswapNodeClient::baseFeePerGas);
    calls.put("priorityFeePerGas", UniswapNodeClient::priorityFeePerGas);
    calls.put("estimateGas", c -> c.estimateGas(ADDRESS, ADDRESS, new byte[] {1}));
    return calls;
  }

  private static TreeSet<String> publicClientMethodNames() {
    TreeSet<String> names = new TreeSet<>();
    for (Method method : UniswapNodeClient.class.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers())
          && !Modifier.isStatic(method.getModifiers())
          && !"close".equals(method.getName())) {
        names.add(method.getName());
      }
    }
    return names;
  }
}
