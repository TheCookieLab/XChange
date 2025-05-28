package org.knowm.xchange.coinbase.v3.service;

import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseCurrencyData.CoinbaseCurrency;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseTimeData.CoinbaseTime;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseBaseService extends BaseExchangeService implements BaseService {

  protected final CoinbaseAuthenticated coinbaseAdvancedTrade;
  protected final ParamsDigest authTokenCreator;

  protected CoinbaseBaseService(Exchange exchange) {
    this(exchange, ExchangeRestProxyBuilder.forInterface(CoinbaseAuthenticated.class,
            exchange.getExchangeSpecification()).build(),
        CoinbaseV3Digest.createInstance(exchange.getExchangeSpecification().getApiKey(),
            exchange.getExchangeSpecification().getSecretKey()));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    this(exchange, coinbaseAdvancedTrade,
        CoinbaseV3Digest.createInstance(exchange.getExchangeSpecification().getApiKey(),
            exchange.getExchangeSpecification().getSecretKey()));
  }

  public CoinbaseBaseService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange);
    this.coinbaseAdvancedTrade = coinbaseAdvancedTrade;
    this.authTokenCreator = authTokenCreator;
  }

  /**
   * Unauthenticated resource that returns currencies supported on Coinbase.
   *
   * @return A list of currency names and their corresponding ISO code.
   * @see <a
   * href="https://developers.coinbase.com/api/v2#get-currencies">developers.coinbase.com/api/v2#get-currencies</a>
   */
  public List<CoinbaseCurrency> getCoinbaseCurrencies() throws IOException {
    return coinbaseAdvancedTrade.getCurrencies(Coinbase.CB_VERSION_VALUE).getData();
  }

  /**
   * Unauthenticated resource that tells you the server time.
   *
   * @return The current server time.
   * @see <a
   * href="https://developers.coinbase.com/api/v2#get-current-time">developers.coinbase.com/api/v2#get-current-time</a>
   */
  public CoinbaseTime getCoinbaseTime() throws IOException {
    return coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData();
  }

  protected void showCurl(HttpMethod method, String apiKey, BigDecimal timestamp, String signature,
      String path, String json) {
    String headers = String.format(
        "-H 'CB-VERSION: 2017-11-26' -H 'CB-ACCESS-KEY: %s' -H 'CB-ACCESS-SIGN: %s' -H 'CB-ACCESS-TIMESTAMP: %s'",
        apiKey, signature, timestamp);
    if (method == HttpMethod.GET) {
      Coinbase.LOG.debug(String.format("curl %s https://api.coinbase.com%s", headers, path));
    } else if (method == HttpMethod.POST) {
      String payload = "-d '" + json + "'";
      Coinbase.LOG.debug(
          String.format("curl -X %s -H 'Content-Type: %s' %s %s https://api.coinbase.com%s", method,
              MediaType.APPLICATION_JSON, headers, payload, path));
    }
  }

  public enum HttpMethod {
    GET, POST
  }
}
