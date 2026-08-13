package org.knowm.xchange.bybit.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.bybit.BybitAdapters;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.marketdata.BybitDeliveryPrice;
import org.knowm.xchange.bybit.dto.marketdata.BybitFundingRateHistoryRaw;
import org.knowm.xchange.bybit.dto.marketdata.BybitKlines;
import org.knowm.xchange.bybit.dto.marketdata.BybitOpenInterest;
import org.knowm.xchange.bybit.dto.marketdata.BybitOrderbook;
import org.knowm.xchange.bybit.dto.marketdata.BybitPublicTrade;
import org.knowm.xchange.bybit.dto.marketdata.BybitServerTime;
import org.knowm.xchange.bybit.dto.marketdata.instruments.BybitInstrumentInfo;
import org.knowm.xchange.bybit.dto.marketdata.instruments.BybitInstrumentsInfo;
import org.knowm.xchange.bybit.dto.marketdata.tickers.BybitTicker;
import org.knowm.xchange.bybit.dto.marketdata.tickers.BybitTickers;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;

public class BybitMarketDataServiceRaw extends BybitBaseService {

  public BybitMarketDataServiceRaw(
      BybitExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public BybitResult<BybitTickers<BybitTicker>> getTicker24h(BybitCategory category, String symbol)
      throws IOException {
    BybitResult<BybitTickers<BybitTicker>> result = bybit.getTicker24h(category.getValue(), symbol);

    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result;
  }

  /** Hard ceiling for catalog pagination: 200 pages x 1000 instruments per category. */
  private static final int MAX_INSTRUMENT_PAGES = 200;

  public BybitResult<BybitInstrumentsInfo<BybitInstrumentInfo>> getInstrumentsInfo(
      BybitCategory category) throws IOException {
    return getInstrumentsInfo(category, null);
  }

  /**
   * Fetches a single instruments-info page. Public so tests can exercise cursor chains one page at
   * a time; use {@link #getAllInstrumentsInfo(BybitCategory)} for a complete catalog.
   */
  public BybitResult<BybitInstrumentsInfo<BybitInstrumentInfo>> getInstrumentsInfo(
      BybitCategory category, String cursor) throws IOException {
    BybitResult<BybitInstrumentsInfo<BybitInstrumentInfo>> result =
        bybit.getInstrumentsInfo(category.getValue(), "1000", cursor);

    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result;
  }

  /**
   * Fetches the complete instrument catalog for a category by following the V5
   * {@code nextPageCursor} contract. Guards against runaway pagination: a page ceiling, a repeated
   * cursor, or a non-empty cursor with an empty page each abort with {@link
   * org.knowm.xchange.exceptions.ExchangeException} instead of looping forever.
   */
  public List<BybitInstrumentInfo> getAllInstrumentsInfo(BybitCategory category)
      throws IOException {
    List<BybitInstrumentInfo> instruments = new ArrayList<>();
    String cursor = null;
    for (int page = 0; page < MAX_INSTRUMENT_PAGES; page++) {
      BybitInstrumentsInfo<BybitInstrumentInfo> payload =
          getInstrumentsInfo(category, cursor).getResult();
      List<BybitInstrumentInfo> pageInstruments = payload.getList();
      instruments.addAll(pageInstruments);
      String nextCursor = payload.getNextPageCursor();
      if (nextCursor == null || nextCursor.isEmpty()) {
        return instruments;
      }
      if (nextCursor.equals(cursor)) {
        throw new ExchangeException(
            "Bybit instruments-info pagination repeated cursor '" + nextCursor + "' for category "
                + category + "; aborting to avoid an infinite loop");
      }
      if (pageInstruments.isEmpty()) {
        throw new ExchangeException(
            "Bybit instruments-info pagination made no progress for category " + category
                + " (empty page with cursor '" + nextCursor + "'); aborting");
      }
      cursor = nextCursor;
    }
    throw new ExchangeException(
        "Bybit instruments-info pagination exceeded " + MAX_INSTRUMENT_PAGES + " pages for category "
            + category + "; aborting");
  }

  public BybitResult<BybitCategorizedPayload<BybitPublicTrade>> getPublicTrades(
      BybitCategory category, String symbol, Integer limit) throws IOException {
    BybitResult<BybitCategorizedPayload<BybitPublicTrade>> result =
        bybit.getPublicTrades(category.getValue(), symbol, limit == null ? null : limit.toString());

    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result;
  }

  /**
   * Option/linear/inverse delivery-price history. Cursor-complete via {@link
   * BybitCategorizedPayload#getNextPageCursor()}.
   */
  public BybitResult<BybitCategorizedPayload<BybitDeliveryPrice>> getDeliveryPrice(
      BybitCategory category, String symbol, String baseCoin, Integer limit, String cursor)
      throws IOException {
    BybitResult<BybitCategorizedPayload<BybitDeliveryPrice>> result =
        bybit.getDeliveryPrice(
            category.getValue(),
            symbol,
            baseCoin,
            limit == null ? null : limit.toString(),
            cursor);
    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result;
  }

  public BybitServerTime getServerTime() throws IOException {
    BybitResult<BybitServerTime> result = bybit.getServerTime();
    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result.getResult();
  }

  public BybitOpenInterest getOpenInterest(
      BybitCategory category, String symbol, String intervalTime, Integer limit)
      throws IOException {
    BybitResult<BybitOpenInterest> result =
        bybit.getOpenInterest(
            category.getValue(), symbol, intervalTime, limit == null ? null : limit.toString());
    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result.getResult();
  }

  public BybitResult<BybitTickers<BybitTicker>> getTickers(BybitCategory category)
      throws IOException {
    BybitResult<BybitTickers<BybitTicker>> result = bybit.getTickers(category.getValue());

    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result;
  }

  public BybitResult<BybitOrderbook> getOrderbook(BybitCategory category, String symbol, int limit)
      throws IOException {
    BybitResult<BybitOrderbook> result =
        bybit.getOrderbook(category.getValue(), symbol, limit > 0 ? String.valueOf(limit) : null);

    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return result;
  }

  public CandleStickData getCandleStickDataRaw(
      BybitCategory category, String symbol, String interval, Long start, Long end, Integer limit)
      throws IOException {
    BybitResult<BybitKlines> result =
        bybit.getKlines(category.getValue(), symbol, interval, start, end, limit);
    if (!result.isSuccess()) {
      throw BybitAdapters.createBybitExceptionFromResult(result);
    }
    return BybitAdapters.adaptCandleStickData(result.getResult(), category);
  }

  public List<BybitFundingRateHistoryRaw> getFundingRateHistoryRaw(
      Instrument instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    return bybit
        .getFundingHistory(
            BybitAdapters.getCategory(instrument).getValue(),
            BybitAdapters.convertToBybitSymbol(instrument),
            startTime,
            endTime,
            limit)
        .getResult()
        .getList();
  }
}
