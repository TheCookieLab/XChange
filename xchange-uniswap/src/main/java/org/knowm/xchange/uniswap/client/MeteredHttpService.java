package org.knowm.xchange.uniswap.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import org.knowm.xchange.client.ratelimit.RateLimitAttemptObserver;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.web3j.protocol.Service;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

/**
 * web3j JSON-RPC transport that owns exactly one rate-admission per wire attempt.
 *
 * <p>web3j's own {@code HttpService} discards the HTTP status of a rejected call, so the rate
 * limiter could not see a {@code 429}. This transport performs the OkHttp call itself inside the
 * {@link RateLimitContext#execute admitted attempt}, reports the status and headers to the attempt
 * observer and keeps web3j's {@link ClientConnectionException} for every non-2xx answer. Redirects
 * and silent connection-failure retries are disabled on the OkHttp client by the caller, and every
 * request body is one-shot so that OkHttp never resends the POST on its own (an {@code HTTP 503}
 * with {@code Retry-After: 0} follow-up, connection recovery): any of them would put a second
 * request on the wire without a second admission.
 *
 * <p>Only single requests are supported; batches are never produced by {@link UniswapNodeClient}
 * and would carry several methods under one admission.
 */
final class MeteredHttpService extends Service {

  private static final RateLimitAttemptObserver UNMETERED_OBSERVER =
      new RateLimitAttemptObserver() {
        @Override
        public void observeHttpResponse(int status, Function<String, String> headerLookup) {}

        @Override
        public void observeFeedback(RateLimitFeedback feedback) {}

        @Override
        public void markUncertain() {}
      };

  private final String url;
  private final OkHttpClient httpClient;
  private final RateLimitPolicy policy;
  private final RateLimitContext context;
  private final String userScope;

  /**
   * @param url the node endpoint
   * @param httpClient the OkHttp client; must not follow redirects or retry on connection failure
   * @param policy the rate-limit policy, or {@code null} together with a {@code null} context for
   *     an unmetered transport
   * @param context the rate-limit context, or {@code null} for an unmetered transport
   * @param userScope the opaque user-scope binding, or {@code null} for the shared default
   */
  MeteredHttpService(
      String url,
      OkHttpClient httpClient,
      RateLimitPolicy policy,
      RateLimitContext context,
      String userScope) {
    super(false);
    this.url = url;
    this.httpClient = httpClient;
    this.policy = policy;
    this.context = context;
    this.userScope = userScope;
  }

  @Override
  public <T extends Response> T send(Request request, Class<T> responseType) throws IOException {
    String payload = objectMapper.writeValueAsString(request);
    if (context == null) {
      return attempt(payload, responseType, UNMETERED_OBSERVER);
    }
    try {
      return context.execute(
          policy,
          new RateLimitRequest(request.getMethod(), false),
          userScope,
          observer -> attempt(payload, responseType, observer));
    } catch (IOException | RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("Unexpected checked rate-limit failure", failure);
    }
  }

  private <T extends Response> T attempt(
      String payload, Class<T> responseType, RateLimitAttemptObserver observer) throws IOException {
    okhttp3.Request httpRequest = new Builder().url(url).post(new OneShotJsonBody(payload)).build();
    try (okhttp3.Response httpResponse = httpClient.newCall(httpRequest).execute()) {
      observer.observeHttpResponse(httpResponse.code(), httpResponse::header);
      ResponseBody body = httpResponse.body();
      if (!httpResponse.isSuccessful()) {
        throw new ClientConnectionException(
            httpResponse.code(),
            "Invalid response received: "
                + httpResponse.code()
                + "; "
                + (body == null ? "N/A" : body.string()));
      }
      if (body == null) {
        return null;
      }
      T response = objectMapper.readValue(body.bytes(), responseType);
      if (response != null
          && response.hasError()
          && response.getError().getCode() == UniswapRateLimitPolicy.LIMIT_EXCEEDED_CODE) {
        observer.observeFeedback(RateLimitFeedback.rejected(null));
      }
      return response;
    } catch (IOException e) {
      observer.markUncertain();
      throw e;
    }
  }

  /**
   * JSON body that OkHttp may write exactly once. A one-shot body makes OkHttp's follow-up logic
   * hand back an {@code HTTP 503 + Retry-After: 0} (or any other follow-up) instead of resending
   * the POST, and keeps it from recovering a failed call by resending: each admission is one wire
   * send.
   */
  private static final class OneShotJsonBody extends RequestBody {
    private final byte[] content;

    private OneShotJsonBody(String payload) {
      this.content = payload.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public MediaType contentType() {
      return HttpService.JSON_MEDIA_TYPE;
    }

    @Override
    public long contentLength() {
      return content.length;
    }

    @Override
    public boolean isOneShot() {
      return true;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
      sink.write(content);
    }
  }

  @Override
  public BatchResponse sendBatch(BatchRequest batchRequest) {
    throw new UnsupportedOperationException(
        "JSON-RPC batches are not rate limited and not supported");
  }

  @Override
  protected InputStream performIO(String payload) {
    throw new UnsupportedOperationException("requests must go through send(Request, Class)");
  }

  @Override
  public void close() {
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
  }
}
