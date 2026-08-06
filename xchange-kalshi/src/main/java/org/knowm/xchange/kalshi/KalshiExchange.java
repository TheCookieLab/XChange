package org.knowm.xchange.kalshi;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kalshi.dto.marketdata.KalshiMarket;
import org.knowm.xchange.kalshi.service.KalshiAccountService;
import org.knowm.xchange.kalshi.service.KalshiMarketDataService;
import org.knowm.xchange.kalshi.service.KalshiTradeService;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Kalshi prediction-market exchange.
 *
 * <p>Markets are exposed as {@link PredictionMarketContract} instruments on the YES leg; NO
 * exposure is never silently synthesized from generic sell orders (see {@code KalshiAdapters}).
 * Credentials are the Kalshi API key id ({@code apiKey}) and the RSA private key in unencrypted
 * PKCS#8 PEM form ({@code secretKey}).
 */
public class KalshiExchange extends BaseExchange {

  /** Production REST base URI (API paths are appended by the client interfaces). */
  public static final String HTTP_URI = "https://api.elections.kalshi.com";

  /** Exchange-specific parameter overriding the REST base URI, for example a demo host. */
  public static final String SSL_URI_PARAMETER = "SslUri";

  @Override
  protected void initServices() {
    marketDataService = new KalshiMarketDataService(this);
    accountService = new KalshiAccountService(this);
    tradeService = new KalshiTradeService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification specification = new ExchangeSpecification(getClass());
    specification.setSslUri(HTTP_URI);
    specification.setHost("api.elections.kalshi.com");
    specification.setExchangeName("Kalshi");
    specification.setExchangeDescription("Kalshi prediction-market exchange");
    return specification;
  }

  @Override
  public void remoteInit() throws IOException {
    List<KalshiMarket> markets =
        ((KalshiMarketDataService) marketDataService).getAllOpenKalshiMarkets();
    exchangeMetaData.getInstruments().clear();
    exchangeMetaData.getCurrencies().clear();
    for (KalshiMarket market : markets) {
      // Tradable markets report lifecycle status "active" (the "open" status filter is a query
      // vocabulary, not the returned status value).
      if (!"active".equalsIgnoreCase(market.status())) {
        continue;
      }
      Instrument instrument = KalshiAdapters.adaptContract(market);
      exchangeMetaData.getInstruments().put(instrument, KalshiAdapters.adaptMetadata(market));
    }
    exchangeMetaData.getCurrencies().put(Currency.USD, new CurrencyMetaData(4, null));
  }
}
