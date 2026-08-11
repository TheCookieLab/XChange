package org.knowm.xchange.kucoin;

import static org.knowm.xchange.kucoin.KucoinExceptionClassifier.classifyingExceptions;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.dto.response.KucoinCurrencyResponseV3;
import org.knowm.xchange.kucoin.dto.response.SymbolResponse;
import org.knowm.xchange.kucoin.dto.response.WebsocketResponse;
import org.knowm.xchange.kucoin.uta.UtaAccountService;
import org.knowm.xchange.kucoin.uta.UtaAdapters;
import org.knowm.xchange.kucoin.uta.UtaMarketDataService;
import org.knowm.xchange.kucoin.uta.UtaTradeService;
import org.knowm.xchange.kucoin.uta.dto.UtaInstrument;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeType;

public class KucoinExchange extends BaseExchange implements Exchange {

  public static final String SANDBOX_URI = "https://openapi-sandbox.kucoin.com";
  public static final String PROD_URI = "https://api.kucoin.com";

  /**
   * Exchange-specific parameter selecting the KuCoin API generation.
   *
   * <p>Accepts {@link KucoinApiMode} values ({@code "CLASSIC"} or {@code "UTA"}, case-insensitive).
   * Absent or blank resolves to {@link KucoinApiMode#CLASSIC}, preserving the compatibility-period
   * default for existing consumers.
   */
  public static final String API_MODE_PARAMETER = "apiMode";

  private static ResilienceRegistries RESILIENCE_REGISTRIES;

  /** @return the API generation selected through {@link #API_MODE_PARAMETER}; never {@code null} */
  public KucoinApiMode getApiMode() {
    return KucoinApiMode.resolve(
        exchangeSpecification.getExchangeSpecificParametersItem(API_MODE_PARAMETER));
  }

  protected void concludeHostParams(ExchangeSpecification exchangeSpecification) {
    if (exchangeSpecification.getExchangeSpecificParameters() != null) {
      if (Boolean.TRUE.equals(
          exchangeSpecification.getExchangeSpecificParametersItem(USE_SANDBOX))) {
        logger.debug("Connecting to sandbox");
        exchangeSpecification.setSslUri(KucoinExchange.SANDBOX_URI);
        try {
          URL url = new URL(KucoinExchange.SANDBOX_URI);
          exchangeSpecification.setHost(url.getHost());
        } catch (MalformedURLException exception) {
          logger.error("Kucoin sandbox host exception: {}", exception.getMessage());
        }
      } else {
        logger.debug("Connecting to live");
      }
    }
  }

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    super.applySpecification(exchangeSpecification);
    concludeHostParams(exchangeSpecification);
  }

  @Override
  protected void initServices() {
    concludeHostParams(exchangeSpecification);
    if (getApiMode() == KucoinApiMode.UTA) {
      this.marketDataService = new UtaMarketDataService(this, getResilienceRegistries());
      this.accountService = new UtaAccountService(this, getResilienceRegistries());
      this.tradeService = new UtaTradeService(this, getResilienceRegistries());
    } else {
      this.marketDataService = new KucoinMarketDataService(this, getResilienceRegistries());
      this.accountService = new KucoinAccountService(this, getResilienceRegistries());
      this.tradeService = new KucoinTradeService(this, getResilienceRegistries());
    }
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri(PROD_URI);
    try {
      URL url = new URL(KucoinExchange.PROD_URI);
      exchangeSpecification.setHost(url.getHost());
    } catch (MalformedURLException exception) {
      logger.error("Kucoin host exception: {}", exception.getMessage());
    }
    exchangeSpecification.setPort(80);
    exchangeSpecification.setExchangeName("Kucoin");
    exchangeSpecification.setExchangeDescription("Kucoin is a bitcoin and altcoin exchange.");
    return exchangeSpecification;
  }

  @Override
  public ResilienceRegistries getResilienceRegistries() {
    if (RESILIENCE_REGISTRIES == null) {
      RESILIENCE_REGISTRIES = KucoinResilience.createRegistries();
    }
    return RESILIENCE_REGISTRIES;
  }

  @Override
  public void remoteInit() throws IOException, ExchangeException {
    if (getApiMode() == KucoinApiMode.UTA) {
      remoteInitUta();
    } else {
      remoteInitClassic();
    }
  }

  private void remoteInitClassic() throws IOException {
    List<SymbolResponse> symbols = getMarketDataService().getKucoinSymbolsV2();

    Map<Instrument, InstrumentMetaData> instruments =
        symbols.stream()
            .collect(
                Collectors.toMap(
                    SymbolResponse::getCurrencyPair, KucoinAdapters::toInstrumentMetaData));

    List<KucoinCurrencyResponseV3> currencies = getMarketDataService().getAllKucoinCurrencies();

    Map<Currency, CurrencyMetaData> currencyMetaData =
        currencies.stream()
            .collect(
                Collectors.toMap(
                    KucoinCurrencyResponseV3::getCurrency, KucoinAdapters::toCurrencyMetaData));

    exchangeMetaData.setInstruments(instruments);
    exchangeMetaData.setCurrencies(currencyMetaData);
  }

  private void remoteInitUta() throws IOException {
    UtaMarketDataService marketDataService = getUtaMarketDataService();
    Map<Instrument, InstrumentMetaData> instruments = new java.util.LinkedHashMap<>();
    Map<Instrument, String> providerSymbols = new java.util.concurrent.ConcurrentHashMap<>();

    for (UtaTradeType tradeType : new UtaTradeType[] {UtaTradeType.SPOT, UtaTradeType.FUTURES}) {
      List<UtaInstrument> catalog = marketDataService.getUtaInstruments(tradeType.name());
      for (UtaInstrument instrument : catalog) {
        Instrument xchangeInstrument =
            UtaAdapters.adaptInstrument(tradeType.name(), instrument);
        instruments.put(xchangeInstrument, UtaAdapters.toInstrumentMetaData(instrument));
        providerSymbols.put(xchangeInstrument, instrument.getSymbol());
      }
    }

    utaProviderSymbols.clear();
    utaProviderSymbols.putAll(providerSymbols);
    exchangeMetaData.setInstruments(instruments);
  }

  private final java.util.concurrent.ConcurrentHashMap<Instrument, String> utaProviderSymbols =
      new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * @return the provider symbol registered for the instrument during UTA {@link #remoteInit()},
   *     deriving the conventional symbol when the catalog is unavailable
   */
  public String getUtaProviderSymbol(Instrument instrument) {
    String registered = utaProviderSymbols.get(instrument);
    return registered != null
        ? registered
        : UtaAdapters.adaptSymbol(instrument, java.util.Collections.emptyMap());
  }

  @Override
  public KucoinMarketDataService getMarketDataService() {
    requireClassicMode("market data", "getUtaMarketDataService");
    return (KucoinMarketDataService) super.getMarketDataService();
  }

  @Override
  public KucoinTradeService getTradeService() {
    requireClassicMode("trade", "getUtaTradeService");
    return (KucoinTradeService) super.getTradeService();
  }

  @Override
  public KucoinAccountService getAccountService() {
    requireClassicMode("account", "getUtaAccountService");
    return (KucoinAccountService) super.getAccountService();
  }

  public UtaMarketDataService getUtaMarketDataService() {
    requireUtaMode("market data");
    return (UtaMarketDataService) super.getMarketDataService();
  }

  public UtaTradeService getUtaTradeService() {
    requireUtaMode("trade");
    return (UtaTradeService) super.getTradeService();
  }

  public UtaAccountService getUtaAccountService() {
    requireUtaMode("account");
    return (UtaAccountService) super.getAccountService();
  }

  private void requireClassicMode(String service, String utaAccessor) {
    if (getApiMode() != KucoinApiMode.CLASSIC) {
      throw new IllegalStateException(
          "Classic " + service + " service is not available in " + getApiMode()
              + " mode; select the UTA service through " + utaAccessor + "()");
    }
  }

  private void requireUtaMode(String service) {
    if (getApiMode() != KucoinApiMode.UTA) {
      throw new IllegalStateException(
          "UTA " + service + " service is not available in " + getApiMode()
              + " mode; set exchange parameter '"
              + API_MODE_PARAMETER
              + "' to UTA or use the classic getter");
    }
  }

  /** Classic public WebSocket connection details; unavailable in UTA mode. */
  public WebsocketResponse getPublicWebsocketConnectionDetails() throws IOException {
    requireClassicMode("websocket", "getUtaMarketDataService");
    return classifyingExceptions(getAccountService().websocketAPI::getPublicWebsocketDetails);
  }

  /** Classic private WebSocket connection details; unavailable in UTA mode. */
  public WebsocketResponse getPrivateWebsocketConnectionDetails() throws IOException {
    requireClassicMode("websocket", "getUtaAccountService");
    getAccountService().checkAuthenticated();

    return classifyingExceptions(
        () ->
            getAccountService()
                .websocketAPI
                .getPrivateWebsocketDetails(
                    getAccountService().apiKey,
                    getAccountService().digest,
                    getAccountService().nonceFactory,
                    getAccountService().passphrase));
  }
}
