package info.bitrich.xchangestream.coinbase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parameters controlling Coinbase candle subscriptions.
 */
public final class CoinbaseCandleSubscriptionParams {

  private final CoinbaseCandleGranularity granularity;
  private final String productType;

  public CoinbaseCandleSubscriptionParams(CoinbaseCandleGranularity granularity) {
    this(granularity, null);
  }

  public CoinbaseCandleSubscriptionParams(
      CoinbaseCandleGranularity granularity, String productType) {
    this.granularity = Objects.requireNonNull(granularity, "granularity");
    this.productType = productType;
  }

  public CoinbaseCandleGranularity getGranularity() {
    return granularity;
  }

  public String getProductType() {
    return productType;
  }

  CoinbaseCandleSubscriptionParams withProductType(String newProductType) {
    if (Objects.equals(productType, newProductType)) {
      return this;
    }
    return new CoinbaseCandleSubscriptionParams(granularity, newProductType);
  }

  Map<String, Object> toChannelArgs() {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("granularity", granularity.apiValue());
    if (productType != null && !productType.isEmpty()) {
      args.put("product_type", productType);
    }
    return args;
  }
}
