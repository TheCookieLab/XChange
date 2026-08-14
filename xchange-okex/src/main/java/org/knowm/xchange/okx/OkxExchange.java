package org.knowm.xchange.okx;

import static org.knowm.xchange.okx.OkxAdapters.adaptOkxInstrumentId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okx.dto.OkxInstType;
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
    OkxMarketDataServiceRaw marketDataServiceRaw = (OkxMarketDataServiceRaw) marketDataService;
    List<OkxInstrument> instruments =
        aggregateInstrumentFamilies(
            List.of(
                fetchInstruments(marketDataServiceRaw, OkxInstType.SPOT, null),
                fetchInstruments(marketDataServiceRaw, OkxInstType.SWAP, null),
                fetchInstruments(marketDataServiceRaw, OkxInstType.MARGIN, null),
                fetchInstruments(marketDataServiceRaw, OkxInstType.FUTURES, null),
                fetchOptionInstruments(marketDataServiceRaw)));

    // Currency data is only retrievable through a private endpoint
    List<OkxCurrency> currencies = null;
    if (exchangeSpecification.getApiKey() != null
        && exchangeSpecification.getSecretKey() != null
        && exchangeSpecification.getExchangeSpecificParametersItem("passphrase") != null) {
      currencies = marketDataServiceRaw.getOkxCurrencies().getData();
      accountLevel =
          ((OkxAccountService) accountService)
              .getOkxAccountConfiguration()
              .getData()
              .get(0)
              .getAccountLevel();
    }

    exchangeMetaData = OkxAdapters.adaptToExchangeMetaData(instruments, currencies);
  }

  /**
   * Fetches all instruments of one family, optionally restricted to a single underlying. All
   * families are served by the public instruments endpoint and require no credentials.
   */
  private static List<OkxInstrument> fetchInstruments(
      OkxMarketDataServiceRaw marketDataServiceRaw, OkxInstType instType, String underlying)
      throws IOException {
    return marketDataServiceRaw.getOkxInstruments(instType.name(), underlying, null).getData();
  }

  /**
   * Fetches all option instruments. OKX requires an underlying (or instrument family) when
   * querying OPTION, so every family reported by the public underlying endpoint is fetched in
   * turn. Uses only public market data and requires no credentials.
   */
  private static List<OkxInstrument> fetchOptionInstruments(
      OkxMarketDataServiceRaw marketDataServiceRaw) throws IOException {
    List<String> underlyings =
        marketDataServiceRaw.getOkxUnderlyings(OkxInstType.OPTION).getData();
    if (underlyings == null || underlyings.isEmpty()) {
      return Collections.emptyList();
    }
    List<OkxInstrument> optionInstruments = new ArrayList<>();
    for (String underlying : underlyings) {
      optionInstruments.addAll(
          fetchInstruments(marketDataServiceRaw, OkxInstType.OPTION, underlying));
    }
    return optionInstruments;
  }

  /**
   * Aggregates per-family instrument lists into a single de-duplicated list and populates the
   * instrument-id-code map used for order placement.
   *
   * <p>Instrument ids are unique across families except that MARGIN reuses the SPOT id of the same
   * pair; the first occurrence of each id wins, so SPOT entries take precedence and the id map is
   * collision-free. Package-private seam so the aggregation can be exercised offline with fixture
   * data; {@link #remoteInit()} feeds it the fetched family lists.
   *
   * @param instrumentFamilies one list per family, in the order they should take precedence
   * @return the de-duplicated instrument list in first-occurrence order
   */
  static List<OkxInstrument> aggregateInstrumentFamilies(
      List<List<OkxInstrument>> instrumentFamilies) {
    Map<String, OkxInstrument> byInstrumentId = new LinkedHashMap<>();
    for (List<OkxInstrument> family : instrumentFamilies) {
      if (family == null) {
        continue;
      }
      for (OkxInstrument instrument : family) {
        byInstrumentId.putIfAbsent(instrument.getInstrumentId(), instrument);
      }
    }
    List<OkxInstrument> instruments = new ArrayList<>(byInstrumentId.values());
    instruments.forEach(
        instrument -> {
          if (instrument.getInstIdCode() != null)
            OkxAdapters.instrumentToInstrumentIdMap.put(
                adaptOkxInstrumentId(instrument.getInstrumentId()),
                Long.parseLong(instrument.getInstIdCode()));
        });
    return instruments;
  }

  protected boolean useSandbox() {
    return Boolean.TRUE.equals(
        exchangeSpecification.getExchangeSpecificParametersItem(USE_SANDBOX));
  }
}
