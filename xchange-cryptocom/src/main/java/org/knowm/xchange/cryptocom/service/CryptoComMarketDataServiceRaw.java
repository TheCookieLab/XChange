package org.knowm.xchange.cryptocom.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComCandlestick;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComExpiredSettlementPrice;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComInstrument;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComOrderBookData;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComPublicTrade;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComRiskParameters;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComTicker;

/**
 * Raw public market-data service. Candles, expired-settlement reference prices and derivative risk
 * parameters are intentionally exposed as typed raw results only: they are reference data with no
 * lossless XChange core surface, and the provider's decimal-string numerics must not be rounded.
 */
public class CryptoComMarketDataServiceRaw extends CryptoComBaseService {

  /** Hard bound on cursor pages walked for public reference lists (defensive; official lists fit in a handful of pages). */
  static final int MAX_REFERENCE_PAGES = 20;

  protected CryptoComMarketDataServiceRaw(
      CryptoComExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public List<CryptoComInstrument> getCryptoComInstruments()
      throws IOException, CryptoComException {
    return getCryptoComInstruments(null);
  }

  /**
   * Fetch the full instrument list, honoring the official {@code next_cursor} continuation with a
   * hard page bound so a misbehaving cursor cannot loop forever.
   */
  public List<CryptoComInstrument> getCryptoComInstruments(String cursor)
      throws IOException, CryptoComException {
    java.util.ArrayList<CryptoComInstrument> all = new java.util.ArrayList<>();
    String currentCursor = cursor;
    int pages = 0;
    do {
      final String pageCursor = currentCursor;
      CryptoComResponse response =
          decorateApiCall(() -> cryptoCom.getInstruments(pageCursor)).call();
      all.addAll(getDataList(response, CryptoComInstrument.class));
      currentCursor = getNextCursor(response);
      pages++;
    } while (currentCursor != null && pages < MAX_REFERENCE_PAGES);
    return all;
  }

  /**
   * Candlestick history. Pass either {@code count} or the {@code startTs}/{@code endTs} window
   * (Unix milliseconds); both map verbatim to the official {@code count}/{@code start_ts}/{@code
   * end_ts} parameters. Returns candles in ascending time order.
   */
  public List<CryptoComCandlestick> getCryptoComCandles(
      String instrumentName, String timeframe, Integer count, Long startTs, Long endTs)
      throws IOException, CryptoComException {
    CryptoComResponse response =
        decorateApiCall(
                () -> cryptoCom.getCandlestick(instrumentName, timeframe, count, startTs, endTs))
            .call();
    return getDataList(response, CryptoComCandlestick.class);
  }

  /** Expired settlement reference prices for a dated instrument type (e.g. {@code FUTURE}). */
  public List<CryptoComExpiredSettlementPrice> getCryptoComExpiredSettlementPrices(
      String instrumentType, Integer page) throws IOException, CryptoComException {
    CryptoComResponse response =
        decorateApiCall(() -> cryptoCom.getExpiredSettlementPrice(instrumentType, page)).call();
    return getDataList(response, CryptoComExpiredSettlementPrice.class);
  }

  /** Smart Cross Margin derivative risk parameters (reference data; exact decimal strings). */
  public CryptoComRiskParameters getCryptoComRiskParameters()
      throws IOException, CryptoComException {
    CryptoComResponse response = decorateApiCall(cryptoCom::getRiskParameters).call();
    com.fasterxml.jackson.databind.JsonNode result = response.getResult();
    return result == null || result.isNull() || result.isMissingNode() ? null : toObject(result, CryptoComRiskParameters.class);
  }

  public CryptoComTicker getCryptoComTicker(String instrumentName)
      throws IOException, CryptoComException {
    CryptoComResponse response = decorateApiCall(() -> cryptoCom.getTickers(instrumentName)).call();
    List<CryptoComTicker> tickers = getDataList(response, CryptoComTicker.class);
    return tickers.isEmpty() ? null : tickers.get(0);
  }

  public List<CryptoComTicker> getCryptoComTickers() throws IOException, CryptoComException {
    CryptoComResponse response = decorateApiCall(() -> cryptoCom.getTickers(null)).call();
    return getDataList(response, CryptoComTicker.class);
  }

  public CryptoComOrderBookData getCryptoComOrderBook(String instrumentName, Integer depth)
      throws IOException, CryptoComException {
    CryptoComResponse response =
        decorateApiCall(() -> cryptoCom.getBook(instrumentName, depth)).call();
    List<CryptoComOrderBookData> data = getDataList(response, CryptoComOrderBookData.class);
    return data.isEmpty() ? null : data.get(0);
  }

  public List<CryptoComPublicTrade> getCryptoComTrades(String instrumentName, Integer count)
      throws IOException, CryptoComException {
    CryptoComResponse response =
        decorateApiCall(() -> cryptoCom.getPublicTrades(instrumentName, count)).call();
    return getDataList(response, CryptoComPublicTrade.class);
  }

  /** Reads the official {@code result.next_cursor} continuation token, if any. */
  private String getNextCursor(CryptoComResponse response) {
    if (response == null || response.getResult() == null || response.getResult().isNull()) {
      return null;
    }
    com.fasterxml.jackson.databind.JsonNode next = response.getResult().get("next_cursor");
    return next == null || next.isNull() || next.asText().isEmpty() ? null : next.asText();
  }
}