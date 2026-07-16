package org.knowm.xchange.coinbasederivatives;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasederivatives.auth.AccessToken;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesAccessTokenProvider;
import org.knowm.xchange.coinbasederivatives.auth.CoinbaseDerivativesJwtGenerator;
import org.knowm.xchange.coinbasederivatives.client.CoinbaseDerivativesJsonRpcTransport;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.coinbasederivatives.service.CoinbaseDerivativesAccountService;
import org.knowm.xchange.coinbasederivatives.service.CoinbaseDerivativesMarketDataService;
import org.knowm.xchange.coinbasederivatives.service.CoinbaseDerivativesTradeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.instrument.Instrument;

/** Coinbase Advanced international derivatives exchange over the Starbase gateway. */
public class CoinbaseDerivativesExchange extends BaseExchange {
  public static final String HTTP_URI = "https://drb.coinbase.com/api/v2";
  public static final String WEBSOCKET_URI = "wss://drb.coinbase.com/ws/api/v2";
  public static final String CANCEL_ON_DISCONNECT = "CancelOnDisconnect";
  public static final String WEBSOCKET_URI_PARAMETER = "WebsocketUri";

  private CoinbaseDerivativesJsonRpcTransport jsonRpcTransport;

  public CoinbaseDerivativesExchange() {}

  CoinbaseDerivativesExchange(CoinbaseDerivativesJsonRpcTransport jsonRpcTransport) {
    this.jsonRpcTransport = jsonRpcTransport;
  }

  @Override
  protected void initServices() {
    marketDataService = new CoinbaseDerivativesMarketDataService(this);
    accountService = new CoinbaseDerivativesAccountService(this);
    tradeService = new CoinbaseDerivativesTradeService(this);
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification specification = new ExchangeSpecification(getClass());
    specification.setSslUri(HTTP_URI);
    specification.setHost("drb.coinbase.com");
    specification.setExchangeName("Coinbase Derivatives");
    specification.setExchangeDescription(
        "Coinbase Advanced international derivatives on the Starbase gateway");
    specification.setOverrideWebsocketApiUri(WEBSOCKET_URI);
    specification.setExchangeSpecificParametersItem(WEBSOCKET_URI_PARAMETER, WEBSOCKET_URI);
    specification.setExchangeSpecificParametersItem(CANCEL_ON_DISCONNECT, false);
    return specification;
  }

  /** Returns the exchange-owned transport shared by all REST services. */
  public synchronized CoinbaseDerivativesJsonRpcTransport getJsonRpcTransport() {
    if (jsonRpcTransport == null) {
      jsonRpcTransport =
          new CoinbaseDerivativesJsonRpcTransport(
              URI.create(getExchangeSpecification().getSslUri()));
      String apiKey = getExchangeSpecification().getApiKey();
      String secretKey = getExchangeSpecification().getSecretKey();
      if (apiKey != null && secretKey != null) {
        CoinbaseDerivativesJwtGenerator generator =
            new CoinbaseDerivativesJwtGenerator(apiKey, secretKey);
        CoinbaseDerivativesAccessTokenProvider provider =
            new CoinbaseDerivativesAccessTokenProvider(
                generator,
                freshJwt ->
                    jsonRpcTransport.callPublicOnce(
                        "public/auth",
                        Map.of("grant_type", "coinbase_cdp", "token", freshJwt),
                        AccessToken.class));
        jsonRpcTransport.setAccessTokenProvider(provider);
      }
    }
    return jsonRpcTransport;
  }

  @Override
  public void remoteInit() throws IOException {
    List<CoinbaseDerivativesInstrument> providerInstruments =
        ((CoinbaseDerivativesMarketDataService) marketDataService)
            .getInstruments("any", null, false);
    exchangeMetaData.getInstruments().clear();
    exchangeMetaData.getCurrencies().clear();
    CurrencyMetaData defaultCurrencyMetadata = new CurrencyMetaData(8, null);
    Set<String> currencies = new LinkedHashSet<>();
    for (CoinbaseDerivativesInstrument providerInstrument : providerInstruments) {
      if (!providerInstrument.active()) {
        continue;
      }
      Instrument instrument = CoinbaseDerivativesAdapters.registerInstrument(providerInstrument);
      exchangeMetaData
          .getInstruments()
          .put(instrument, CoinbaseDerivativesAdapters.adaptMetadata(providerInstrument));
      currencies.add(providerInstrument.baseCurrency());
      currencies.add(providerInstrument.counterCurrency());
      if (providerInstrument.settlementCurrency() != null) {
        currencies.add(providerInstrument.settlementCurrency());
      }
    }
    for (String currency : currencies) {
      exchangeMetaData.getCurrencies().put(Currency.getInstance(currency), defaultCurrencyMetadata);
    }
  }
}
