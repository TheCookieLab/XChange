package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.knowm.xchange.okx.dto.OkxInstType.SPOT;
import static org.knowm.xchange.okx.dto.OkxInstType.SWAP;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;

public class OkxAdapterTest {
  @Test
  public void testAdaptTradingFee() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    InputStream is = OkxAdapterTest.class.getResourceAsStream("/getFeeRatesSpot.json5");
    OkxTradeFee okxSpotTradeFee =
        mapper
            .readValue(is, new TypeReference<OkxResponse<List<OkxTradeFee>>>() {})
            .getData()
            .get(0);
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("USDT/SGD")))
        .isEqualTo(new Fee(new BigDecimal("0.0005"), new BigDecimal("0.0007")));
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("OKB/BTC")))
        .isEqualTo(new Fee(new BigDecimal("0.0005"), new BigDecimal("0.0007")));
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("USDC/USDT")))
        .isEqualTo(new Fee(new BigDecimal("0.0008"), new BigDecimal("0.001")));
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("EUR/USDT")))
        .isEqualTo(new Fee(new BigDecimal("0.0008"), new BigDecimal("0.001")));

    is = OkxAdapterTest.class.getResourceAsStream("/getFeeRatesSwap.json5");
    OkxTradeFee okxSwapTradeFee =
        mapper
            .readValue(is, new TypeReference<OkxResponse<List<OkxTradeFee>>>() {})
            .getData()
            .get(0);
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, SWAP, new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, SWAP, new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, SWAP, new FuturesContract("USDC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    // currently no USD support in OKX swap
    //    assertThat(OkxAdapters.adaptTradingFee(okxSwapTradeFee,SWAP, new
    // FuturesContract("BTC/USD/SWAP")))
    //        .isEqualTo(new Fee(new BigDecimal("-0.0002"),new BigDecimal("-0.0005")));
  }
}
