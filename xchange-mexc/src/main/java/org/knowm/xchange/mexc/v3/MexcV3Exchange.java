package org.knowm.xchange.mexc.v3;

import java.io.IOException;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.config.MexcV3Configuration;
import org.knowm.xchange.mexc.v3.dto.marketdata.MexcV3ExchangeInfo;
import org.knowm.xchange.mexc.v3.service.MexcV3AccountService;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataService;
import org.knowm.xchange.mexc.v3.service.MexcV3TradeService;
import org.knowm.xchange.utils.AuthUtils;

/**
 * MEXC Spot v3 exchange ({@code https://api.mexc.com}, {@code /api/v3}).
 *
 * <p>This is the current MEXC integration entry point. The legacy Spot v2 adapter
 * ({@code org.knowm.xchange.mexc.MEXCExchange}) is frozen for compatibility and deprecated; see
 * the xchange-mexc README for the migration notes.
 */
public class MexcV3Exchange extends MexcV3BaseExchange {

  private MexcV3Configuration configuration;

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification spec = super.getDefaultExchangeSpecification();
    spec.setExchangeDescription("MEXC Spot v3 Exchange (api.mexc.com /api/v3).");
    AuthUtils.setApiAndSecretKey(spec, "mexc");
    return spec;
  }

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    this.configuration = MexcV3Configuration.from(exchangeSpecification);
    Object restBaseUrl =
        exchangeSpecification.getParameter(MexcV3Configuration.REST_BASE_URL_KEY);
    if (restBaseUrl != null) {
      // The explicit REST base URL parameter wins over the default and drives HTTP routing.
      exchangeSpecification.setSslUri(restBaseUrl.toString());
    }
    concludeHostParams(exchangeSpecification);
    super.applySpecification(exchangeSpecification);
  }

  @Override
  protected void initServices() {
    marketDataService = new MexcV3MarketDataService(this);
    accountService = new MexcV3AccountService(this);
    tradeService = new MexcV3TradeService(this);
  }

  @Override
  public void remoteInit() {
    try {
      MexcV3ExchangeInfo exchangeInfo =
          ((MexcV3MarketDataService) marketDataService).getExchangeInfo();
      exchangeMetaData = MexcV3Adapters.adaptExchangeInfo(exchangeInfo);
    } catch (IOException | MexcV3Exception e) {
      throw new ExchangeException("Failed to initialize MEXC Spot v3 metadata", e);
    }
  }

  /**
   * The typed configuration in effect for this exchange instance.
   *
   * @throws IllegalStateException when called before
   *     {@link #applySpecification(ExchangeSpecification)} has run.
   */
  public MexcV3Configuration getConfiguration() {
    if (configuration == null) {
      throw new IllegalStateException(
          "MexcV3Exchange configuration is not initialized; applySpecification must run first.");
    }
    return configuration;
  }
}
