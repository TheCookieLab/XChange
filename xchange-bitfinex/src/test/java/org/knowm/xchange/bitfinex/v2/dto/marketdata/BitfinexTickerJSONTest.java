package org.knowm.xchange.bitfinex.v2.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.bitfinex.service.BitfinexAdapters;
import org.knowm.xchange.dto.marketdata.Ticker;

public class BitfinexTickerJSONTest {
  @Test
  public void testUnmarshal() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        BitfinexTickerJSONTest.class.getResourceAsStream(
            "/org/knowm/xchange/bitfinex/v2/dto/marketdata/example-ticker-data.json");

    BitfinexTicker[] tickers = readTickers(is);

    // Verify that the example data was unmarshalled correctly
    // funding currency:
    BitfinexTickerFundingCurrency bitfinexTicker = (BitfinexTickerFundingCurrency) tickers[0];
    assertThat(bitfinexTicker.getSymbol()).isEqualTo("fLEO");
    assertThat(bitfinexTicker.getFrr()).isEqualTo("1.0958904109589042e-08");
    assertThat(bitfinexTicker.getBid()).isEqualTo("0");
    assertThat(bitfinexTicker.getBidPeriod()).isEqualTo("0");
    assertThat(bitfinexTicker.getBidSize()).isEqualTo("0");
    assertThat(bitfinexTicker.getAsk()).isEqualByComparingTo("1e-08");
    assertThat(bitfinexTicker.getAskPeriod()).isEqualTo("2");
    assertThat(bitfinexTicker.getAskSize()).isEqualTo("2663861.8810786298");
    assertThat(bitfinexTicker.getDailyChange()).isEqualTo("0");
    assertThat(bitfinexTicker.getDailyChangePerc()).isEqualTo("0");
    assertThat(bitfinexTicker.getLastPrice()).isEqualByComparingTo("1e-08");
    assertThat(bitfinexTicker.getVolume()).isEqualTo("664.1085");
    assertThat(bitfinexTicker.getHigh()).isEqualByComparingTo("1e-08");
    assertThat(bitfinexTicker.getLow()).isEqualByComparingTo("1e-08");
    assertThat(bitfinexTicker.getPlaceHolder0()).isNull();
    assertThat(bitfinexTicker.getPlaceHolder1()).isNull();
    assertThat(bitfinexTicker.getFrrAmountAvailable()).isEqualTo("2594257.74114297");
    assertThat(bitfinexTicker.getTimestamp()).isEqualTo(1469734163000L);

    // trading pair:
    BitfinexTickerTraidingPair bitfinexTicker2 = (BitfinexTickerTraidingPair) tickers[1];
    assertThat(bitfinexTicker2.getSymbol()).isEqualTo("tBTCUSD");
    assertThat(bitfinexTicker2.getBid()).isEqualTo("7381.6");
    assertThat(bitfinexTicker2.getBidSize()).isEqualTo("38.644979070000005");
    assertThat(bitfinexTicker2.getAsk()).isEqualTo("7381.7");
    assertThat(bitfinexTicker2.getAskSize()).isEqualByComparingTo("32.145906579999995");
    assertThat(bitfinexTicker2.getDailyChange()).isEqualTo("126.6");
    assertThat(bitfinexTicker2.getDailyChangePerc()).isEqualTo("0.0175");
    assertThat(bitfinexTicker2.getLastPrice()).isEqualByComparingTo("7381.2");
    assertThat(bitfinexTicker2.getVolume()).isEqualTo("1982.88275223");
    assertThat(bitfinexTicker2.getHigh()).isEqualByComparingTo("7390");
    assertThat(bitfinexTicker2.getLow()).isEqualByComparingTo("7228.1");
    assertThat(bitfinexTicker2.getTimestamp()).isEqualTo(1358182043000L);

    Ticker ticker = BitfinexAdapters.adaptTicker(bitfinexTicker2);
    assertThat(ticker.getTimestamp()).isEqualTo(new Date(1358182043000L));
  }

  @Test
  public void legacyRowsWithoutTimestampDefaultToNoTimestamp() throws IOException {

    BitfinexTicker[] tickers =
        readTickers(
            "["
                + "[\"fLEO\",1.0958904109589042e-8,0,0,0,1e-8,2,2663861.8810786298,0,0,"
                + "1e-8,664.1085,1e-8,1e-8,null,null,2594257.74114297],"
                + "[\"tBTCUSD\",7381.6,38.644979070000005,7381.7,32.145906579999995,126.6,"
                + "0.0175,7381.2,1982.88275223,7390,7228.1]"
                + "]");

    BitfinexTickerFundingCurrency fundingTicker =
        (BitfinexTickerFundingCurrency) tickers[0];
    assertThat(fundingTicker.getTimestamp()).isNull();

    BitfinexTickerTraidingPair tradingTicker = (BitfinexTickerTraidingPair) tickers[1];
    assertThat(tradingTicker.getTimestamp()).isNull();

    Ticker ticker = BitfinexAdapters.adaptTicker(tradingTicker);
    assertThat(ticker.getTimestamp()).isNull();
  }

  @Test
  public void legacyImplementationsDefaultToNoTimestamp() {

    BitfinexTicker ticker =
        new BitfinexTicker() {
          @Override
          public String getSymbol() {
            return "tBTCUSD";
          }

          @Override
          public BigDecimal getBid() {
            return null;
          }

          @Override
          public BigDecimal getBidSize() {
            return null;
          }

          @Override
          public BigDecimal getAsk() {
            return null;
          }

          @Override
          public BigDecimal getAskSize() {
            return null;
          }

          @Override
          public BigDecimal getDailyChange() {
            return null;
          }

          @Override
          public BigDecimal getDailyChangePerc() {
            return null;
          }

          @Override
          public BigDecimal getLastPrice() {
            return null;
          }

          @Override
          public BigDecimal getVolume() {
            return null;
          }

          @Override
          public BigDecimal getHigh() {
            return null;
          }

          @Override
          public BigDecimal getLow() {
            return null;
          }
        };

    assertThat(ticker.getTimestamp()).isNull();
  }

  private static BitfinexTicker[] readTickers(InputStream json) throws IOException {

    ObjectMapper mapper = new ObjectMapper();
    List<ArrayNode> tickerRows = mapper.readValue(json, tickerRowsType(mapper));
    return BitfinexAdapters.adoptBitfinexTickers(tickerRows);
  }

  private static BitfinexTicker[] readTickers(String json) throws IOException {

    ObjectMapper mapper = new ObjectMapper();
    List<ArrayNode> tickerRows = mapper.readValue(json, tickerRowsType(mapper));
    return BitfinexAdapters.adoptBitfinexTickers(tickerRows);
  }

  private static CollectionType tickerRowsType(ObjectMapper mapper) {

    return mapper.getTypeFactory().constructCollectionType(List.class, ArrayNode.class);
  }
}
