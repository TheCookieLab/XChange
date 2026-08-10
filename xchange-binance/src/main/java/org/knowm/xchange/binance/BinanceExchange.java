package org.knowm.xchange.binance;

import java.io.IOException;
import java.util.Map;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.config.BinanceConfiguration;
import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.dto.ExchangeType;
import org.knowm.xchange.binance.dto.account.AssetDetail;
import org.knowm.xchange.binance.dto.meta.exchangeinfo.BinanceExchangeInfo;
import org.knowm.xchange.binance.service.BinanceAccountService;
import org.knowm.xchange.binance.service.BinanceMarketDataService;
import org.knowm.xchange.binance.service.BinanceMarketDataServiceRaw;
import org.knowm.xchange.binance.service.BinanceTradeService;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.utils.AuthUtils;
import si.mazi.rescu.SynchronizedValueFactory;

public class BinanceExchange extends BaseExchange implements Exchange {

  /**
   * Legacy exchange-type parameter selecting Spot, Futures, Inverse, or Portfolio Margin mode.
   *
   * @deprecated Use the typed {@link org.knowm.xchange.binance.config.BinanceConfiguration#PRODUCT_FAMILY}
   *     parameter with a {@link org.knowm.xchange.binance.config.BinanceProductFamily} value
   *     instead. The legacy parameter remains honored during the documented grace period.
   */
  @Deprecated public static String EXCHANGE_TYPE = "Exchange_Type";
  private static final String SPOT_URL = "https://api.binance.com";
  public static final String FUTURES_URL = "https://fapi.binance.com";
  public static final String INVERSE_FUTURES_URL = "https://dapi.binance.com";
  public static final String PORTFOLIO_MARGIN_URL = "https://papi.binance.com";

  public static final String SANDBOX_SPOT_URL = "https://testnet.binance.vision";
  public static final String SANDBOX_FUTURES_URL = "https://testnet.binancefuture.com";
  public static final String SANDBOX_INVERSE_FUTURES_URL = "https://testnet.binancefuture.com";

  protected ResilienceRegistries RESILIENCE_REGISTRIES;
  protected SynchronizedValueFactory<Long> timestampFactory;
  protected BinanceConfiguration configuration;

  @Override
  protected void initServices() {
    this.timestampFactory =
        new BinanceTimestampFactory(
            getExchangeSpecification().getResilience(),
            getResilienceRegistries(),
            configuration.getTimestampUnit());
    this.marketDataService = new BinanceMarketDataService(this, getResilienceRegistries());
    this.tradeService = new BinanceTradeService(this, getResilienceRegistries());
    this.accountService = new BinanceAccountService(this, getResilienceRegistries());
  }

  /** Typed configuration derived from the applied exchange specification. */
  public BinanceConfiguration getConfiguration() {
    return configuration;
  }

  public SynchronizedValueFactory<Long> getTimestampFactory() {
    return timestampFactory;
  }

  @Override
  public SynchronizedValueFactory<Long> getNonceFactory() {
    throw new UnsupportedOperationException(
        "Binance uses timestamp/recvwindow rather than a nonce");
  }

  public void resetResilienceRegistries() {
    RESILIENCE_REGISTRIES = createResilienceRegistries();
  }

  @Override
  public ResilienceRegistries getResilienceRegistries() {
    if (RESILIENCE_REGISTRIES == null) {
      RESILIENCE_REGISTRIES = createResilienceRegistries();
    }
    return RESILIENCE_REGISTRIES;
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification spec = new ExchangeSpecification(this.getClass());
    spec.setSslUri(SPOT_URL);
    spec.setHost("www.binance.com");
    spec.setPort(80);
    spec.setExchangeName("Binance");
    spec.setExchangeDescription("Binance Exchange.");
    spec.setExchangeSpecificParametersItem(EXCHANGE_TYPE, ExchangeType.SPOT);
    spec.setExchangeSpecificParametersItem(USE_SANDBOX, false);
    AuthUtils.setApiAndSecretKey(spec, "binance");
    return spec;
  }

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    this.configuration = BinanceConfiguration.from(exchangeSpecification);
    concludeHostParams(exchangeSpecification);
    super.applySpecification(exchangeSpecification);
  }

  /**
   * @deprecated Use {@link #getConfiguration()}{@code .getProductFamily() ==
   *     BinanceProductFamily.FUTURES} instead.
   */
  @Deprecated
  public boolean isFuturesEnabled() {
    return getProductFamily().equals(BinanceProductFamily.USDM);
  }

  /**
   * @deprecated Use {@link #getConfiguration()}{@code .getProductFamily() ==
   *     BinanceProductFamily.SPOT} instead.
   */
  @Deprecated
  public boolean isSpotEnabled() {
    return getProductFamily().equals(BinanceProductFamily.SPOT);
  }

  /**
   * @deprecated Use {@link #getConfiguration()}{@code .getProductFamily() ==
   *     BinanceProductFamily.PORTFOLIO_MARGIN} instead.
   */
  @Deprecated
  public boolean isPortfolioMarginEnabled() {
    return getProductFamily().equals(BinanceProductFamily.PORTFOLIO_MARGIN);
  }

  /** The configured Binance product family (defaults to {@link BinanceProductFamily#SPOT}). */
  public BinanceProductFamily getProductFamily() {
    return configuration != null
        ? configuration.getProductFamily()
        : legacyExchangeTypeOrDefault();
  }

  public boolean usingSandbox() {
    return configuration != null
        ? configuration.isSandboxEnabled()
        : enabledSandbox(exchangeSpecification);
  }

  private BinanceProductFamily legacyExchangeTypeOrDefault() {
    Object legacy = exchangeSpecification.getExchangeSpecificParametersItem(EXCHANGE_TYPE);
    if (legacy instanceof ExchangeType) {
      switch ((ExchangeType) legacy) {
        case FUTURES:
          return BinanceProductFamily.USDM;
        case INVERSE:
          return BinanceProductFamily.COINM;
        case PORTFOLIO_MARGIN:
          return BinanceProductFamily.PORTFOLIO_MARGIN;
        default:
          break;
      }
    }
    return BinanceProductFamily.SPOT;
  }

  @Override
  public void remoteInit() {
    try {
      BinanceMarketDataServiceRaw marketDataServiceRaw =
          (BinanceMarketDataServiceRaw) marketDataService;
      BinanceAccountService accountService = (BinanceAccountService) getAccountService();

      BinanceExchangeInfo exchangeInfo;
      BinanceProductFamily productFamily = getProductFamily();

      switch (productFamily) {
        case USDM:
        case COINM:
          exchangeInfo = marketDataServiceRaw.getFutureExchangeInfo();
          BinanceAdapters.adaptFutureExchangeMetaData(exchangeMetaData, exchangeInfo);
          break;
        default:
          Map<String, AssetDetail> assetDetailMap = null;
          if (!usingSandbox() && isAuthenticated()) {
            assetDetailMap = accountService.getAssetDetails(); // not available in sndbox
          }
          exchangeInfo = marketDataServiceRaw.getExchangeInfo();
          exchangeMetaData = BinanceAdapters.adaptExchangeMetaData(exchangeInfo, assetDetailMap);
      }

      // init symbol mappings
      exchangeInfo.getSymbols().stream()
          .filter(
              symbol ->
                  symbol.getBaseAsset() != null
                      && symbol.getQuoteAsset() != null
                      && symbol.getSymbol() != null)
          .forEach(
              symbol ->
                  BinanceAdapters.putSymbolMapping(
                      symbol.getSymbol(),
                      new CurrencyPair(symbol.getBaseAsset(), symbol.getQuoteAsset())));

    } catch (IOException e) {
      throw new ExchangeException("Failed to initialize: " + e.getMessage(), e);
    }
  }

  private ResilienceRegistries createResilienceRegistries() {
    BinanceProductFamily family = getProductFamily();
    return family == BinanceProductFamily.USDM || family == BinanceProductFamily.COINM
        ? BinanceResilience.createRegistriesFuture()
        : BinanceResilience.createRegistries();
  }

  protected boolean isAuthenticated() {
    return exchangeSpecification != null
        && exchangeSpecification.getApiKey() != null
        && exchangeSpecification.getSecretKey() != null;
  }

  /** Adjust host parameters depending on exchange specific parameters */
  protected void concludeHostParams(ExchangeSpecification exchangeSpecification) {
    BinanceConfiguration config = configuration;
    if (config == null) {
      concludeHostParamsLegacy(exchangeSpecification);
      return;
    }
    switch (config.getProductFamily()) {
      case USDM:
        exchangeSpecification.setSslUri(
            config.isSandboxEnabled() ? SANDBOX_FUTURES_URL : FUTURES_URL);
        break;
      case COINM:
        exchangeSpecification.setSslUri(
            config.isSandboxEnabled() ? SANDBOX_INVERSE_FUTURES_URL : INVERSE_FUTURES_URL);
        break;
      case PORTFOLIO_MARGIN:
        exchangeSpecification.setSslUri(PORTFOLIO_MARGIN_URL);
        break;
      case SPOT:
      case WALLET_SAPI:
      case MARGIN:
        // Production URL stays as configured (Binance US keeps its own host); only the sandbox
        // URL is applied here.
        if (config.isSandboxEnabled()) {
          exchangeSpecification.setSslUri(SANDBOX_SPOT_URL);
        }
        break;
      default:
        throw new IllegalStateException(
            "Unsupported Binance product family: " + config.getProductFamily());
    }
    if (exchangeSpecification.getExchangeSpecificParametersItem(
            BinanceConfiguration.REST_BASE_URL)
        != null) {
      exchangeSpecification.setSslUri(config.getRestBaseUrl());
    }
  }

  /** Legacy mode selection preserved during the grace period. */
  private void concludeHostParamsLegacy(ExchangeSpecification exchangeSpecification) {
    if (exchangeSpecification.getExchangeSpecificParametersItem(EXCHANGE_TYPE) != null) {
      switch ((ExchangeType)
          exchangeSpecification.getExchangeSpecificParametersItem(EXCHANGE_TYPE)) {
        case SPOT:
          {
            if (enabledSandbox(exchangeSpecification)) {
              exchangeSpecification.setSslUri(SANDBOX_SPOT_URL);
            }
            break;
          }
        case FUTURES:
          {
            if (!enabledSandbox(exchangeSpecification)) {
              exchangeSpecification.setSslUri(FUTURES_URL);
            } else {
              exchangeSpecification.setSslUri(SANDBOX_FUTURES_URL);
            }
            break;
          }
        case INVERSE:
          {
            if (!enabledSandbox(exchangeSpecification)) {
              exchangeSpecification.setSslUri(INVERSE_FUTURES_URL);
            } else {
              exchangeSpecification.setSslUri(SANDBOX_INVERSE_FUTURES_URL);
            }
            break;
          }
        case PORTFOLIO_MARGIN:
          exchangeSpecification.setSslUri(PORTFOLIO_MARGIN_URL);
          break;
      }
    }
  }

  private static boolean enabledSandbox(ExchangeSpecification exchangeSpecification) {
    return Boolean.TRUE.equals(
        exchangeSpecification.getExchangeSpecificParametersItem(USE_SANDBOX));
  }
}
