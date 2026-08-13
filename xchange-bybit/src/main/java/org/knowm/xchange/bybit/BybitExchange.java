package org.knowm.xchange.bybit;

import java.io.IOException;
import lombok.Getter;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.config.BybitConfiguration;
import org.knowm.xchange.bybit.config.BybitEnvironment;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;
import org.knowm.xchange.bybit.dto.marketdata.instruments.linear.BybitLinearInverseInstrumentInfo;
import org.knowm.xchange.bybit.dto.marketdata.instruments.option.BybitOptionInstrumentInfo;
import org.knowm.xchange.bybit.dto.marketdata.instruments.spot.BybitSpotInstrumentInfo;
import org.knowm.xchange.bybit.service.BybitAccountService;
import org.knowm.xchange.bybit.service.BybitMarketDataService;
import org.knowm.xchange.bybit.service.BybitMarketDataServiceRaw;
import org.knowm.xchange.bybit.service.BybitTradeService;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.utils.AuthUtils;
import si.mazi.rescu.SynchronizedValueFactory;

public class BybitExchange extends BaseExchange implements Exchange {

  public static final String SPECIFIC_PARAM_ACCOUNT_TYPE = "accountType";
  private static final String BASE_URL = "https://api.bybit.com";

  // enable TEST_NET
  public static final String SPECIFIC_PARAM_TESTNET = "test_net";

  private static ResilienceRegistries RESILIENCE_REGISTRIES;

  @Getter protected SynchronizedValueFactory<Long> timeStampFactory = new BybitTimeStampFactory();

  /**
   * Validated configuration resolved in {@link #applySpecification(ExchangeSpecification)} before
   * any service or transport is constructed; shared by the REST and streaming modules.
   */
  @Getter private BybitConfiguration configuration;

  @Override
  protected void initServices() {
    if (configuration == null) {
      // Defensive fallback for applySpecification(null), which merges the default specification
      // after initServices has already been invoked by the base class.
      configuration = BybitConfiguration.from(getExchangeSpecification());
    }
    marketDataService = new BybitMarketDataService(this, getResilienceRegistries());
    tradeService = new BybitTradeService(this, getResilienceRegistries());
    accountService =
        new BybitAccountService(this, configuration.getAccountType(), getResilienceRegistries());
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri(BASE_URL);
    exchangeSpecification.setHost("bybit.com");
    exchangeSpecification.setPort(80);
    exchangeSpecification.setExchangeName("Bybit");
    exchangeSpecification.setExchangeDescription("BYBIT");
    exchangeSpecification.setExchangeSpecificParametersItem(
        SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.UNIFIED);
    exchangeSpecification.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, false);
    exchangeSpecification.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, false);
    exchangeSpecification.getResilience().setRateLimiterEnabled(true);
    AuthUtils.setApiAndSecretKey(exchangeSpecification, "bybit");
    return exchangeSpecification;
  }

  @Override
  public void remoteInit() throws IOException, ExchangeException {
    ((BybitMarketDataServiceRaw) marketDataService)
        .getInstrumentsInfo(BybitCategory.SPOT)
        .getResult()
        .getList()
        .forEach(
            instrumentInfo ->
                exchangeMetaData
                    .getInstruments()
                    .put(
                        BybitAdapters.adaptInstrumentInfo(instrumentInfo),
                        BybitAdapters.symbolToCurrencyPairMetaData(
                            (BybitSpotInstrumentInfo) instrumentInfo)));

    ((BybitMarketDataServiceRaw) marketDataService)
        .getInstrumentsInfo(BybitCategory.LINEAR)
        .getResult()
        .getList()
        .forEach(
            instrumentInfo ->
                exchangeMetaData
                    .getInstruments()
                    .put(
                        BybitAdapters.adaptInstrumentInfo(instrumentInfo),
                        BybitAdapters.symbolToCurrencyPairMetaData(
                            (BybitLinearInverseInstrumentInfo) instrumentInfo)));

    ((BybitMarketDataServiceRaw) marketDataService)
        .getInstrumentsInfo(BybitCategory.INVERSE)
        .getResult()
        .getList()
        .forEach(
            instrumentInfo ->
                exchangeMetaData
                    .getInstruments()
                    .put(
                        BybitAdapters.adaptInstrumentInfo(instrumentInfo),
                        BybitAdapters.symbolToCurrencyPairMetaData(
                            (BybitLinearInverseInstrumentInfo) instrumentInfo)));

    ((BybitMarketDataServiceRaw) marketDataService)
        .getInstrumentsInfo(BybitCategory.OPTION)
        .getResult()
        .getList()
        .forEach(
            instrumentInfo ->
                exchangeMetaData
                    .getInstruments()
                    .put(
                        BybitAdapters.adaptInstrumentInfo(instrumentInfo),
                        BybitAdapters.symbolToCurrencyPairMetaData(
                            (BybitOptionInstrumentInfo) instrumentInfo)));
  }

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    if (exchangeSpecification != null) {
      // Resolve and validate the environment before the base class constructs services: a
      // contradictory demo/testnet combination or an unsupported account type fails here
      // instead of silently rerouting traffic.
      BybitConfiguration resolved = BybitConfiguration.from(exchangeSpecification);
      this.configuration = resolved;
      if (exchangeSpecification.getSslUri() == null
          || isDefaultBybitRestUrl(exchangeSpecification.getSslUri())) {
        // An explicitly configured sslUri (custom endpoint, test proxy) wins; otherwise the
        // environment contract selects the REST base URL.
        exchangeSpecification.setSslUri(resolved.getEnvironment().getRestBaseUrl());
      }
    }
    super.applySpecification(exchangeSpecification);
  }

  private static boolean isDefaultBybitRestUrl(String sslUri) {
    for (BybitEnvironment environment : BybitEnvironment.values()) {
      if (environment.getRestBaseUrl().equals(sslUri)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ResilienceRegistries getResilienceRegistries() {
    if (RESILIENCE_REGISTRIES == null) {
      RESILIENCE_REGISTRIES = BybitResilience.createRegistries();
    }
    return RESILIENCE_REGISTRIES;
  }

  @Override
  public SynchronizedValueFactory<Long> getNonceFactory() {
    throw new UnsupportedOperationException("Bybit uses timestamp/recv-window rather than a nonce");
  }
}
