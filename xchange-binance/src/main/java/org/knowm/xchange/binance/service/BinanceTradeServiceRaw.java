package org.knowm.xchange.binance.service;

import static org.knowm.xchange.binance.BinanceExchange.EXCHANGE_TYPE;
import static org.knowm.xchange.binance.BinanceResilience.ORDERS_PER_10_SECONDS_RATE_LIMITER;
import static org.knowm.xchange.binance.BinanceResilience.ORDERS_PER_MINUTE_RATE_LIMITER;
import static org.knowm.xchange.binance.BinanceResilience.ORDERS_PER_SECOND_RATE_LIMITER;
import static org.knowm.xchange.binance.BinanceResilience.RAW_REQUESTS_RATE_LIMITER;
import static org.knowm.xchange.binance.BinanceResilience.REQUEST_WEIGHT_RATE_LIMITER;
import static org.knowm.xchange.client.ResilienceRegistries.NON_IDEMPOTENT_CALLS_RETRY_CONFIG_NAME;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.BinanceException;
import org.knowm.xchange.binance.dto.account.futures.BinancePosition;
import org.knowm.xchange.binance.dto.trade.BinanceCancelledOrder;
import org.knowm.xchange.binance.dto.trade.BinanceDustLog;
import org.knowm.xchange.binance.dto.trade.BinanceListenKey;
import org.knowm.xchange.binance.dto.trade.BinanceNewOrder;
import org.knowm.xchange.binance.dto.trade.BinanceOrder;
import org.knowm.xchange.binance.dto.trade.BinanceTrade;
import org.knowm.xchange.binance.dto.trade.OrderSide;
import org.knowm.xchange.binance.dto.trade.OrderType;
import org.knowm.xchange.binance.dto.trade.TimeInForce;
import org.knowm.xchange.binance.dto.trade.futures.BinanceChangeStatus;
import org.knowm.xchange.binance.dto.trade.futures.BinanceFutureNewOrder;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;

public class BinanceTradeServiceRaw extends BinanceBaseService {

  protected BinanceTradeServiceRaw(
      BinanceExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public List<BinanceOrder> openOrdersAllProducts() throws BinanceException, IOException {
    switch (exchange
        .getExchangeSpecification()
        .getExchangeSpecificParametersItem(EXCHANGE_TYPE)
        .toString()) {
      case "FUTURES":
        return decorateApiCall(
                () ->
                    binanceUsdmAuth.futureOpenOrders(
                        null, getRecvWindow(), getTimestampFactory(), apiKey, signatureCreator))
            .withRetry(retry("openOrders"))
            .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), openOrdersPermits(null))
            .call();
      case "INVERSE":
        return decorateApiCall(
                () ->
                    binanceCoinmAuth.futureOpenInverseOrders(
                        null, getRecvWindow(), getTimestampFactory(), apiKey, signatureCreator))
            .withRetry(retry("openOrders"))
            .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), openOrdersPermits(null))
            .call();
      case "PORTFOLIO_MARGIN":
        return decorateApiCall(
                () ->
                    binancePortfolioMargin.futureOpenPortfolioMarginOrders(
                        null, getRecvWindow(), getTimestampFactory(), apiKey, signatureCreator))
            .withRetry(retry("openOrders"))
            .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), openOrdersPermits(null))
            .call();
      default: // i.e. SPOT
        return decorateApiCall(
                () ->
                    binanceSpotAuth.openOrders(
                        null, getRecvWindow(), getTimestampFactory(), apiKey, signatureCreator))
            .withRetry(retry("openOrders"))
            .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), openOrdersPermits(null))
            .call();
    }
  }

  public List<BinanceOrder> openOrdersAllProducts(Instrument pair)
      throws BinanceException, IOException {
    if (exchange.isPortfolioMarginEnabled()) {
      return decorateApiCall(
              () ->
                  (pair instanceof FuturesContract)
                      ? (BinanceAdapters.isInverse(pair)
                          ? binancePortfolioMargin.futureOpenPortfolioMarginInverseOrders(
                              Optional.of(pair).map(BinanceAdapters::toInverseSymbol).orElse(null),
                              getRecvWindow(),
                              getTimestampFactory(),
                              apiKey,
                              signatureCreator)
                          : binancePortfolioMargin.futureOpenPortfolioMarginOrders(
                              Optional.of(pair).map(BinanceAdapters::toSymbol).orElse(null),
                              getRecvWindow(),
                              getTimestampFactory(),
                              apiKey,
                              signatureCreator))
                      : binanceSpotAuth.openOrders(
                          Optional.ofNullable(pair).map(BinanceAdapters::toSymbol).orElse(null),
                          getRecvWindow(),
                          getTimestampFactory(),
                          apiKey,
                          signatureCreator))
          .withRetry(retry("openOrders"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), openOrdersPermits(pair))
          .call();
    } else {
      return decorateApiCall(
              () ->
                  (pair instanceof FuturesContract)
                      ? (BinanceAdapters.isInverse(pair)
                          ? binanceCoinmAuth.futureOpenInverseOrders(
                              Optional.of(pair).map(BinanceAdapters::toInverseSymbol).orElse(null),
                              getRecvWindow(),
                              getTimestampFactory(),
                              apiKey,
                              signatureCreator)
                          : binanceUsdmAuth.futureOpenOrders(
                              Optional.of(pair).map(BinanceAdapters::toSymbol).orElse(null),
                              getRecvWindow(),
                              getTimestampFactory(),
                              apiKey,
                              signatureCreator))
                      : binanceSpotAuth.openOrders(
                          Optional.ofNullable(pair).map(BinanceAdapters::toSymbol).orElse(null),
                          getRecvWindow(),
                          getTimestampFactory(),
                          apiKey,
                          signatureCreator))
          .withRetry(retry("openOrders"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), openOrdersPermits(pair))
          .call();
    }
  }

  public BinanceNewOrder newOrder(
      Instrument pair,
      OrderSide side,
      OrderType type,
      TimeInForce timeInForce,
      BigDecimal quantity,
      BigDecimal quoteOrderQty,
      BigDecimal price,
      String newClientOrderId,
      BigDecimal stopPrice,
      Long trailingDelta,
      BigDecimal icebergQty,
      BinanceNewOrder.NewOrderResponseType newOrderRespType)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binanceSpotAuth.newOrder(
                    BinanceAdapters.toSymbol(pair),
                    side,
                    type,
                    timeInForce,
                    quantity,
                    quoteOrderQty,
                    price,
                    newClientOrderId,
                    stopPrice,
                    trailingDelta,
                    icebergQty,
                    newOrderRespType,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("newOrder", NON_IDEMPOTENT_CALLS_RETRY_CONFIG_NAME))
        .withRateLimiter(rateLimiter(ORDERS_PER_SECOND_RATE_LIMITER))
        .withRateLimiter(rateLimiter(RAW_REQUESTS_RATE_LIMITER))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public BinanceFutureNewOrder newPortfolioMarginFutureOrder(
      Instrument pair,
      OrderSide side,
      OrderType type,
      TimeInForce timeInForce,
      BigDecimal quantity,
      boolean reduceOnly,
      BigDecimal price,
      String newClientOrderId,
      BinanceNewOrder.NewOrderResponseType newOrderRespType)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binancePortfolioMargin.newPortfolioMarginLinearOrder(
                    BinanceAdapters.toSymbol(pair),
                    side,
                    type,
                    timeInForce,
                    quantity,
                    reduceOnly,
                    price,
                    newClientOrderId,
                    newOrderRespType,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("newFutureOrder", NON_IDEMPOTENT_CALLS_RETRY_CONFIG_NAME))
        .withRateLimiter(rateLimiter(ORDERS_PER_SECOND_RATE_LIMITER))
        .withRateLimiter(rateLimiter(RAW_REQUESTS_RATE_LIMITER))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public BinanceFutureNewOrder newPortfolioMarginInverseFutureOrder(
      Instrument pair,
      OrderSide side,
      OrderType type,
      TimeInForce timeInForce,
      BigDecimal quantity,
      boolean reduceOnly,
      BigDecimal price,
      String newClientOrderId,
      BinanceNewOrder.NewOrderResponseType newOrderRespType)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binancePortfolioMargin.newPortfolioMarginInverseOrder(
                    BinanceAdapters.toSymbol(pair, true),
                    side,
                    type,
                    timeInForce,
                    quantity,
                    reduceOnly,
                    price,
                    newClientOrderId,
                    newOrderRespType,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("newFutureOrder", NON_IDEMPOTENT_CALLS_RETRY_CONFIG_NAME))
        .withRateLimiter(rateLimiter(ORDERS_PER_SECOND_RATE_LIMITER))
        .withRateLimiter(rateLimiter(RAW_REQUESTS_RATE_LIMITER))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public BinanceFutureNewOrder newFutureOrder(
      Instrument pair,
      OrderSide side,
      OrderType type,
      TimeInForce timeInForce,
      BigDecimal quantity,
      Boolean reduceOnly,
      BigDecimal price,
      String newClientOrderId,
      BigDecimal stopPrice,
      boolean closePosition,
      BigDecimal activationPrice,
      BigDecimal callbackRate,
      BinanceNewOrder.NewOrderResponseType newOrderRespType)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binanceUsdmAuth.newOrder(
                    BinanceAdapters.toSymbol(pair),
                    side,
                    type,
                    timeInForce,
                    quantity,
                    reduceOnly,
                    price,
                    newClientOrderId,
                    stopPrice,
                    closePosition,
                    activationPrice,
                    callbackRate,
                    newOrderRespType,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("newFutureOrder", NON_IDEMPOTENT_CALLS_RETRY_CONFIG_NAME))
        .withRateLimiter(rateLimiter(ORDERS_PER_10_SECONDS_RATE_LIMITER))
        .withRateLimiter(rateLimiter(ORDERS_PER_MINUTE_RATE_LIMITER))
        .call();
  }

  public BinanceFutureNewOrder newInverseFutureOrder(
      Instrument pair,
      OrderSide side,
      OrderType type,
      TimeInForce timeInForce,
      BigDecimal quantity,
      boolean reduceOnly,
      BigDecimal price,
      String newClientOrderId,
      BigDecimal stopPrice,
      boolean closePosition,
      BigDecimal activationPrice,
      BigDecimal callbackRate,
      BinanceNewOrder.NewOrderResponseType newOrderRespType)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binanceCoinmAuth.newInverseOrder(
                    BinanceAdapters.toSymbol(pair, true),
                    side,
                    type,
                    timeInForce,
                    quantity,
                    reduceOnly,
                    price,
                    newClientOrderId,
                    stopPrice,
                    closePosition,
                    activationPrice,
                    callbackRate,
                    newOrderRespType,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("newFutureOrder", NON_IDEMPOTENT_CALLS_RETRY_CONFIG_NAME))
        .withRateLimiter(rateLimiter(ORDERS_PER_SECOND_RATE_LIMITER))
        .withRateLimiter(rateLimiter(RAW_REQUESTS_RATE_LIMITER))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public void testNewOrder(
      Instrument pair,
      OrderSide side,
      OrderType type,
      TimeInForce timeInForce,
      BigDecimal quantity,
      BigDecimal quoteOrderQty,
      BigDecimal price,
      String newClientOrderId,
      BigDecimal stopPrice,
      Long trailingDelta,
      BigDecimal icebergQty)
      throws IOException, BinanceException {
    decorateApiCall(
            () ->
                binanceSpotAuth.testNewOrder(
                    BinanceAdapters.toSymbol(pair),
                    side,
                    type,
                    timeInForce,
                    quantity,
                    quoteOrderQty,
                    price,
                    newClientOrderId,
                    stopPrice,
                    trailingDelta,
                    icebergQty,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("testNewOrder"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public BinanceOrder orderStatusAllProducts(
      Instrument pair, Long orderId, String origClientOrderId)
      throws IOException, BinanceException {
    if (exchange.isPortfolioMarginEnabled()) {

      return decorateApiCall(
              () ->
                  (pair instanceof FuturesContract)
                      ? (BinanceAdapters.isInverse(pair)
                          ? binancePortfolioMargin.futurePortfolioMarginInverseOrderStatus(
                              BinanceAdapters.toInverseSymbol(pair),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator)
                          : binancePortfolioMargin.futurePortfolioMarginOrderStatus(
                              BinanceAdapters.toSymbol(pair),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator))
                      : binanceSpotAuth.orderStatus(
                          BinanceAdapters.toSymbol(pair),
                          orderId,
                          origClientOrderId,
                          getRecvWindow(),
                          getTimestampFactory(),
                          super.apiKey,
                          super.signatureCreator))
          .withRetry(retry("orderStatus"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
          .call();
    } else {
      return decorateApiCall(
              () ->
                  (pair instanceof FuturesContract)
                      ? (BinanceAdapters.isInverse(pair)
                          ? binanceCoinmAuth.futureInverseOrderStatus(
                              BinanceAdapters.toInverseSymbol(pair),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator)
                          : binanceUsdmAuth.futureOrderStatus(
                              BinanceAdapters.toSymbol(pair),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator))
                      : binanceSpotAuth.orderStatus(
                          BinanceAdapters.toSymbol(pair),
                          orderId,
                          origClientOrderId,
                          getRecvWindow(),
                          getTimestampFactory(),
                          super.apiKey,
                          super.signatureCreator))
          .withRetry(retry("orderStatus"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
          .call();
    }
  }

  public BinanceCancelledOrder cancelOrderAllProducts(
      Instrument pair, Long orderId, String origClientOrderId, String newClientOrderId)
      throws IOException, BinanceException {
    if (exchange.isPortfolioMarginEnabled()) {

      return decorateApiCall(
              () ->
                  (pair instanceof FuturesContract)
                      ? (BinanceAdapters.isInverse(pair)
                          ? binancePortfolioMargin.cancelPortfolioMarginInverseFutureOrder(
                              BinanceAdapters.toSymbol(pair, true),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator)
                          : binancePortfolioMargin.cancelPortfolioMarginFutureOrder(
                              BinanceAdapters.toSymbol(pair),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator))
                      : binanceSpotAuth.cancelOrder(
                          BinanceAdapters.toSymbol(pair),
                          orderId,
                          origClientOrderId,
                          newClientOrderId,
                          getRecvWindow(),
                          getTimestampFactory(),
                          super.apiKey,
                          super.signatureCreator))
          .withRetry(retry("cancelOrder"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
          .call();
    } else {
      return decorateApiCall(
              () ->
                  (pair instanceof FuturesContract)
                      ? (BinanceAdapters.isInverse(pair)
                          ? binanceCoinmAuth.cancelInverseFutureOrder(
                              BinanceAdapters.toSymbol(pair, true),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator)
                          : binanceUsdmAuth.cancelFutureOrder(
                              BinanceAdapters.toSymbol(pair),
                              orderId,
                              origClientOrderId,
                              getRecvWindow(),
                              getTimestampFactory(),
                              super.apiKey,
                              super.signatureCreator))
                      : binanceSpotAuth.cancelOrder(
                          BinanceAdapters.toSymbol(pair),
                          orderId,
                          origClientOrderId,
                          newClientOrderId,
                          getRecvWindow(),
                          getTimestampFactory(),
                          super.apiKey,
                          super.signatureCreator))
          .withRetry(retry("cancelOrder"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
          .call();
    }
  }

  public List<BinanceCancelledOrder> cancelAllOpenOrdersAllProducts(Instrument pair)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binanceSpotAuth.cancelAllOpenOrders(
                    BinanceAdapters.toSymbol(pair),
                    getRecvWindow(),
                    getTimestampFactory(),
                    super.apiKey,
                    super.signatureCreator))
        .withRetry(retry("cancelAllOpenOrders"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public BinanceChangeStatus cancelAllOpenOrdersAllFuturesProducts(Instrument pair)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binanceUsdmAuth.cancelAllFutureOpenOrders(
                    BinanceAdapters.toSymbol(pair),
                    getRecvWindow(),
                    getTimestampFactory(),
                    super.apiKey,
                    super.signatureCreator))
        .withRetry(retry("cancelAllOpenOrders"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public List<BinanceOrder> allOrders(CurrencyPair pair, Long orderId, Integer limit)
      throws BinanceException, IOException {
    return decorateApiCall(
            () ->
                binanceSpotAuth.allOrders(
                    BinanceAdapters.toSymbol(pair),
                    orderId,
                    limit,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("allOrders"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public List<BinanceTrade> myTradesAllProducts(
      Instrument pair, Long orderId, Long startTime, Long endTime, Long fromId, Integer limit)
      throws BinanceException, IOException {
    if (pair instanceof FuturesContract) {
      return decorateApiCall(
              () ->
                  binanceUsdmAuth.myFutureTrades(
                      BinanceAdapters.toSymbol(pair),
                      orderId,
                      startTime,
                      endTime,
                      fromId,
                      limit,
                      getRecvWindow(),
                      getTimestampFactory(),
                      apiKey,
                      signatureCreator))
          .withRetry(retry("myTrades"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), 5)
          .call();
    } else {
      return decorateApiCall(
              () ->
                  binanceSpotAuth.myTrades(
                      BinanceAdapters.toSymbol(pair),
                      orderId,
                      startTime,
                      endTime,
                      fromId,
                      limit,
                      getRecvWindow(),
                      getTimestampFactory(),
                      apiKey,
                      signatureCreator))
          .withRetry(retry("myTrades"))
          .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), orderId != null ? 5 : 20)
          .call();
    }
  }

  public List<BinancePosition> openPositions() throws BinanceException, IOException {
    return openPositions(false);
  }

  public List<BinancePosition> openPositions(boolean useV3) throws BinanceException, IOException {
    return decorateApiCall(
            () ->
                useV3
                    ? binanceUsdmAuth.futuresV3Account(
                        getRecvWindow(), getTimestampFactory(), apiKey, signatureCreator)
                    : binanceUsdmAuth.futuresAccount(
                        getRecvWindow(), getTimestampFactory(), apiKey, signatureCreator))
        .withRetry(retry("futures-account"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), 5)
        .call()
        .getPositions();
  }

  /**
   * Retrieves the dust log from Binance. If you have many currencies with low amount (=dust) that
   * cannot be traded, because their amount is less than the minimum amount required for trading
   * them, you can convert all these currencies at once into BNB with the button "Convert Small
   * Balance to BNB".
   *
   * @param startTime optional. If set, also the endTime must be set. If neither time is set, the
   *     100 most recent dust logs are returned.
   * @param endTime optional. If set, also the startTime must be set. If neither time is set, the
   *     100 most recent dust logs are returned.
   * @return
   * @throws IOException
   */
  public BinanceDustLog getDustLog(Long startTime, Long endTime) throws IOException {

    if (((startTime != null) && (endTime == null)) || (startTime == null) && (endTime != null)) {
      throw new ExchangeException(
          "You need to specify both, the start and the end date, or none of them");
    }

    return decorateApiCall(
            () ->
                binanceWallet.getDustLog(
                    startTime,
                    endTime,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("myDustLog"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public BinanceListenKey startUserDataStream() throws IOException {
    return decorateApiCall(() -> binanceSpotAuth.startUserDataStream(apiKey))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public void keepAliveDataStream(String listenKey) throws IOException {
    decorateApiCall(() -> binanceSpotAuth.keepAliveUserDataStream(apiKey, listenKey))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  public void closeDataStream(String listenKey) throws IOException {
    decorateApiCall(() -> binanceSpotAuth.closeUserDataStream(apiKey, listenKey))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER))
        .call();
  }

  protected int openOrdersPermits(Instrument pair) {
    if (exchange
        .getExchangeSpecification()
        .getExchangeSpecificParametersItem(EXCHANGE_TYPE)
        .equals("SPOT")) return pair != null ? 6 : 80;
    else // FUTURES,INVERSE and MARGIN
    return pair != null ? 1 : 40;
  }

  //  protected int myTradesPermits(Integer limit) {
  //    if (limit != null && limit > 500) {
  //      return 10;
  //    }
  //    return 5;
  //  }

  public BinanceFutureNewOrder modifyOrder(
      Long orderId,
      String origClientOrderId,
      Instrument instrument,
      OrderSide side,
      BigDecimal quantity,
      BigDecimal price)
      throws IOException {
    return decorateApiCall(
            () ->
                binanceUsdmAuth.modifyOrder(
                    orderId,
                    origClientOrderId,
                    BinanceAdapters.toSymbol(instrument, false),
                    side,
                    quantity,
                    price,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("modifyOrder"))
        .withRateLimiter(rateLimiter(ORDERS_PER_10_SECONDS_RATE_LIMITER))
        .withRateLimiter(rateLimiter(ORDERS_PER_MINUTE_RATE_LIMITER))
        .call();
  }

  public List<BinancePosition> getFuturesPositionRisk(Instrument instrument)
      throws IOException, BinanceException {
    return getFuturesPositionRisk(instrument, false);
  }

  public List<BinancePosition> getFuturesPositionRisk(Instrument instrument, boolean useV3)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                useV3
                    ? binanceUsdmAuth.getFuturesV3PositionRisk(
                        BinanceAdapters.toSymbol(instrument),
                        getRecvWindow(),
                        getTimestampFactory(),
                        apiKey,
                        signatureCreator)
                    : binanceUsdmAuth.getFuturesPositionRisk(
                        BinanceAdapters.toSymbol(instrument),
                        getRecvWindow(),
                        getTimestampFactory(),
                        apiKey,
                        signatureCreator))
        .withRetry(retry("futuresPositionRisk"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), 5)
        .call();
  }

  public List<BinanceOrder> getAllFutureOrders(
      Instrument instrument, Long orderId, long startTime, long endTime, int limit)
      throws IOException, BinanceException {
    return decorateApiCall(
            () ->
                binanceUsdmAuth.getAllFutureOrders(
                    BinanceAdapters.toSymbol(instrument),
                    orderId,
                    startTime,
                    endTime,
                    limit,
                    getRecvWindow(),
                    getTimestampFactory(),
                    apiKey,
                    signatureCreator))
        .withRetry(retry("getAllFutureOrders"))
        .withRateLimiter(rateLimiter(REQUEST_WEIGHT_RATE_LIMITER), 5)
        .call();
  }
}
