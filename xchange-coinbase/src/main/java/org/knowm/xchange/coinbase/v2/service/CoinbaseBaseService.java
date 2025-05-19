package org.knowm.xchange.coinbase.v2.service;

import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Mac;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.service.CoinbaseDigest;
import org.knowm.xchange.coinbase.v2.Coinbase;
import org.knowm.xchange.coinbase.v2.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v2.CoinbaseV2Digest;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseCryptocurrencyData.CoinbaseCryptocurrency;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseFiatCurrencyData.CoinbaseFiatCurrency;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseTimeData.CoinbaseTime;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;
import org.knowm.xchange.utils.DigestUtils;

public class CoinbaseBaseService extends BaseExchangeService implements BaseService {

  protected final CoinbaseAuthenticated coinbase;
  protected final CoinbaseV2Digest authTokenGenerator;

  protected CoinbaseBaseService(Exchange exchange) {

    super(exchange);
    coinbase = ExchangeRestProxyBuilder.forInterface(CoinbaseAuthenticated.class,
        exchange.getExchangeSpecification()).build();

    authTokenGenerator = CoinbaseV2Digest.createInstance(
        exchange.getExchangeSpecification().getApiKey(),
        exchange.getExchangeSpecification().getSecretKey());
  }

  /**
   * Unauthenticated resource that returns cryptocurrencies supported on Coinbase.
   *
   * @return A list of cryptocurrency names and their corresponding ISO code.
   * @see <a
   * href="https://docs.cdp.coinbase.com/coinbase-app/docs/track/api-currencies">https://docs.cdp.coinbase.com/coinbase-app/docs/track/api-currencies</a>
   */
  public List<CoinbaseCryptocurrency> getCoinbaseCryptocurrencies() throws IOException {
    return coinbase.getCryptocurrencies(Coinbase.CB_VERSION_VALUE).getData();
  }

  /**
   * Unauthenticated resource that returns fiat currencies supported on Coinbase.
   *
   * @return A list of fiat currency names and their corresponding ISO code.
   * @see <a
   * href="https://docs.cdp.coinbase.com/coinbase-app/docs/track/api-currencies">https://docs.cdp.coinbase.com/coinbase-app/docs/track/api-currencies</a>
   */
  public List<CoinbaseFiatCurrency> getCoinbaseFiatCurrencies() throws IOException {
    return coinbase.getFiatCurrencies(Coinbase.CB_VERSION_VALUE).getData();
  }

  /**
   * Unauthenticated resource that tells you the server time.
   *
   * @return The current server time.
   * @see <a
   * href="https://developers.coinbase.com/api/v2#get-current-time">developers.coinbase.com/api/v2#get-current-time</a>
   */
  public CoinbaseTime getCoinbaseTime() throws IOException {

    return coinbase.getTime(Coinbase.CB_VERSION_VALUE).getData();
  }

  protected String getSignature(BigDecimal timestamp, HttpMethod method, String path, String body) {
    String secretKey = exchange.getExchangeSpecification().getSecretKey();
    String message = timestamp + method.toString() + path + (body != null ? body : "");
    final Mac mac = CoinbaseDigest.createInstance(secretKey).getMac();
    byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    return DigestUtils.bytesToHex(bytes);
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
