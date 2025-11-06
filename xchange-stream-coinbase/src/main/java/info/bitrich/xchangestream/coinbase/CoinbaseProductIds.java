package info.bitrich.xchangestream.coinbase;

import org.knowm.xchange.currency.CurrencyPair;

final class CoinbaseProductIds {

  private CoinbaseProductIds() {}

  static String productId(CurrencyPair currencyPair) {
    return currencyPair.getBase().getCurrencyCode()
        + "-"
        + currencyPair.getCounter().getCurrencyCode();
  }
}

