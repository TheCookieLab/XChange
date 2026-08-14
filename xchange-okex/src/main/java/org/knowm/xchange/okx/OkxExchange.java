package org.knowm.xchange.okx;

import static org.knowm.xchange.okx.OkxAdapters.adaptOkxInstrumentId;
import static org.knowm.xchange.okx.dto.OkxInstType.SPOT;
import static org.knowm.xchange.okx.dto.OkxInstType.SWAP;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okx.dto.marketdata.OkxCurrency;
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;
import org.knowm.xchange.okx.service.OkxAccountService;
import org.knowm.xchange.okx.service.OkxMarketDataService;
import org.knowm.xchange.okx.service.OkxMarketDataServiceRaw;
import org.knowm.xchange.okx.service.OkxTradeService;
import si.mazi.rescu.SynchronizedValueFactory;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxExchange extends BaseExchange {

  /**
   * @deprecated Not supported anymore. Retained only for source compatibility; the parameter is
   *     ignored.
   */
  @Deprecated public static final String PARAM_USE_AWS = "Use_AWS";

  public static final String PARAM_SIMULATED = "simulated";
  public static final String PARAM_PASSPHRASE = "passphrase";
  private static ResilienceRegistries RESILIENCE_REGISTRIES;

  public String accountLevel = "1";

  /** Adjust host parameters depending on exchange specific parameters */
  protected void concludeHostParams(ExchangeSpecification exchangeSpecification) {}

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    super.applySpecification(exchangeSpecification);
    concludeHostParams(exchangeSpecification);
  }

  @Override
  protected void initServices() {
    concludeHostParams(exchangeSpecification);

    this.marketDataService = new OkxMarketDataService(this, getResilienceRegistries());
    this.accountService = new OkxAccountService(this, getResilienceRegistries());
    this.tradeService = new OkxTradeService(this, getResilienceRegistries());
  }

  /**
   * For Demo Trading add the following param to exchangeSpecification:
   * exchangeSpecification.setExchangeSpecificParametersItem(PARAM_SIMULATED_TRADING, "1");
   */
  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {

    ExchangeSpecification exchangeSpecification = new ExchangeSpecification(this.getClass());
    exchangeSpecification.setSslUri("https://www.okx.com");
    exchangeSpecification.setHost("okx.com");
    exchangeSpecification.setPort(80);
    exchangeSpecification.setExchangeName("OKX");
    exchangeSpecification.setExchangeDescription("OKX Exchange");
    // not supported anymore
    exchangeSpecification.setExchangeSpecificParametersItem(PARAM_USE_AWS, false);
    return exchangeSpecification;
  }

  @Override
  public SynchronizedValueFactory<Long> getNonceFactory() {
    throw new UnsupportedOperationException(
        "Okx uses timestamp rather than a nonce"); // TODO: This
  }

  @Override
  public ResilienceRegistries getResilienceRegistries() {
    if (RESILIENCE_REGISTRIES == null) {
      RESILIENCE_REGISTRIES = OkxResilience.createRegistries();
    }
    return RESILIENCE_REGISTRIES;
  }

  @Override
  public void remoteInit() throws IOException {
    List<OkxInstrument> instruments =
        ((OkxMarketDataServiceRaw) marketDataService)
            .getOkxInstruments(SPOT.name(), null, null)
            .getData();

    List<OkxInstrument> swap_instruments =
        ((OkxMarketDataServiceRaw) marketDataService)
            .getOkxInstruments(SWAP.name(), null, null)
            .getData();

    instruments.addAll(swap_instruments);

    instruments.forEach(
        instrument -> {
          if (instrument.getInstIdCode() != null)
            OkxAdapters.instrumentToInstrumentIdMap.put(
                adaptOkxInstrumentId(instrument.getInstrumentId()),
                Long.parseLong(instrument.getInstIdCode()));
        });
    // Currency data is only retrievable through a private endpoint
    List<OkxCurrency> currencies = null;
    if (exchangeSpecification.getApiKey() != null
        && exchangeSpecification.getSecretKey() != null
        && exchangeSpecification.getExchangeSpecificParametersItem("passphrase") != null) {
      currencies = ((OkxMarketDataServiceRaw) marketDataService).getOkxCurrencies().getData();
      accountLevel =
          ((OkxAccountService) accountService)
              .getOkxAccountConfiguration()
              .getData()
              .get(0)
              .getAccountLevel();
    }

    exchangeMetaData = OkxAdapters.adaptToExchangeMetaData(instruments, currencies);
  }

  protected boolean useSandbox() {
    return Boolean.TRUE.equals(
        exchangeSpecification.getExchangeSpecificParametersItem(USE_SANDBOX));
  }
}
