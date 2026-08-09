package org.knowm.xchange.coinbase.v3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseMarketDataServiceRaw;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;

/**
 * First-class Coinbase Advanced Trade product identity catalog.
 *
 * <p>Builds the authoritative instrument mapping from product discovery, preserving the native
 * {@code product_id}, product type, venue, and perpetual flag. Spot products map to {@link
 * CurrencyPair}; futures and perpetual products map to {@link FuturesContract} with a prompt
 * derived from the native product id. Mappings that cannot be resolved losslessly (missing base or
 * quote currency, or the same instrument produced by distinct product ids) are rejected with an
 * explicit exception instead of being silently resolved.
 *
 * <p>This replaces the global {@code Coinbase_Product_Id_Override} workaround and the two-token
 * {@code BASE-COUNTER} derivation as the primary identity strategy.
 *
 * @since 1.0
 */
public final class CoinbaseProductIdentity {

  /** Maximum number of products fetched during {@link #discover(CoinbaseMarketDataServiceRaw)}. */
  public static final int DISCOVERY_PAGE_SIZE = 250;

  private static final int DISCOVERY_MAX_PRODUCTS = 10_000;

  /** Native product metadata preserved from discovery. */
  public record Product(
      String productId,
      String productType,
      String productVenue,
      String baseCurrencyId,
      String quoteCurrencyId,
      boolean perpetual) {

    public Product {
      Objects.requireNonNull(productId, "productId");
    }
  }

  /** Raised when a product or instrument cannot be mapped without information loss. */
  public static final class AmbiguousMappingException extends ExchangeException {

    private static final long serialVersionUID = 1L;

    public AmbiguousMappingException(String message) {
      super(message);
    }
  }

  private final Map<String, Product> productByProductId;
  private final Map<Instrument, String> productIdByInstrument;
  private final Map<String, Instrument> instrumentByProductId;

  private CoinbaseProductIdentity(
      Map<String, Product> productByProductId,
      Map<Instrument, String> productIdByInstrument,
      Map<String, Instrument> instrumentByProductId) {
    this.productByProductId = Collections.unmodifiableMap(productByProductId);
    this.productIdByInstrument = Collections.unmodifiableMap(productIdByInstrument);
    this.instrumentByProductId = Collections.unmodifiableMap(instrumentByProductId);
  }

  /** Returns the preserved native metadata for a product id, if discovered. */
  public Product product(String productId) {
    return productByProductId.get(productId);
  }

  /** Returns all discovered products in discovery order. */
  public List<Product> products() {
    return new ArrayList<>(productByProductId.values());
  }

  /** Returns the native product id for an instrument, or null when unknown. */
  public String productId(Instrument instrument) {
    return productIdByInstrument.get(instrument);
  }

  /**
   * Resolves an instrument from a native product id.
   *
   * @throws AmbiguousMappingException when the product is unknown, cannot be mapped losslessly, or
   *     maps to the same instrument as another product id.
   */
  public Instrument instrument(String productId) {
    if (productId == null) {
      throw new AmbiguousMappingException("product id is null");
    }
    Instrument instrument = instrumentByProductId.get(productId);
    if (instrument == null) {
      Product product = productByProductId.get(productId);
      if (product == null) {
        throw new AmbiguousMappingException("unknown product id '" + productId + "'");
      }
      throw new AmbiguousMappingException(
          "product '" + productId + "' cannot be mapped to an XChange instrument");
    }
    return instrument;
  }

  /**
   * Resolves the native product id for an instrument, rejecting lossy or ambiguous mappings.
   *
   * @throws AmbiguousMappingException when the instrument is unknown or ambiguous.
   */
  public String requireProductId(Instrument instrument) {
    String productId = productIdByInstrument.get(instrument);
    if (productId == null) {
      throw new AmbiguousMappingException("no unambiguous product id for instrument '" + instrument + "'");
    }
    return productId;
  }

  /**
   * Builds a catalog from a product discovery page sequence.
   *
   * <p>Products missing base or quote currency are preserved in the registry but not mapped.
   * Products whose derived instrument collides with another product id are not mapped; callers
   * must use the raw product id for those.
   */
  public static CoinbaseProductIdentity build(List<CoinbaseProductResponse> products) {
    Map<String, Product> productByProductId = new LinkedHashMap<>();
    Map<Instrument, String> productIdByInstrument = new LinkedHashMap<>();
    Map<String, Instrument> instrumentByProductId = new LinkedHashMap<>();

    for (CoinbaseProductResponse response : products) {
      if (response == null || response.getProductId() == null || response.getProductId().isEmpty()) {
        continue;
      }
      String productId = response.getProductId();
      Product product =
          new Product(
              productId,
              response.getProductType(),
              response.getProductVenue(),
              response.getBaseCurrencyId(),
              response.getQuoteCurrencyId(),
              isPerpetual(response));
      productByProductId.put(productId, product);

      Instrument instrument = mapInstrument(product);
      if (instrument == null) {
        continue;
      }
      String existing = productIdByInstrument.putIfAbsent(instrument, productId);
      if (existing != null && !existing.equals(productId)) {
        // Two distinct product ids produce the same instrument: reject both rather than
        // silently selecting one (for example identical contracts across venues).
        productIdByInstrument.remove(instrument);
        instrumentByProductId.remove(existing);
        continue;
      }
      instrumentByProductId.put(productId, instrument);
    }
    return new CoinbaseProductIdentity(productByProductId, productIdByInstrument, instrumentByProductId);
  }

  /**
   * Discovers the full product catalog through bounded offset pagination.
   *
   * @throws java.io.IOException on transport failure
   * @throws IllegalArgumentException when the raw service is null or discovery does not converge
   */
  public static CoinbaseProductIdentity discover(CoinbaseMarketDataServiceRaw rawService)
      throws Exception {
    if (rawService == null) {
      throw new IllegalArgumentException("raw market data service is required for discovery");
    }
    List<CoinbaseProductResponse> all = new ArrayList<>();
    int offset = 0;
    while (all.size() < DISCOVERY_MAX_PRODUCTS) {
      List<CoinbaseProductResponse> page = rawService.listProducts(DISCOVERY_PAGE_SIZE, offset, null);
      if (page == null || page.isEmpty()) {
        break;
      }
      all.addAll(page);
      if (page.size() < DISCOVERY_PAGE_SIZE) {
        break;
      }
      offset += DISCOVERY_PAGE_SIZE;
    }
    return build(all);
  }

  private static boolean isPerpetual(CoinbaseProductResponse response) {
    return response.getFutureProductDetails() != null
        && response.getFutureProductDetails().getPerpetualDetails() != null;
  }

  private static Instrument mapInstrument(Product product) {
    if (product.baseCurrencyId() == null
        || product.baseCurrencyId().isEmpty()
        || product.quoteCurrencyId() == null
        || product.quoteCurrencyId().isEmpty()) {
      return null;
    }
    CurrencyPair pair =
        new CurrencyPair(product.baseCurrencyId(), product.quoteCurrencyId());
    if ("SPOT".equalsIgnoreCase(product.productType())) {
      return pair;
    }
    if (!"FUTURE".equalsIgnoreCase(product.productType())) {
      return null;
    }
    String prompt = prompt(product);
    if (prompt == null) {
      return null;
    }
    return new FuturesContract(pair, prompt);
  }

  /**
   * Derives the futures prompt from the native product id by removing the base and quote currency
   * tokens, so dated contracts such as {@code BTC-28MAR25-CFMF} keep their expiry token. Perpetual
   * products use the canonical {@code PERP} prompt.
   */
  private static String prompt(Product product) {
    if (product.perpetual()) {
      return "PERP";
    }
    List<String> tokens = new ArrayList<>(Arrays.asList(product.productId().split("-")));
    tokens.removeIf(token -> token.equalsIgnoreCase(product.baseCurrencyId()));
    tokens.removeIf(token -> token.equalsIgnoreCase(product.quoteCurrencyId()));
    if (tokens.isEmpty()) {
      return null;
    }
    return String.join("-", tokens);
  }
}
