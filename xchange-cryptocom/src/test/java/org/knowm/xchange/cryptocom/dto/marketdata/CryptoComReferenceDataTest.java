package org.knowm.xchange.cryptocom.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;

/** Unmarshal tests for the Phase-2 reference-data public endpoints. */
public class CryptoComReferenceDataTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void candlestickRowsKeepExactDecimalStrings() throws IOException {
    // when
    CryptoComResponse response =
        CryptoComTestSupport.readResponse(
            CryptoComReferenceDataTest.class,
            "/org/knowm/xchange/cryptocom/dto/marketdata/get-candlestick.json",
            objectMapper);
    List<CryptoComCandlestick> candles =
        CryptoComTestSupport.readDataList(response, objectMapper, CryptoComCandlestick.class);

    // then
    assertThat(candles).hasSize(3);
    CryptoComCandlestick first = candles.get(0);
    assertThat(first.getStartTimeMs()).isEqualTo("1771761038000");
    assertThat(first.getOpen()).isEqualTo("67123.45");
    assertThat(first.getHigh()).isEqualTo("67145.00");
    assertThat(first.getLow()).isEqualTo("67080.10");
    assertThat(first.getClose()).isEqualTo("67110.22");
    assertThat(first.getVolume()).isEqualTo("12.50000000");
    assertThat(candles.get(2).getClose()).isEqualTo("67231.10");
  }

  @Test
  public void expiredSettlementPrices() throws IOException {
    // when
    CryptoComResponse response =
        CryptoComTestSupport.readResponse(
            CryptoComReferenceDataTest.class,
            "/org/knowm/xchange/cryptocom/dto/marketdata/get-expired-settlement-price.json",
            objectMapper);
    List<CryptoComExpiredSettlementPrice> prices =
        CryptoComTestSupport.readDataList(response, objectMapper, CryptoComExpiredSettlementPrice.class);

    // then
    assertThat(prices).hasSize(2);
    CryptoComExpiredSettlementPrice future = prices.get(0);
    assertThat(future.getInstrumentName()).isEqualTo("BTCUSD-250627");
    assertThat(future.getExpiryTimestampMs()).isEqualTo("1750982400000");
    assertThat(future.getValue()).isEqualTo("62450.11");
    assertThat(future.getTimestampMs()).isEqualTo("1750982400100");
  }

  @Test
  public void riskParametersKeepExactReferences() throws IOException {
    // when
    CryptoComResponse response =
        CryptoComTestSupport.readResponse(
            CryptoComReferenceDataTest.class,
            "/org/knowm/xchange/cryptocom/dto/marketdata/get-risk-parameters.json",
            objectMapper);
    CryptoComRiskParameters risk =
        objectMapper.convertValue(response.getResult(), CryptoComRiskParameters.class);

    // then
    assertThat(risk.getDefaultMaxProductLeverageForPerps()).isEqualTo("100");
    assertThat(risk.getDefaultUmrMultiplierForPerps()).isEqualTo("0.5");
    assertThat(risk.getUpdateTimestampMs()).isEqualTo("1771764000000");
    assertThat(risk.getBaseCurrencyConfig()).hasSize(2);
    CryptoComRiskParameters.BaseCurrencyConfig btc = risk.getBaseCurrencyConfig().get(0);
    assertThat(btc.getInstrumentName()).isEqualTo("BTC");
    assertThat(btc.getCollateralCapNotional()).isEqualTo("500000");
    assertThat(btc.getMinimumHaircut()).isEqualTo("0.01");
    assertThat(btc.getCollateralWeight()).isEqualTo("0.98");
  }
}