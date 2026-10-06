package org.knowm.xchange.kucoin;

import static org.knowm.xchange.kucoin.KucoinExceptionClassifier.classifyingExceptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.dto.KlineIntervalType;
import org.knowm.xchange.kucoin.dto.response.AllTickersResponse;
import org.knowm.xchange.kucoin.dto.response.CurrenciesResponse;
import org.knowm.xchange.kucoin.dto.response.CurrencyResponseV2;
import org.knowm.xchange.kucoin.dto.response.KucoinCurrencyResponseV3;
import org.knowm.xchange.kucoin.dto.response.KucoinKline;
import org.knowm.xchange.kucoin.dto.response.OrderBookResponse;
import org.knowm.xchange.kucoin.dto.response.SymbolResponse;
import org.knowm.xchange.kucoin.dto.response.SymbolTickResponse;
import org.knowm.xchange.kucoin.dto.response.TickerResponse;
import org.knowm.xchange.kucoin.dto.response.TradeFeeResponse;
import org.knowm.xchange.kucoin.dto.response.TradeHistoryResponse;

public class KucoinMarketDataServiceRaw extends KucoinBaseService {

  protected KucoinMarketDataServiceRaw(
      KucoinExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public TickerResponse getKucoinTicker(CurrencyPair pair) throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(() -> symbolApi.getTicker(KucoinAdapters.adaptCurrencyPair(pair)))
                .withRetry(retry("ticker"))
                .call());
  }

  public AllTickersResponse getKucoinTickers() throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(symbolApi::getTickers)
                .withRetry(retry("tickers"))
                .call());
  }

  public SymbolTickResponse getKucoin24hrStats(CurrencyPair pair) throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(() -> symbolApi.getMarketStats(KucoinAdapters.adaptCurrencyPair(pair)))
                .withRetry(retry("24hrStats"))
                .call());
  }

  public Map<String, BigDecimal> getKucoinPrices() throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(symbolApi::getPrices)
                .withRetry(retry("prices"))
                .call());
  }

  public TradeFeeResponse getKucoinBaseFee() throws IOException {
    checkAuthenticated();
    return classifyingExceptions(
        () ->
            decorateApiCall(
                    () -> tradingFeeAPI.getBaseFee(apiKey, digest, nonceFactory, passphrase))
                .withRetry(retry("baseFee"))
                .call());
  }

  public List<TradeFeeResponse> getKucoinTradeFee(String symbols) throws IOException {
    checkAuthenticated();
    return classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        tradingFeeAPI.getTradeFee(
                            apiKey, digest, nonceFactory, passphrase, symbols))
                .withRetry(retry("tradeFee"))
                .call());
  }

  /**
   * @deprecated use {@link #getKucoinSymbolsV2()}
   */
  @Deprecated
  public List<SymbolResponse> getKucoinSymbols() throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(symbolApi::getSymbols)
                .withRetry(retry("symbols"))
                .call());
  }

  public List<SymbolResponse> getKucoinSymbolsV2() throws IOException {
    return decorateApiCall(symbolApi::getSymbolsV2)
        .withRetry(retry("symbols"))
        .call()
        .getData();
  }

  public List<CurrenciesResponse> getKucoinCurrencies() throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(symbolApi::getCurrencies)
                .withRetry(retry("currencies"))
                .call());
  }

  public CurrencyResponseV2 getKucoinCurrencies(Currency currency) throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(() -> symbolApi.getCurrencies(currency.getCurrencyCode()))
                .withRetry(retry("currencies"))
                .call());
  }

  public List<KucoinCurrencyResponseV3> getAllKucoinCurrencies() throws IOException {
    return decorateApiCall(symbolApi::getAllCurrencies)
        .withRetry(retry("currencies"))
        .call()
        .getData();
  }

  public OrderBookResponse getKucoinOrderBookPartial(Instrument instrument) throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        orderBookApi.getPartOrderBookAggregated(
                            KucoinAdapters.adaptCurrencyPair(instrument)))
                .withRetry(retry("partialOrderBook"))
                .call());
  }

  public OrderBookResponse getKucoinOrderBookPartialShallow(Instrument instrument)
      throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        orderBookApi.getPartOrderBookShallowAggregated(
                            KucoinAdapters.adaptCurrencyPair(instrument)))
                .withRetry(retry("partialShallowOrderBook"))
                .call());
  }

  public OrderBookResponse getKucoinOrderBookFull(Instrument instrument) throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(
                    () ->
                        orderBookApi.getFullOrderBookAggregated(
                            KucoinAdapters.adaptCurrencyPair(instrument),
                            apiKey,
                            digest,
                            nonceFactory,
                            passphrase))
                .withRetry(retry("fullOrderBook"))
                .call());
  }

  public List<TradeHistoryResponse> getKucoinTrades(CurrencyPair pair) throws IOException {
    return classifyingExceptions(
        () ->
            decorateApiCall(
                    () -> historyApi.getTradeHistories(KucoinAdapters.adaptCurrencyPair(pair)))
                .withRetry(retry("tradeHistories"))
                .call());
  }

  public List<KucoinKline> getKucoinKlines(
      CurrencyPair pair, Long startTime, Long endTime, KlineIntervalType type) throws IOException {
    List<Object[]> raw =
        classifyingExceptions(
            () ->
                decorateApiCall(
                        () ->
                            historyApi.getKlines(
                                KucoinAdapters.adaptCurrencyPair(pair),
                                startTime,
                                endTime,
                                type.code()))
                    .withRetry(retry("klines"))
                    .call());

    return raw.stream().map(obj -> new KucoinKline(pair, type, obj)).collect(Collectors.toList());
  }
}
