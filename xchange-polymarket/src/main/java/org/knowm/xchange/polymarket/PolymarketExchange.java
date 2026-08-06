package org.knowm.xchange.polymarket;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.service.PolymarketAccountService;
import org.knowm.xchange.polymarket.service.PolymarketMarketDataService;
import org.knowm.xchange.polymarket.service.PolymarketTradeService;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Polymarket prediction-market exchange over the Gamma (discovery), CLOB (books and trading), and
 * Data (trades/positions) APIs.
 *
 * <p>Instruments are {@link PredictionMarketContract}s whose market id is the condition id and
 * whose outcome id is the CLOB outcome-token id actually traded, quoted in pUSD (Polymarket's
 * native collateral token); outcome tokens are never silently complemented (see {@code
 * PolymarketAdapters}). Negative-risk markets are fully supported: discovery records the market
 * type and order signing selects the matching EIP-712 verifying contract.
 *
 * <p>Credential mapping on {@link ExchangeSpecification}: {@code userName} is the wallet address,
 * {@code apiKey}/{@code secretKey}/{@code password} are the L2 API key/secret/passphrase, and the
 * exchange-specific parameter {@link #PARAM_PRIVATE_KEY} holds the EOA private key (hex) used for
 * EIP-712 order signing and L1 credential derivation. Only EOA signatures (type 0) are
 * implemented; proxy, Gnosis Safe, and EIP-1271 wallet strategies are rejected before submission.
 */
public class PolymarketExchange extends BaseExchange {

  /** CLOB API base URI (books, prices, orders, auth). */
  public static final String CLOB_URI = "https://clob.polymarket.com";

  /** Gamma API base URI (public market/event discovery). */
  public static final String GAMMA_URI = "https://gamma-api.polymarket.com";

  /** Data API base URI (public trades and positions). */
  public static final String DATA_URI = "https://data-api.polymarket.com";

  /** Exchange-specific parameter carrying the hex-encoded EOA private key used for signing. */
  public static final String PARAM_PRIVATE_KEY = "polymarket.private.key";

  /** Exchange-specific parameter overriding the CLOB base URI, for example in tests. */
  public static final String PARAM_CLOB_URI = "polymarket.clob.uri";

  /** Exchange-specific parameter overriding the Gamma base URI. */
  public static final String PARAM_GAMMA_URI = "polymarket.gamma.uri";

  /** Exchange-specific parameter overriding the Data base URI. */
  public static final String PARAM_DATA_URI = "polymarket.data.uri";

  @Override
  protected void initServices() {
    marketDataService = new PolymarketMarketDataService(this);
    accountService = new PolymarketAccountService(this);
    tradeService = new PolymarketTradeService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification specification = new ExchangeSpecification(getClass());
    specification.setSslUri(CLOB_URI);
    specification.setHost("clob.polymarket.com");
    specification.setExchangeName("Polymarket");
    specification.setExchangeDescription("Polymarket prediction-market exchange");
    return specification;
  }

  /** Resolves an optional URI override against the production default. */
  public String resolveUri(String parameterName, String defaultUri) {
    Object override = getExchangeSpecification().getExchangeSpecificParametersItem(parameterName);
    return override == null ? defaultUri : override.toString();
  }

  @Override
  public void remoteInit() throws IOException {
    List<PolymarketGammaMarket> markets =
        ((PolymarketMarketDataService) marketDataService).getAllActiveGammaMarkets();
    exchangeMetaData.getInstruments().clear();
    exchangeMetaData.getCurrencies().clear();
    for (PolymarketGammaMarket market : markets) {
      for (int outcomeIndex = 0;
          outcomeIndex < PolymarketAdapters.tokenIds(market).size();
          outcomeIndex++) {
        Instrument instrument = PolymarketAdapters.adaptContract(market, outcomeIndex);
        exchangeMetaData
            .getInstruments()
            .put(instrument, PolymarketAdapters.adaptMetadata(market));
      }
    }
    exchangeMetaData.getCurrencies().put(Currency.PUSD, new CurrencyMetaData(6, null));
  }
}
