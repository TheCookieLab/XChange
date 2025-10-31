package org.knowm.xchange.coinbase.v2.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v2.Coinbase;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseCurrencyData.CoinbaseCurrency;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseTimeData.CoinbaseTime;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

/**
 * @deprecated The Coinbase v2 API has limited functionality. Authenticated endpoints no longer work
 *     due to changes in Coinbase's authentication mechanism. Use {@link
 *     org.knowm.xchange.coinbase.v3.CoinbaseExchange} instead for full trading functionality via the
 *     Coinbase Advanced Trade API.
 */
@Deprecated
public class CoinbaseBaseService extends BaseExchangeService implements BaseService {

  protected final Coinbase coinbase;

  protected CoinbaseBaseService(Exchange exchange) {

    super(exchange);
    coinbase =
        ExchangeRestProxyBuilder.forInterface(
                Coinbase.class, exchange.getExchangeSpecification())
            .build();
  }

  /**
   * Unauthenticated resource that returns currencies supported on Coinbase.
   *
   * @return A list of currency names and their corresponding ISO code.
   * @see <a
   *     href="https://developers.coinbase.com/api/v2#get-currencies">developers.coinbase.com/api/v2#get-currencies</a>
   */
  public List<CoinbaseCurrency> getCoinbaseCurrencies() throws IOException {

    return coinbase.getCurrencies(Coinbase.CB_VERSION_VALUE).getData();
  }

  /**
   * Unauthenticated resource that tells you the server time.
   *
   * @return The current server time.
   * @see <a
   *     href="https://developers.coinbase.com/api/v2#get-current-time">developers.coinbase.com/api/v2#get-current-time</a>
   */
  public CoinbaseTime getCoinbaseTime() throws IOException {

    return coinbase.getTime(Coinbase.CB_VERSION_VALUE).getData();
  }
}
