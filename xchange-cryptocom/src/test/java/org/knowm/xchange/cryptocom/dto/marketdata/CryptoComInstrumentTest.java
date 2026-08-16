package org.knowm.xchange.cryptocom.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;

public class CryptoComInstrumentTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testUnmarshalInstruments() throws IOException {
    // given
    String resource = "/org/knowm/xchange/cryptocom/dto/marketdata/get-instruments.json";

    // when
    CryptoComResponse response =
        CryptoComTestSupport.readResponse(CryptoComInstrumentTest.class, resource, objectMapper);
    List<CryptoComInstrument> instruments =
        CryptoComTestSupport.readDataList(response, objectMapper, CryptoComInstrument.class);

    // then
    assertThat(response.getId()).isEqualTo(1);
    assertThat(response.getMethod()).isEqualTo("public/get-instruments");
    assertThat(response.getCode()).isEqualTo(0);

    assertThat(instruments).hasSize(7);

    CryptoComInstrument btcUsdt = instruments.get(1);
    assertThat(btcUsdt.getSymbol()).isEqualTo("BTC_USDT");
    assertThat(btcUsdt.getInstType()).isEqualTo("CCY_PAIR");
    assertThat(btcUsdt.getBaseCurrency()).isEqualTo("BTC");
    assertThat(btcUsdt.getQuoteCurrency()).isEqualTo("USDT");
    assertThat(btcUsdt.getQuoteDecimals()).isEqualTo(2);
    assertThat(btcUsdt.getQuantityDecimals()).isEqualTo(5);
    assertThat(btcUsdt.getPriceTickSize()).isEqualTo("0.01");
    assertThat(btcUsdt.getQtyTickSize()).isEqualTo("0.00001");
    assertThat(btcUsdt.getMaxLeverage()).isEqualTo("50");
    assertThat(btcUsdt.getTradable()).isTrue();
    assertThat(btcUsdt.isCcPair()).isTrue();
    assertThat(btcUsdt.isDerivative()).isFalse();
    assertThat(btcUsdt.getIdentity()).isNull();
    assertThat(btcUsdt.getSettlementCurrency()).isEqualTo("USDT");
    assertThat(btcUsdt.getMarginTradingEligibility()).isEqualTo(new boolean[] {true, true});
  }

  @Test
  public void testDerivativeInstrumentIdentity() throws IOException {
    // given
    String resource = "/org/knowm/xchange/cryptocom/dto/marketdata/get-instruments.json";

    // when
    CryptoComResponse response =
        CryptoComTestSupport.readResponse(CryptoComInstrumentTest.class, resource, objectMapper);
    List<CryptoComInstrument> instruments =
        CryptoComTestSupport.readDataList(response, objectMapper, CryptoComInstrument.class);

    // then -- perpetual swap
    CryptoComInstrument perpetual = instruments.get(3);
    assertThat(perpetual.getSymbol()).isEqualTo("1INCHUSD-PERP");
    assertThat(perpetual.getInstType()).isEqualTo("PERPETUAL_SWAP");
    assertThat(perpetual.getUnderlyingSymbol()).isEqualTo("1INCHUSD-INDEX");
    assertThat(perpetual.getContractSize()).isEqualTo("1");
    assertThat(perpetual.isPerpetual()).isTrue();
    assertThat(perpetual.isDerivative()).isTrue();
    assertThat(perpetual.getIdentity().getProductType())
        .isEqualTo(CryptoComInstrumentIdentity.ProductType.PERPETUAL_SWAP);
    assertThat(perpetual.getIdentity().getBaseCurrency()).isEqualTo("1INCH");
    assertThat(perpetual.getIdentity().getQuoteCurrency()).isEqualTo("USD");
    assertThat(perpetual.getIdentity().getExpiry()).isNull();
    assertThat(perpetual.getMarginTradingEligibility()).isEqualTo(new boolean[] {false, false});

    // then -- ETHUSD perpetual
    CryptoComInstrument ethPerp = instruments.get(4);
    assertThat(ethPerp.getSymbol()).isEqualTo("ETHUSD-PERP");
    assertThat(ethPerp.getMaxLeverage()).isEqualTo("75");
    assertThat(ethPerp.getLastUpdatedTime()).isEqualTo(1750000000000L);
    assertThat(ethPerp.getIdentity().getProductType())
        .isEqualTo(CryptoComInstrumentIdentity.ProductType.PERPETUAL_SWAP);

    // then -- dated future
    CryptoComInstrument future = instruments.get(5);
    assertThat(future.getSymbol()).isEqualTo("BTCUSD-250627");
    assertThat(future.getInstType()).isEqualTo("FUTURE");
    assertThat(future.getExpiryTimestampMs()).isEqualTo(1750982400000L);
    assertThat(future.isFuture()).isTrue();
    CryptoComInstrumentIdentity futureIdentity = future.getIdentity();
    assertThat(futureIdentity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.FUTURE);
    assertThat(futureIdentity.getExpiry()).isEqualTo("250627");
    assertThat(futureIdentity.getBaseCurrency()).isEqualTo("BTC");

    // then -- option
    CryptoComInstrument option = instruments.get(6);
    assertThat(option.getSymbol()).isEqualTo("BTCUSD-250627-60000-C");
    assertThat(option.getInstType()).isEqualTo("OPTION");
    assertThat(option.getExpiryTimestampMs()).isEqualTo(1750982400000L);
    assertThat(option.isOption()).isTrue();
    CryptoComInstrumentIdentity optionIdentity = option.getIdentity();
    assertThat(optionIdentity.getProductType()).isEqualTo(CryptoComInstrumentIdentity.ProductType.OPTION);
    assertThat(optionIdentity.getExpiry()).isEqualTo("250627");
    assertThat(optionIdentity.getStrikePrice()).isEqualTo("60000");
    assertThat(optionIdentity.getOptionSide()).isEqualTo('C');
    assertThat(optionIdentity.isOption()).isTrue();
    assertThat(option.getMarginTradingEligibility()).isEqualTo(new boolean[] {false, true});
  }
}