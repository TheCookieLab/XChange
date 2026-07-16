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
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesAccessTokenProvider;

/** Strict Coinbase-namespaced JSON-RPC 2.0 HTTP transport. */
public final class CoinbaseDerivativesJsonRpcTransport {
  private static final int TRANSPORT_ERROR = -1;
  private static final int PUBLIC_ATTEMPTS = 3;

  private final URI endpoint;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final AtomicLong requestIds = new AtomicLong();
  private volatile CoinbaseDerivativesAccessTokenProvider accessTokenProvider;
  private volatile RateCreditMetadata rateCreditMetadata = new RateCreditMetadata(Map.of());

  public CoinbaseDerivativesJsonRpcTransport(URI endpoint) {
    this(
        endpoint,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        defaultObjectMapper());
  }

  CoinbaseDerivativesJsonRpcTransport(
      URI endpoint, HttpClient httpClient, ObjectMapper objectMapper) {
    this.endpoint = Objects.requireNonNull(endpoint);
    this.httpClient = Objects.requireNonNull(httpClient);
    this.objectMapper = Objects.requireNonNull(objectMapper);
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

  /** Performs one public request attempt. Authentication uses this so a JWT is never replayed. */
  public <T> T callPublicOnce(String method, Object params, Class<T> resultType)
      throws IOException {
    return execute(method, params, resultType, null).value();
  }

  public <T> RpcResult<T> callPublicWithId(String method, Object params, Class<T> resultType)
      throws IOException {
    for (int attempt = 1; attempt <= PUBLIC_ATTEMPTS; attempt++) {
      try {
        return execute(method, params, resultType, null);
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
    String token = provider.getToken();
    try {
      return execute(method, params, resultType, token);
    } catch (CoinbaseDerivativesException failure) {
      if (failure.getRetryClassification() == RetryClassification.AUTHENTICATION
          && replaySafety != ReplaySafety.PLACEMENT) {
        provider.invalidate(token);
        return execute(method, params, resultType, provider.getToken());
      }
      throw failure;
    } catch (IOException failure) {
      if (replaySafety == ReplaySafety.PLACEMENT) {
        throw new CoinbaseDerivativesException(
            TRANSPORT_ERROR,
            "Coinbase derivatives placement outcome is ambiguous",
            null,
            method,
            RetryClassification.AMBIGUOUS,
            CoinbaseDerivativesRedactor.sanitize(failure.getMessage()),
            failure);
      }
      backoff(1);
      return execute(method, params, resultType, token);
    }
  }

  private <T> RpcResult<T> execute(
      String method, Object params, Class<T> resultType, String bearerToken) throws IOException {
    long id = requestIds.incrementAndGet();
    String requestBody = objectMapper.writeValueAsString(new JsonRpcRequest(id, method, params));
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
      throw new IOException("Interrupted calling Coinbase derivatives gateway", e);
    }
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
      throw new IOException("Coinbase derivatives gateway transient HTTP " + response.statusCode());
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
      throw adaptError(id, method, envelope.error());
    }
    try {
      T result = objectMapper.treeToValue(envelope.result(), resultType);
      return new RpcResult<>(id, result);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw protocolFailure(id, method, "Type-incompatible JSON-RPC result", e.getMessage(), e);
    }
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
    } else if (normalized.contains("credit") || normalized.contains("rate limit")) {
      classification = RetryClassification.RATE_CREDIT;
    } else if (normalized.contains("temporar") || normalized.contains("unavailable")) {
      classification = RetryClassification.TRANSIENT;
    } else {
      classification = RetryClassification.PERMANENT;
    }
    return new CoinbaseDerivativesException(
        error.code(),
        error.message(),
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
