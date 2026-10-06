package org.knowm.xchange.coinbasederivatives.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.knowm.xchange.client.ratelimit.RateLimitAttemptObserver;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.knowm.xchange.coinbasederivatives.auth.AccessToken;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesAccessTokenProvider;

/**
 * Strict Coinbase-namespaced JSON-RPC 2.0 HTTP transport.
 *
 * <p>Every wire attempt (public calls, private calls, retries and token acquisition) is admitted
 * by the exchange's {@link RateLimitContext} before anything is minted or sent, keyed by the
 * JSON-RPC method because all methods share one HTTP endpoint. The JWT of a {@code public/auth}
 * exchange and the bearer token of a private call are produced inside the admitted attempt, so
 * every replay after a confirmed rate rejection carries fresh values.
 */
public final class CoinbaseDerivativesJsonRpcTransport {
  private static final int TRANSPORT_ERROR = -1;
  private static final int PUBLIC_ATTEMPTS = 3;
  private static final String AUTH_METHOD = "public/auth";
  private static final RateLimitAttemptObserver UNMETERED_OBSERVER =
      new RateLimitAttemptObserver() {
        @Override
        public void observeHttpResponse(int status, Function<String, String> headerLookup) {}

        @Override
        public void observeFeedback(RateLimitFeedback feedback) {}

        @Override
        public void markUncertain() {}
      };

  /** One wire attempt, run after admission. */
  @FunctionalInterface
  private interface Wire<T> {
    RpcResult<T> run(RateLimitAttemptObserver observer) throws IOException;
  }

  /** Failure to obtain the bearer token; it happens before dispatch so it is never ambiguous. */
  private static final class TokenAcquisitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private TokenAcquisitionException(IOException cause) {
      super(cause);
    }

    private IOException ioCause() {
      return (IOException) getCause();
    }
  }

  private final URI endpoint;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final RateLimitPolicy rateLimitPolicy;
  private final RateLimitContext rateLimitContext;
  private final String rateLimitUserScope;
  private final AtomicLong requestIds = new AtomicLong();
  private volatile CoinbaseDerivativesAccessTokenProvider accessTokenProvider;
  private volatile RateCreditMetadata rateCreditMetadata = new RateCreditMetadata(Map.of());

  /**
   * Creates a transport governed by the default policy and a context owned by this transport.
   *
   * @param endpoint the JSON-RPC HTTP endpoint
   */
  public CoinbaseDerivativesJsonRpcTransport(URI endpoint) {
    this(endpoint, CoinbaseDerivativesRateLimitPolicy.create(), new RateLimitContext(), null);
  }

  /**
   * Creates a transport whose every wire attempt is admitted by the given context.
   *
   * @param endpoint the JSON-RPC HTTP endpoint
   * @param rateLimitPolicy the policy classifying JSON-RPC methods
   * @param rateLimitContext the context holding the budgets; may be shared with other exchanges
   * @param rateLimitUserScope opaque user-allocation binding, {@code null} for the conservative
   *     shared binding
   * @since 1.0.3
   */
  public CoinbaseDerivativesJsonRpcTransport(
      URI endpoint,
      RateLimitPolicy rateLimitPolicy,
      RateLimitContext rateLimitContext,
      String rateLimitUserScope) {
    this(
        endpoint,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        defaultObjectMapper(),
        Objects.requireNonNull(rateLimitPolicy, "rateLimitPolicy"),
        Objects.requireNonNull(rateLimitContext, "rateLimitContext"),
        rateLimitUserScope);
  }

  CoinbaseDerivativesJsonRpcTransport(
      URI endpoint, HttpClient httpClient, ObjectMapper objectMapper) {
    this(
        endpoint,
        httpClient,
        objectMapper,
        CoinbaseDerivativesRateLimitPolicy.create(),
        new RateLimitContext(),
        null);
  }

  CoinbaseDerivativesJsonRpcTransport(
      URI endpoint,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      RateLimitPolicy rateLimitPolicy,
      RateLimitContext rateLimitContext,
      String rateLimitUserScope) {
    this.endpoint = Objects.requireNonNull(endpoint);
    this.httpClient = Objects.requireNonNull(httpClient);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.rateLimitPolicy = rateLimitPolicy;
    this.rateLimitContext = rateLimitContext;
    this.rateLimitUserScope = rateLimitUserScope;
  }

  /**
   * Creates a transport that is not rate limited, for exchanges whose specification explicitly
   * disables the rate limiter.
   *
   * @param endpoint the JSON-RPC HTTP endpoint
   * @return the unmetered transport
   * @since 1.0.3
   */
  public static CoinbaseDerivativesJsonRpcTransport unmetered(URI endpoint) {
    return new CoinbaseDerivativesJsonRpcTransport(
        endpoint,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        defaultObjectMapper(),
        null,
        null,
        null);
  }

  public static ObjectMapper defaultObjectMapper() {
    return new ObjectMapper()
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public void setAccessTokenProvider(CoinbaseDerivativesAccessTokenProvider provider) {
    if (accessTokenProvider != null) {
      throw new IllegalStateException("Access-token provider is already configured");
    }
    accessTokenProvider = Objects.requireNonNull(provider);
  }

  public RateCreditMetadata getRateCreditMetadata() {
    return rateCreditMetadata;
  }

  public <T> T callPublic(String method, Object params, Class<T> resultType) throws IOException {
    return callPublicWithId(method, params, resultType).value();
  }

  /** Performs one admitted public request attempt without transient-failure retry. */
  public <T> T callPublicOnce(String method, Object params, Class<T> resultType)
      throws IOException {
    return publicAttempt(method, () -> params, resultType).value();
  }

  /**
   * Exchanges a freshly minted CDP JWT for an access token. The JWT is requested from the supplier
   * only once the {@code public/auth} attempt is admitted, and again for every replay, so a JWT is
   * never reused across authentication exchanges.
   *
   * @param freshJwt supplies a new JWT per call
   * @return the access token
   * @throws IOException on transport failure
   * @since 1.0.3
   */
  public AccessToken authenticate(Supplier<String> freshJwt) throws IOException {
    Objects.requireNonNull(freshJwt);
    return publicAttempt(
            AUTH_METHOD,
            () -> Map.of("grant_type", "coinbase_cdp", "token", freshJwt.get()),
            AccessToken.class)
        .value();
  }

  public <T> RpcResult<T> callPublicWithId(String method, Object params, Class<T> resultType)
      throws IOException {
    for (int attempt = 1; attempt <= PUBLIC_ATTEMPTS; attempt++) {
      try {
        return publicAttempt(method, () -> params, resultType);
      } catch (IOException failure) {
        if (attempt == PUBLIC_ATTEMPTS) {
          throw failure;
        }
        backoff(attempt);
      }
    }
    throw new IllegalStateException("Unreachable public retry state");
  }

  public <T> T callPrivate(
      String method, Object params, Class<T> resultType, ReplaySafety replaySafety)
      throws IOException {
    return callPrivateWithId(method, params, resultType, replaySafety).value();
  }

  public <T> RpcResult<T> callPrivateWithId(
      String method, Object params, Class<T> resultType, ReplaySafety replaySafety)
      throws IOException {
    CoinbaseDerivativesAccessTokenProvider provider = accessTokenProvider;
    if (provider == null) {
      throw new IllegalStateException("Private transport is not configured for authentication");
    }
    boolean placement = replaySafety == ReplaySafety.PLACEMENT;
    AtomicReference<String> token = new AtomicReference<>();
    try {
      return privateAttempt(method, params, resultType, placement, provider, token);
    } catch (CoinbaseDerivativesException failure) {
      if (failure.getRetryClassification() == RetryClassification.AUTHENTICATION && !placement) {
        provider.invalidate(token.get());
        return privateAttempt(method, params, resultType, false, provider, new AtomicReference<>());
      }
      throw failure;
    } catch (IOException failure) {
      if (placement) {
        throw failure;
      }
      backoff(1);
      return privateAttempt(method, params, resultType, false, provider, new AtomicReference<>());
    } catch (TokenAcquisitionException failure) {
      throw failure.ioCause();
    }
  }

  private <T> RpcResult<T> publicAttempt(
      String method, Supplier<Object> params, Class<T> resultType) throws IOException {
    return admitted(
        method,
        false,
        observer -> execute(method, params, resultType, null, observer, false));
  }

  /**
   * One admitted private attempt. The bearer token is obtained inside the attempt (its own refresh
   * is a separately admitted {@code public/auth} operation), so nothing is minted for a request
   * that admission rejects.
   */
  private <T> RpcResult<T> privateAttempt(
      String method,
      Object params,
      Class<T> resultType,
      boolean placement,
      CoinbaseDerivativesAccessTokenProvider provider,
      AtomicReference<String> usedToken)
      throws IOException {
    return admitted(
        method,
        true,
        observer -> {
          String token;
          try {
            token = provider.getToken();
          } catch (IOException failure) {
            throw new TokenAcquisitionException(failure);
          }
          usedToken.set(token);
          return execute(method, () -> params, resultType, token, observer, placement);
        });
  }

  private <T> RpcResult<T> admitted(String method, boolean authenticated, Wire<T> wire)
      throws IOException {
    if (rateLimitContext == null) {
      return wire.run(UNMETERED_OBSERVER);
    }
    try {
      return rateLimitContext.execute(
          rateLimitPolicy,
          new RateLimitRequest(method, authenticated),
          rateLimitUserScope,
          wire::run);
    } catch (IOException | RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("Unexpected checked rate-limit failure", failure);
    }
  }

  private <T> RpcResult<T> execute(
      String method,
      Supplier<Object> params,
      Class<T> resultType,
      String bearerToken,
      RateLimitAttemptObserver observer,
      boolean ambiguousOnTransportFailure)
      throws IOException {
    long id = requestIds.incrementAndGet();
    String requestBody =
        objectMapper.writeValueAsString(new JsonRpcRequest(id, method, params.get()));
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody));
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }

    HttpResponse<String> response;
    try {
      response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      observer.markUncertain();
      IOException interrupted =
          new IOException("Interrupted calling Coinbase derivatives gateway", e);
      if (ambiguousOnTransportFailure) {
        throw ambiguousPlacement(method, interrupted);
      }
      throw interrupted;
    } catch (IOException e) {
      observer.markUncertain();
      if (ambiguousOnTransportFailure) {
        throw ambiguousPlacement(method, e);
      }
      throw e;
    }
    observer.observeHttpResponse(
        response.statusCode(), name -> response.headers().firstValue(name).orElse(null));
    captureCreditMetadata(response, null);
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new CoinbaseDerivativesException(
          response.statusCode(),
          "Coinbase derivatives authentication was rejected",
          id,
          method,
          RetryClassification.AUTHENTICATION,
          CoinbaseDerivativesRedactor.sanitize(response.body()));
    }
    if (response.statusCode() == 429) {
      throw new CoinbaseDerivativesException(
          response.statusCode(),
          "Coinbase derivatives request was rate limited",
          id,
          method,
          RetryClassification.RATE_CREDIT,
          CoinbaseDerivativesRedactor.sanitize(response.body()));
    }
    if (response.statusCode() >= 500) {
      observer.markUncertain();
      IOException transientFailure =
          new IOException("Coinbase derivatives gateway transient HTTP " + response.statusCode());
      if (ambiguousOnTransportFailure) {
        throw ambiguousPlacement(method, transientFailure);
      }
      throw transientFailure;
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new CoinbaseDerivativesException(
          response.statusCode(),
          "Coinbase derivatives gateway rejected the request",
          id,
          method,
          RetryClassification.PERMANENT,
          CoinbaseDerivativesRedactor.sanitize(response.body()));
    }

    JsonRpcResponse envelope;
    try {
      envelope = objectMapper.readValue(response.body(), JsonRpcResponse.class);
    } catch (JsonProcessingException e) {
      throw protocolFailure(id, method, "Malformed JSON-RPC response", e.getOriginalMessage(), e);
    }
    captureCreditMetadata(response, envelope.result());
    validateEnvelope(id, method, envelope);
    if (envelope.error() != null) {
      if (envelope.error().code() == CoinbaseDerivativesRateLimitPolicy.TOO_MANY_REQUESTS_CODE) {
        observer.observeFeedback(RateLimitFeedback.rejected(null));
      }
      throw adaptError(id, method, envelope.error());
    }
    try {
      T result = objectMapper.treeToValue(envelope.result(), resultType);
      return new RpcResult<>(id, result);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw protocolFailure(id, method, "Type-incompatible JSON-RPC result", e.getMessage(), e);
    }
  }

  /** A placement that failed after dispatch may have been accepted: ambiguous, never retried. */
  private CoinbaseDerivativesException ambiguousPlacement(String method, IOException failure) {
    return new CoinbaseDerivativesException(
        TRANSPORT_ERROR,
        "Coinbase derivatives placement outcome is ambiguous",
        null,
        method,
        RetryClassification.AMBIGUOUS,
        CoinbaseDerivativesRedactor.sanitize(failure.getMessage()),
        failure);
  }

  private void validateEnvelope(long id, String method, JsonRpcResponse envelope) {
    if (!"2.0".equals(envelope.jsonrpc())) {
      throw protocolFailure(id, method, "Missing or invalid JSON-RPC version", null);
    }
    if (envelope.id() == null || envelope.id() != id) {
      throw protocolFailure(id, method, "Mismatched JSON-RPC response ID", null);
    }
    if ((envelope.result() == null) == (envelope.error() == null)) {
      throw protocolFailure(
          id, method, "JSON-RPC response must contain exactly one result or error", null);
    }
    if (envelope.error() != null && envelope.error().message() == null) {
      throw protocolFailure(id, method, "Malformed JSON-RPC error", null);
    }
  }

  private CoinbaseDerivativesException adaptError(long id, String method, JsonRpcError error) {
    String normalized = error.message().toLowerCase(Locale.ROOT);
    RetryClassification classification;
    if (normalized.contains("auth")
        || normalized.contains("token")
        || normalized.contains("credential")) {
      classification = RetryClassification.AUTHENTICATION;
    } else if (error.code() == CoinbaseDerivativesRateLimitPolicy.TOO_MANY_REQUESTS_CODE
        || normalized.contains("credit")
        || normalized.contains("rate limit")
        || normalized.contains("too_many_requests")) {
      classification = RetryClassification.RATE_CREDIT;
    } else if (normalized.contains("temporar") || normalized.contains("unavailable")) {
      classification = RetryClassification.TRANSIENT;
    } else {
      classification = RetryClassification.PERMANENT;
    }
    return new CoinbaseDerivativesException(
        error.code(),
        CoinbaseDerivativesRedactor.sanitize(error.message()),
        id,
        method,
        classification,
        CoinbaseDerivativesRedactor.sanitize(
            error.data() == null ? null : error.data().toString()));
  }

  private CoinbaseDerivativesException protocolFailure(
      long id, String method, String message, String details) {
    return new CoinbaseDerivativesException(
        TRANSPORT_ERROR,
        message,
        id,
        method,
        RetryClassification.PERMANENT,
        CoinbaseDerivativesRedactor.sanitize(details));
  }

  private CoinbaseDerivativesException protocolFailure(
      long id, String method, String message, String details, Throwable cause) {
    return new CoinbaseDerivativesException(
        TRANSPORT_ERROR,
        message,
        id,
        method,
        RetryClassification.PERMANENT,
        CoinbaseDerivativesRedactor.sanitize(details),
        cause);
  }

  private void captureCreditMetadata(HttpResponse<String> response, JsonNode result) {
    Map<String, String> observed = new ConcurrentHashMap<>();
    response
        .headers()
        .map()
        .forEach(
            (name, values) -> {
              if (name.toLowerCase(Locale.ROOT).contains("credit")) {
                observed.put("header." + name, String.join(",", values));
              }
            });
    collectCreditFields(result, "result", observed);
    if (!observed.isEmpty()) {
      rateCreditMetadata = new RateCreditMetadata(observed);
    }
  }

  private void collectCreditFields(JsonNode node, String path, Map<String, String> observed) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              entry -> {
                String childPath = path + "." + entry.getKey();
                if (entry.getKey().toLowerCase(Locale.ROOT).contains("credit")
                    && entry.getValue().isValueNode()) {
                  observed.put(childPath, entry.getValue().asText());
                }
                collectCreditFields(entry.getValue(), childPath, observed);
              });
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        collectCreditFields(node.get(index), path + "[" + index + "]", observed);
      }
    }
  }

  private static void backoff(int attempt) throws IOException {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(25L, 76L) * attempt);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted during Coinbase derivatives retry backoff", e);
    }
  }
}
