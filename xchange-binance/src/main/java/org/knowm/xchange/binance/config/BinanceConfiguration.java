package org.knowm.xchange.binance.config;

import java.util.Objects;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Typed, immutable configuration for the Binance integration, derived from an {@link
 * ExchangeSpecification}.
 *
 * <p>This replaces new uses of magic strings and loosely typed exchange-specific parameters with
 * documented constants and typed accessors. Unknown or invalid combinations fail during {@link
 * #from(ExchangeSpecification)} with an actionable message, before any network call.
 *
 * <p>Legacy parameters ({@code Exchange_Type}, {@code ed25519}, {@code recvWindow}) remain honored
 * for source compatibility during the documented grace period; typed parameters take precedence
 * when both are present.
 */
public final class BinanceConfiguration {

  /** Typed product-family parameter (a {@link BinanceProductFamily} value). */
  public static final String PRODUCT_FAMILY = "Binance_ProductFamily";

  /** Typed key-algorithm parameter (a {@link BinanceKeyAlgorithm} value). */
  public static final String KEY_ALGORITHM = "Binance_KeyAlgorithm";

  /** Typed timestamp-unit parameter (a {@link BinanceTimestampUnit} value). */
  public static final String TIMESTAMP_UNIT = "Binance_TimestampUnit";

  /** Typed receive-window parameter (a {@link Long} in {@code [0, 60000]} milliseconds). */
  public static final String RECV_WINDOW = "Binance_RecvWindow";

  /** REST base-URL override (a {@link String}); defaults to the product family base URL. */
  public static final String REST_BASE_URL = "Binance_RestBaseUrl";

  /** WebSocket base-URL override (a {@link String}); defaults to the product family stream URL. */
  public static final String STREAM_BASE_URL = "Binance_StreamBaseUrl";

  /** Order-book depth for streaming order-book recovery (a positive {@link Integer}). */
  public static final String ORDER_BOOK_DEPTH = "Binance_OrderBookDepth";

  /** Order-book update cadence in milliseconds (a positive {@link Integer}). */
  public static final String ORDER_BOOK_UPDATE_CADENCE_MS = "Binance_OrderBookUpdateCadenceMs";

  /** Maximum receive window accepted by Binance. */
  public static final long MAX_RECV_WINDOW_MS = 60_000L;

  private static final int DEFAULT_ORDER_BOOK_DEPTH = 1000;
  private static final int DEFAULT_ORDER_BOOK_UPDATE_CADENCE_MS = 100;

  private final BinanceProductFamily productFamily;
  private final BinanceKeyAlgorithm keyAlgorithm;
  private final BinanceTimestampUnit timestampUnit;
  private final boolean sandboxEnabled;
  private final Long recvWindow;
  private final String restBaseUrl;
  private final String streamBaseUrl;
  private final int orderBookDepth;
  private final int orderBookUpdateCadenceMs;

  private BinanceConfiguration(Builder builder) {
    this.productFamily = builder.productFamily;
    this.keyAlgorithm = builder.keyAlgorithm;
    this.timestampUnit = builder.timestampUnit;
    this.sandboxEnabled = builder.sandboxEnabled;
    this.recvWindow = builder.recvWindow;
    this.restBaseUrl = builder.restBaseUrl;
    this.streamBaseUrl = builder.streamBaseUrl;
    this.orderBookDepth = builder.orderBookDepth;
    this.orderBookUpdateCadenceMs = builder.orderBookUpdateCadenceMs;
  }

  /**
   * Builds a configuration from an exchange specification, validating typed parameters and
   * falling back to the documented legacy parameters when typed ones are absent.
   *
   * @throws IllegalArgumentException when a parameter value is invalid or a combination is not
   *     supported, with an actionable message.
   */
  public static BinanceConfiguration from(ExchangeSpecification specification) {
    Objects.requireNonNull(specification, "specification");
    Builder builder = new Builder();

    builder.productFamily = readProductFamily(specification);
    if (!builder.productFamily.isImplemented()) {
      throw new IllegalArgumentException(
          "Binance product family "
              + builder.productFamily
              + " is not implemented yet. Supported families: SPOT, WALLET_SAPI, MARGIN, USDM, "
              + "COINM, PORTFOLIO_MARGIN.");
    }

    builder.keyAlgorithm =
        typedOrDefault(specification, KEY_ALGORITHM, BinanceKeyAlgorithm.HMAC_SHA_256);

    builder.timestampUnit =
        typedOrDefault(specification, TIMESTAMP_UNIT, BinanceTimestampUnit.MILLISECONDS);

    builder.sandboxEnabled =
        Boolean.TRUE.equals(
            specification.getExchangeSpecificParametersItem(Exchange.USE_SANDBOX));

    builder.recvWindow = readRecvWindow(specification);
    builder.restBaseUrl = stringParameter(specification, REST_BASE_URL);
    builder.streamBaseUrl = stringParameter(specification, STREAM_BASE_URL);
    builder.orderBookDepth = intParameter(specification, ORDER_BOOK_DEPTH, DEFAULT_ORDER_BOOK_DEPTH);
    builder.orderBookUpdateCadenceMs =
        intParameter(
            specification, ORDER_BOOK_UPDATE_CADENCE_MS, DEFAULT_ORDER_BOOK_UPDATE_CADENCE_MS);

    if (builder.orderBookDepth <= 0) {
      throw new IllegalArgumentException(
          "Binance exchange-specific parameter \""
              + ORDER_BOOK_DEPTH
              + "\" must be a positive integer, got "
              + builder.orderBookDepth
              + ".");
    }
    if (builder.orderBookUpdateCadenceMs <= 0) {
      throw new IllegalArgumentException(
          "Binance exchange-specific parameter \""
              + ORDER_BOOK_UPDATE_CADENCE_MS
              + "\" must be a positive integer, got "
              + builder.orderBookUpdateCadenceMs
              + ".");
    }

    return new BinanceConfiguration(builder);
  }

  /** The product family this exchange instance is configured for. */
  public BinanceProductFamily getProductFamily() {
    return productFamily;
  }

  /** The private-key algorithm used to sign requests. */
  public BinanceKeyAlgorithm getKeyAlgorithm() {
    return keyAlgorithm;
  }

  /** The timestamp unit applied to the {@code timestamp} parameter of signed requests. */
  public BinanceTimestampUnit getTimestampUnit() {
    return timestampUnit;
  }

  /** Whether the sandbox/testnet base URL should be used. */
  public boolean isSandboxEnabled() {
    return sandboxEnabled;
  }

  /** Receive window in milliseconds, or {@code null} to let Binance apply its default. */
  public Long getRecvWindow() {
    return recvWindow;
  }

  /** Effective REST base URL after family defaults, sandbox selection, and overrides. */
  public String getRestBaseUrl() {
    if (restBaseUrl != null) {
      return restBaseUrl;
    }
    if (sandboxEnabled && productFamily.getSandboxRestBaseUrl() != null) {
      return productFamily.getSandboxRestBaseUrl();
    }
    return productFamily.getRestBaseUrl();
  }

  /** Configured WebSocket base URL override, if any. */
  public String getStreamBaseUrl() {
    return streamBaseUrl;
  }

  /** Order-book depth used when recovering streaming order books from a REST snapshot. */
  public int getOrderBookDepth() {
    return orderBookDepth;
  }

  /** Order-book update cadence in milliseconds used for streaming order-book recovery. */
  public int getOrderBookUpdateCadenceMs() {
    return orderBookUpdateCadenceMs;
  }

  private static BinanceProductFamily readProductFamily(ExchangeSpecification specification) {
    Object typed = specification.getExchangeSpecificParametersItem(PRODUCT_FAMILY);
    if (typed != null) {
      if (!(typed instanceof BinanceProductFamily)) {
        throw new IllegalArgumentException(
            "Binance exchange-specific parameter \""
                + PRODUCT_FAMILY
                + "\" must be a BinanceProductFamily, got "
                + typed.getClass().getSimpleName()
                + " ("
                + typed
                + ").");
      }
      return (BinanceProductFamily) typed;
    }
    // Legacy "Exchange_Type" parameter honored during the grace period.
    return legacyProductFamily(specification);
  }

  /**
   * Maps the legacy {@code Exchange_Type} parameter to a product family.
   *
   * @deprecated Legacy selection honored during the grace period; use {@link #PRODUCT_FAMILY}.
   */
  private static BinanceProductFamily legacyProductFamily(ExchangeSpecification specification) {
    Object legacy = specification.getExchangeSpecificParametersItem("Exchange_Type");
    if (legacy instanceof org.knowm.xchange.binance.dto.ExchangeType) {
      switch ((org.knowm.xchange.binance.dto.ExchangeType) legacy) {
        case FUTURES:
          return BinanceProductFamily.USDM;
        case INVERSE:
          return BinanceProductFamily.COINM;
        case PORTFOLIO_MARGIN:
          return BinanceProductFamily.PORTFOLIO_MARGIN;
        default:
          return BinanceProductFamily.SPOT;
      }
    }
    return BinanceProductFamily.SPOT;
  }

  @SuppressWarnings("unchecked")
  private static <T> T typedOrDefault(
      ExchangeSpecification specification, String key, T defaultValue) {
    Object value = specification.getExchangeSpecificParametersItem(key);
    if (value == null) {
      return defaultValue;
    }
    if (defaultValue != null && !defaultValue.getClass().isInstance(value)) {
      throw new IllegalArgumentException(
          "Binance exchange-specific parameter \""
              + key
              + "\" must be a "
              + defaultValue.getClass().getSimpleName()
              + ", got "
              + value.getClass().getSimpleName()
              + " ("
              + value
              + ").");
    }
    return (T) value;
  }

  private static String stringParameter(ExchangeSpecification specification, String key) {
    Object value = specification.getExchangeSpecificParametersItem(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(
          "Binance exchange-specific parameter \""
              + key
              + "\" must be a String, got "
              + value.getClass().getSimpleName()
              + ".");
    }
    return (String) value;
  }

  private static int intParameter(
      ExchangeSpecification specification, String key, int defaultValue) {
    Object value = specification.getExchangeSpecificParametersItem(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Binance exchange-specific parameter \"" + key + "\" could not be parsed as int.", e);
      }
    }
    throw new IllegalArgumentException(
        "Binance exchange-specific parameter \""
            + key
            + "\" must be a Number or String, got "
            + value.getClass().getSimpleName()
            + ".");
  }

  private static Long readRecvWindow(ExchangeSpecification specification) {
    Object typed = specification.getExchangeSpecificParametersItem(RECV_WINDOW);
    if (typed == null) {
      // Legacy parameter name honored during the grace period.
      typed = specification.getExchangeSpecificParametersItem("recvWindow");
    }
    if (typed == null) {
      return null;
    }
    final long value;
    if (typed instanceof Number) {
      value = ((Number) typed).longValue();
    } else if (typed instanceof String) {
      try {
        value = Long.parseLong((String) typed, 10);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Binance exchange-specific parameter \""
                + RECV_WINDOW
                + "\" could not be parsed as long.",
            e);
      }
    } else {
      throw new IllegalArgumentException(
          "Binance exchange-specific parameter \""
              + RECV_WINDOW
              + "\" must be a Number or String, got "
              + typed.getClass().getSimpleName()
              + ".");
    }
    if (value < 0 || value > MAX_RECV_WINDOW_MS) {
      throw new IllegalArgumentException(
          "Binance exchange-specific parameter \""
              + RECV_WINDOW
              + "\" must be in the range [0, "
              + MAX_RECV_WINDOW_MS
              + "], got "
              + value
              + ".");
    }
    return value;
  }

  private static final class Builder {
    private BinanceProductFamily productFamily;
    private BinanceKeyAlgorithm keyAlgorithm;
    private BinanceTimestampUnit timestampUnit;
    private boolean sandboxEnabled;
    private Long recvWindow;
    private String restBaseUrl;
    private String streamBaseUrl;
    private int orderBookDepth;
    private int orderBookUpdateCadenceMs;
  }
}
