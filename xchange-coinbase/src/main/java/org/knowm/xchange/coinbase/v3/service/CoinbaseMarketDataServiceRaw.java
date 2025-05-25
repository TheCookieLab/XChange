package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAmount;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBooksResponse;
import org.knowm.xchange.currency.Currency;

class CoinbaseMarketDataServiceRaw extends CoinbaseBaseService {

  public CoinbaseMarketDataServiceRaw(Exchange exchange) {
    super(exchange);
  }

  /**
   * Retrieves the best bid and ask price book entries for a specified product.
   * This method authenticates the request using the stored API credentials.
   *
   * @param productId The product identifier (e.g., "BTC-USD") for which to fetch bid/ask data. If null is passed, then retrieve the best bid/ask data for ALL products
   * @return A list of {@link CoinbasePriceBook} objects containing bid and ask entries for the requested product.
   *         Each entry includes price levels, quantities, and timestamps.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public List<CoinbasePriceBook> getBestBidAsk(String productId) throws IOException {
    CoinbasePriceBooksResponse response = coinbaseAdvancedTrade.getBestBidAsk(authTokenCreator,
        productId);

    return response.getPriceBooks();
  }

  /**
   * Unauthenticated resource that tells you the price to buy one unit.
   *
   * @return The price in the desired {@code currency} to buy one unit.
   * @throws IOException
   * @see <a
   *     href="https://developers.coinbase.com/api/v2#get-buy-price">developers.coinbase.com/api/v2#get-buy-price</a>
   */
//  public CoinbaseAmount getCoinbaseBuyPrice(Currency base, Currency counter) throws IOException {
//    return coinbaseAdvancedTrade.getBuyPrice(Coinbase.CB_VERSION_VALUE, base + "-" + counter).getData();
//  }

  /**
   * Unauthenticated resource that tells you the amount you can get if you sell one unit.
   *
   * @return The price in the desired {@code currency} to sell one unit.
   * @throws IOException
   * @see <a
   *     href="https://developers.coinbase.com/api/v2#get-sell-price">developers.coinbase.com/api/v2#get-sell-price</a>
   */
//  public CoinbaseAmount getCoinbaseSellPrice(Currency base, Currency counter) throws IOException {
//    return coinbaseAdvancedTrade.getSellPrice(Coinbase.CB_VERSION_VALUE, base + "-" + counter).getData();
//  }

  /**
   * Unauthenticated resource that tells you the current price of one unit. This is usually
   * somewhere in between the buy and sell price, current to within a few minutes.
   *
   * @return The price in the desired {@code currency} for one unit.
   * @throws IOException
   * @see <a
   *     href="https://developers.coinbase.com/api/v2#get-spot-price">developers.coinbase.com/api/v2#get-spot-price</a>
   */
//  public CoinbaseAmount getCoinbaseSpotRate(Currency base, Currency counter) throws IOException {
//    return coinbaseAdvancedTrade.getSpotRate(Coinbase.CB_VERSION_VALUE, base + "-" + counter).getData();
//  }

  /**
   * Unauthenticated resource that tells you the current price of one unit. This is usually
   * somewhere in between the buy and sell price, current to within a few minutes.
   *
   * @param date The given date.
   * @return The price in the desired {@code currency} ont the give {@code date} for one unit.
   * @throws IOException
   * @see <a
   *     href="https://developers.coinbase.com/api/v2#get-spot-price">developers.coinbase.com/api/v2#get-spot-price</a>
   */
//  public CoinbaseAmount getCoinbaseHistoricalSpotRate(Currency base, Currency counter, Date date)
//      throws IOException {
//    String datespec = new SimpleDateFormat("yyyy-MM-dd").format(date);
//    return coinbaseAdvancedTrade
//        .getHistoricalSpotRate(Coinbase.CB_VERSION_VALUE, base + "-" + counter, datespec)
//        .getData();
//  }
}
