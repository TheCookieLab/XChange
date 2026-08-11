package org.knowm.xchange.bitget;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitget.config.BitgetApiMode;
import org.knowm.xchange.bitget.config.BitgetConfiguration;
import org.knowm.xchange.bitget.dto.marketdata.BitgetSymbolDto;
import org.knowm.xchange.bitget.service.BitgetAccountService;
import org.knowm.xchange.bitget.service.BitgetMarketDataService;
import org.knowm.xchange.bitget.service.BitgetMarketDataServiceRaw;
import org.knowm.xchange.bitget.service.BitgetTradeService;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3AccountService;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3MarketDataService;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;

/**
 * Bitget exchange entry point.
 *
 * <p>The configured {@link BitgetApiMode} selects the API/account generation. The default is {@link
 * BitgetApiMode#CLASSIC_V2}, preserving the historical Spot/Futures behavior of this class. Select
 * {@link BitgetApiMode#UTA_V3} through the {@link BitgetConfiguration#API_MODE} exchange-specific
 * parameter to use the Unified Trading Account (UTA) v3 implementation.
 *
 * <p>Classic and UTA credentials are not interchangeable: routing UTA credentials through the
 * classic mode (or vice versa) fails before any trading request with an actionable diagnostic from
 * the mode-specific authentication path.
 */
public class BitgetExchange extends BaseExchange {

  private BitgetConfiguration configuration;

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    this.configuration = BitgetConfiguration.from(exchangeSpecification);
    super.applySpecification(exchangeSpecification);
  }

  @Override
  protected void initServices() {
    switch (getApiMode()) {
      case CLASSIC_V2:
        accountService = new BitgetAccountService(this);
        marketDataService = new BitgetMarketDataService(this);
        tradeService = new BitgetTradeService(this);
        break;
      case UTA_V3:
        accountService = new BitgetUtaV3AccountService(this);
        marketDataService = new BitgetUtaV3MarketDataService(this);
        tradeService = new BitgetUtaV3TradeService(this);
        break;
      default:
        throw new IllegalStateException("Unknown Bitget API mode: " + getApiMode());
    }
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification specification = new ExchangeSpecification(getClass());
    specification.setSslUri("https://api.bitget.com");
    specification.setHost("www.bitget.com");
    specification.setExchangeName("Bitget");
    specification.setExchangeSpecificParametersItem(
        BitgetConfiguration.API_MODE, BitgetApiMode.CLASSIC_V2);
    return specification;
  }

  /** The typed Bitget configuration of this instance (never {@code null} after setup). */
  public BitgetConfiguration getConfiguration() {
    return configuration;
  }

  /** The API/account mode of this instance (defaults to {@link BitgetApiMode#CLASSIC_V2}). */
  public BitgetApiMode getApiMode() {
    return configuration.getApiMode();
  }

  @Override
  public void remoteInit() throws IOException {
    switch (getApiMode()) {
      case CLASSIC_V2:
        remoteInitClassic();
        break;
      case UTA_V3:
        remoteInitUtaV3();
        break;
      default:
        throw new IllegalStateException("Unknown Bitget API mode: " + getApiMode());
    }
  }

  private void remoteInitClassic() throws IOException {
    BitgetMarketDataServiceRaw bitgetMarketDataServiceRaw =
        (BitgetMarketDataServiceRaw) marketDataService;

    // initialize symbol mappings
    List<BitgetSymbolDto> bitgetSymbolDtos = bitgetMarketDataServiceRaw.getBitgetSymbolDtos(null);
    bitgetSymbolDtos.forEach(
        bitgetSymbolDto -> {
          BitgetAdapters.putSymbolMapping(
              bitgetSymbolDto.getSymbol(), bitgetSymbolDto.getCurrencyPair());
        });

    // initialize instrument metadata
    Map<Instrument, InstrumentMetaData> instruments =
        bitgetSymbolDtos.stream()
            .collect(
                Collectors.toMap(
                    BitgetSymbolDto::getCurrencyPair, BitgetAdapters::toInstrumentMetaData));

    exchangeMetaData = new ExchangeMetaData(instruments, null, null, null, null);
  }

  private void remoteInitUtaV3() throws IOException {
    BitgetUtaV3MarketDataService marketData = (BitgetUtaV3MarketDataService) marketDataService;
    exchangeMetaData = marketData.buildExchangeMetaData();
  }
}
