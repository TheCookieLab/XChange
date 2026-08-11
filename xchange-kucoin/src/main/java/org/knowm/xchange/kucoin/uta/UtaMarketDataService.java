package org.knowm.xchange.kucoin.uta;

import static org.knowm.xchange.kucoin.uta.UtaResilience.UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER;
import static org.knowm.xchange.kucoin.uta.service.UtaExceptionClassifier.callOrThrow;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.dto.UtaInstrument;
import org.knowm.xchange.kucoin.uta.dto.UtaInstrumentList;
import org.knowm.xchange.kucoin.uta.dto.UtaKlineList;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderBook;
import org.knowm.xchange.kucoin.uta.dto.UtaTicker;
import org.knowm.xchange.kucoin.uta.dto.UtaTickerList;
import org.knowm.xchange.kucoin.uta.dto.UtaTrade;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeList;
import org.knowm.xchange.kucoin.uta.service.UtaMarketAPI;
import org.knowm.xchange.service.marketdata.MarketDataService;

/**
 * UTA market data service: instrument catalog, tickers, order books, klines, and public trades.
 *
 * <p>The provider's public endpoints require no authentication, so public initialization never
 * needs private credentials.
 */
public class UtaMarketDataService extends UtaBaseService implements MarketDataService {

  private final UtaMarketAPI marketApi;

  public UtaMarketDataService(KucoinExchange exchange, ResilienceRegistries registries) {
    super(exchange, registries);
    this.marketApi = service(UtaMarketAPI.class);
  }

  // ---- raw API ---------------------------------------------------------------

  /** Full instrument catalog for a trade type ({@code SPOT} or {@code FUTURES}). */
  public List<UtaInstrument> getUtaInstruments(String tradeType) throws IOException {
    UtaInstrumentList result =
        callOrThrow(
            () ->
                decorateApiCall(() -> marketApi.getInstruments(tradeType, null))
                    .withRetry(retry("utaInstruments"))
                    .withRateLimiter(rateLimiter(UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER))
                    .call(),
            UtaDomains.MARKET,
            "GET /api/ua/v1/market/instrument");
    return result == null || result.getList() == null
        ? java.util.Collections.emptyList()
        : result.getList();
  }

  public UtaTickerList getUtaTickers(String tradeType, String symbol) throws IOException {
    return callOrThrow(
        () ->
            decorateApiCall(() -> marketApi.getTickers(tradeType, symbol))
                .withRetry(retry("utaTickers"))
                .withRateLimiter(rateLimiter(UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.MARKET,
        "GET /api/ua/v1/market/ticker");
  }

  public UtaOrderBook getUtaOrderBook(String tradeType, String symbol, String limit)
      throws IOException {
    return callOrThrow(
        () ->
            decorateApiCall(() -> marketApi.getOrderBook(tradeType, symbol, limit, null))
                .withRetry(retry("utaOrderBook"))
                .withRateLimiter(rateLimiter(UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.MARKET,
        "GET /api/ua/v1/market/orderbook");
  }

  public UtaKlineList getUtaKlines(
      String tradeType, String symbol, String interval, Long startAtSeconds, Long endAtSeconds)
      throws IOException {
    return callOrThrow(
        () ->
            decorateApiCall(
                    () -> marketApi.getKlines(tradeType, symbol, interval, startAtSeconds, endAtSeconds))
                .withRetry(retry("utaKlines"))
                .withRateLimiter(rateLimiter(UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER))
                .call(),
        UtaDomains.MARKET,
        "GET /api/ua/v1/market/kline");
  }

  public List<UtaTrade> getUtaTrades(String tradeType, String symbol) throws IOException {
    UtaTradeList result =
        callOrThrow(
            () ->
                decorateApiCall(() -> marketApi.getTrades(tradeType, symbol))
                    .withRetry(retry("utaTrades"))
                    .withRateLimiter(rateLimiter(UTA_PUBLIC_REST_ENDPOINT_RATE_LIMITER))
                    .call(),
            UtaDomains.MARKET,
            "GET /api/ua/v1/market/trade");
    return result == null || result.getList() == null
        ? java.util.Collections.emptyList()
        : result.getList();
  }

  // ---- high-level XChange API ------------------------------------------------

  /** Instrument-aware ticker supporting both spot and futures instruments. */
  public Ticker getUtaTicker(Instrument instrument) throws IOException {
    String tradeType = UtaTradeTypes.of(instrument);
    String symbol = exchange.getUtaProviderSymbol(instrument);
    UtaTickerList tickers = getUtaTickers(tradeType, symbol);
    UtaTicker ticker =
        tickers == null || tickers.getList() == null || tickers.getList().isEmpty()
            ? null
            : tickers.getList().get(0);
    if (ticker == null) {
      throw new java.util.NoSuchElementException("No UTA ticker for " + symbol);
    }
    return UtaAdapters.adaptTicker(instrument, ticker);
  }

  @Override
  public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
    return getUtaTicker(currencyPair);
  }

  /** Full aggregated order book for an instrument. */
  public OrderBook getUtaOrderBook(Instrument instrument) throws IOException {
    String tradeType = UtaTradeTypes.of(instrument);
    String symbol = exchange.getUtaProviderSymbol(instrument);
    UtaOrderBook book = getUtaOrderBook(tradeType, symbol, "FULL");
    return UtaAdapters.adaptOrderBook(instrument, book);
  }

  @Override
  public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
    return getUtaOrderBook(currencyPair);
  }

  @Override
  public Trades getTrades(CurrencyPair currencyPair, Object... args) throws IOException {
    return getUtaTrades(currencyPair);
  }

  public Trades getUtaTrades(Instrument instrument) throws IOException {
    String tradeType = UtaTradeTypes.of(instrument);
    String symbol = exchange.getUtaProviderSymbol(instrument);
    return UtaAdapters.adaptTrades(instrument, getUtaTrades(tradeType, symbol));
  }
}
