package org.knowm.xchange.mexc.v3.config;

import java.net.URI;
import java.net.URISyntaxException;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Typed configuration for the MEXC Spot v3 integration.
 *
 * <p>All knobs are optional and read from exchange-specific parameters on the {@link
 * ExchangeSpecification} so the defaults match the official MEXC production surface:
 *
 * <ul>
 *   <li>{@value #REST_BASE_URL_KEY} — REST base URL override (default {@value #REST_BASE_URL}).
 *   <li>{@value #STREAM_BASE_URL_KEY} — WebSocket base URL override used by the streaming module
 *       (default {@value #STREAM_BASE_URL}).
 *   <li>{@value #RECV_WINDOW_KEY} — signed-request {@code recvWindow} in milliseconds (default
 *       {@value #DEFAULT_RECV_WINDOW_MS}, validated against {@value #MAX_RECV_WINDOW_MS}).
 * </ul>
 */
public final class MexcV3Configuration {

  /** Exchange-specific parameter key overriding the REST base URL. */
  public static final String REST_BASE_URL_KEY = "MexcV3_RestBaseUrl";

  /** Exchange-specific parameter key overriding the WebSocket base URL. */
  public static final String STREAM_BASE_URL_KEY = "MexcV3_StreamBaseUrl";

  /** Exchange-specific parameter key overriding the signed-request {@code recvWindow}. */
  public static final String RECV_WINDOW_KEY = "MexcV3_RecvWindow";

  /** Official MEXC Spot v3 REST base URL. */
  public static final String REST_BASE_URL = "https://api.mexc.com";

  /** Official MEXC Spot v3 WebSocket base URL (market and user-data streams). */
  public static final String STREAM_BASE_URL = "wss://wbs-api.mexc.com/ws";

  /** Default {@code recvWindow} in milliseconds. */
  public static final long DEFAULT_RECV_WINDOW_MS = 5000;

  /** Maximum accepted {@code recvWindow} in milliseconds (provider constraint). */
  public static final long MAX_RECV_WINDOW_MS = 60_000;

  private final String restBaseUrl;
  private final String streamBaseUrl;
  private final long recvWindowMs;

  private MexcV3Configuration(String restBaseUrl, String streamBaseUrl, long recvWindowMs) {
    this.restBaseUrl = restBaseUrl;
    this.streamBaseUrl = streamBaseUrl;
    this.recvWindowMs = recvWindowMs;
  }

  /**
   * Builds the configuration from an exchange specification, applying validation to every
   * overridable value so misconfiguration fails at exchange construction time rather than at the
   * first request.
   *
   * @throws IllegalArgumentException when an override is malformed or out of range.
   */
  public static MexcV3Configuration from(ExchangeSpecification specification) {
    String restBaseUrl = stringParameter(specification, REST_BASE_URL_KEY, REST_BASE_URL);
    String streamBaseUrl = stringParameter(specification, STREAM_BASE_URL_KEY, STREAM_BASE_URL);
    validateHttpUrl(restBaseUrl, REST_BASE_URL_KEY);
    validateWsUrl(streamBaseUrl, STREAM_BASE_URL_KEY);

    long recvWindowMs = DEFAULT_RECV_WINDOW_MS;
    Object recvWindowValue = specification.getExchangeSpecificParametersItem(RECV_WINDOW_KEY);
    if (recvWindowValue != null) {
      try {
        recvWindowMs = Long.parseLong(String.valueOf(recvWindowValue));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Invalid " + RECV_WINDOW_KEY + ": '" + recvWindowValue + "' is not a number.", e);
      }
      if (recvWindowMs <= 0 || recvWindowMs > MAX_RECV_WINDOW_MS) {
        throw new IllegalArgumentException(
            "Invalid " + RECV_WINDOW_KEY + ": " + recvWindowMs
                + " must be in (0, " + MAX_RECV_WINDOW_MS + "] milliseconds.");
      }
    }
    return new MexcV3Configuration(restBaseUrl, streamBaseUrl, recvWindowMs);
  }

  private static String stringParameter(
      ExchangeSpecification specification, String key, String defaultValue) {
    Object value = specification.getExchangeSpecificParametersItem(key);
    return value == null ? defaultValue : String.valueOf(value);
  }

  private static void validateHttpUrl(String url, String key) {
    try {
      URI uri = new URI(url);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new IllegalArgumentException();
      }
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid " + key + ": '" + url + "' is not an https URL.",
          e instanceof URISyntaxException ? e : null);
    }
  }

  private static void validateWsUrl(String url, String key) {
    try {
      URI uri = new URI(url);
      if (!"wss".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new IllegalArgumentException();
      }
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid " + key + ": '" + url + "' is not a wss URL.",
          e instanceof URISyntaxException ? e : null);
    }
  }

  /** The configured REST base URL, without a trailing slash. */
  public String getRestBaseUrl() {
    return restBaseUrl;
  }

  /** The configured WebSocket base URL (streaming module). */
  public String getStreamBaseUrl() {
    return streamBaseUrl;
  }

  /** The configured signed-request {@code recvWindow} in milliseconds. */
  public long getRecvWindowMs() {
    return recvWindowMs;
  }
}
