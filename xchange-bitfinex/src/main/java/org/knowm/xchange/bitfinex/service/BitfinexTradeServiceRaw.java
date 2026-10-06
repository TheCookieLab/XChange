package org.knowm.xchange.bitfinex.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.dto.BitfinexException;
import org.knowm.xchange.bitfinex.v1.BitfinexOrderType;
import org.knowm.xchange.bitfinex.v1.BitfinexUtils;
import org.knowm.xchange.bitfinex.v1.dto.account.BitfinexWithdrawalRequest;
import org.knowm.xchange.bitfinex.v1.dto.account.BitfinexWithdrawalResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexAccountInfosResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexActiveCreditsRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexActivePositionsResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexCancelAllOrdersRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexCancelOfferRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexCancelOrderMultiRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexCancelOrderRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexCreditResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexFundingTradeResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexLimitOrder;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexNewOfferRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexNewOrder;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexNewOrderMultiRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexNewOrderMultiResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexNewOrderRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexNonceOnlyRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOfferStatusRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOfferStatusResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrderFlags;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrderStatusRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrderStatusResponse;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrdersHistoryRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexPastFundingTradesRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexPastTradesRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexReplaceOrderRequest;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexTradeResponse;
import org.knowm.xchange.bitfinex.v2.dto.EmptyRequest;
import org.knowm.xchange.bitfinex.v2.dto.trade.BitfinexOpenOrdersRequest;
import org.knowm.xchange.bitfinex.v2.dto.trade.BitfinexOrderDetails;
import org.knowm.xchange.bitfinex.v2.dto.trade.BitfinexPosition;
import org.knowm.xchange.bitfinex.v2.dto.trade.BitfinexTrade;
import org.knowm.xchange.bitfinex.v2.dto.trade.OrderTrade;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.FixedRateLoanOrder;
import org.knowm.xchange.dto.trade.FloatingRateLoanOrder;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;

public class BitfinexTradeServiceRaw extends BitfinexBaseService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public BitfinexTradeServiceRaw(
      BitfinexExchange exchange, ResilienceRegistries resilienceRegistries) {

    super(exchange, resilienceRegistries);
  }

  public BitfinexAccountInfosResponse[] getBitfinexAccountInfos() throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.accountInfos(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNonceOnlyRequest("/v1/account_infos")))
        .call();
  }

  public BitfinexOrderStatusResponse[] getBitfinexOpenOrders() throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.activeOrders(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNonceOnlyRequest("/v1/orders")))
        .call();
  }

  public BitfinexOrderStatusResponse[] getBitfinexOrdersHistory(long limit) throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.ordersHist(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexOrdersHistoryRequest(limit)))
        .call();
  }

  public BitfinexOfferStatusResponse[] getBitfinexOpenOffers() throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.activeOffers(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNonceOnlyRequest("/v1/offers")))
        .call();
  }

  public BitfinexOrderStatusResponse placeBitfinexMarketOrder(
      MarketOrder marketOrder, BitfinexOrderType bitfinexOrderType) throws IOException {

    String pair = BitfinexUtils.toPairStringV1(marketOrder.getCurrencyPair());
    String type =
        (marketOrder.getType().equals(OrderType.BID)
                || marketOrder.getType().equals(OrderType.EXIT_ASK))
            ? "buy"
            : "sell";
    String orderType = bitfinexOrderType.toString();

    return decorateApiCall(
            () ->
                bitfinex.newOrder(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNewOrderRequest(
                        pair,
                        marketOrder.getOriginalAmount(),
                        BigDecimal.ONE,
                        "bitfinex",
                        type,
                        orderType,
                        null)))
        .call();
  }

  public BitfinexOrderStatusResponse placeBitfinexLimitOrder(
      LimitOrder limitOrder, BitfinexOrderType orderType) throws IOException {

    return sendLimitOrder(limitOrder, orderType, Long.MIN_VALUE);
  }

  public BitfinexOrderStatusResponse replaceBitfinexLimitOrder(
      LimitOrder limitOrder, BitfinexOrderType orderType, long replaceOrderId) throws IOException {
    if (limitOrder instanceof BitfinexLimitOrder
        && ((BitfinexLimitOrder) limitOrder).getOcoStopLimit() != null) {
      throw new ExchangeException("OCO orders are not yet editable");
    }
    return sendLimitOrder(limitOrder, orderType, replaceOrderId);
  }

  private BitfinexOrderStatusResponse sendLimitOrder(
      LimitOrder limitOrder, BitfinexOrderType bitfinexOrderType, long replaceOrderId)
      throws IOException {

    String pair = BitfinexUtils.toPairStringV1(limitOrder.getCurrencyPair());
    String type =
        (limitOrder.getType().equals(Order.OrderType.BID)
                || limitOrder.getType().equals(Order.OrderType.EXIT_ASK))
            ? "buy"
            : "sell";
    String orderType = bitfinexOrderType.toString();

    boolean isHidden;
    if (limitOrder.hasFlag(BitfinexOrderFlags.HIDDEN)) {
      isHidden = true;
    } else {
      isHidden = false;
    }
    boolean isPostOnly;
    if (limitOrder.hasFlag(BitfinexOrderFlags.POST_ONLY)) {
      isPostOnly = true;
    } else {
      isPostOnly = false;
    }

    BitfinexOrderStatusResponse response;
    if (replaceOrderId == Long.MIN_VALUE) { // order entry
      BigDecimal ocoAmount =
          limitOrder instanceof BitfinexLimitOrder
              ? ((BitfinexLimitOrder) limitOrder).getOcoStopLimit()
              : null;
      BitfinexNewOrderRequest request =
          new BitfinexNewOrderRequest(
              pair,
              limitOrder.getOriginalAmount(),
              limitOrder.getLimitPrice(),
              "bitfinex",
              type,
              orderType,
              isHidden,
              isPostOnly,
              ocoAmount);
      response =
          decorateApiCall(
                  () -> bitfinex.newOrder(apiKey, payloadCreator, signatureCreator, request))
              .call();

    } else { // order amend
      boolean useRemaining = limitOrder.hasFlag(BitfinexOrderFlags.USE_REMAINING);

      BitfinexReplaceOrderRequest request =
          new BitfinexReplaceOrderRequest(
              replaceOrderId,
              pair,
              limitOrder.getOriginalAmount(),
              limitOrder.getLimitPrice(),
              "bitfinex",
              type,
              orderType,
              isHidden,
              isPostOnly,
              useRemaining);
      response =
          decorateApiCall(
                  () -> bitfinex.replaceOrder(apiKey, payloadCreator, signatureCreator, request))
              .call();
    }

    if (limitOrder instanceof BitfinexLimitOrder) {
      BitfinexLimitOrder bitfinexOrder = (BitfinexLimitOrder) limitOrder;
      bitfinexOrder.setResponse(response);
    }
    return response;
  }

  public BitfinexNewOrderMultiResponse placeBitfinexOrderMulti(
      List<? extends Order> orders, BitfinexOrderType bitfinexOrderType) throws IOException {

    BitfinexNewOrder[] bitfinexOrders = new BitfinexNewOrder[orders.size()];

    for (int i = 0; i < bitfinexOrders.length; i++) {
      Order o = orders.get(i);
      if (o instanceof LimitOrder) {
        LimitOrder limitOrder = (LimitOrder) o;
        String pair = BitfinexUtils.toPairStringV1(limitOrder.getCurrencyPair());
        String type =
            (limitOrder.getType().equals(OrderType.BID)
                    || limitOrder.getType().equals(OrderType.EXIT_ASK))
                ? "buy"
                : "sell";
        String orderType = bitfinexOrderType.toString();
        bitfinexOrders[i] =
            new BitfinexNewOrder(
                pair,
                "bitfinex",
                type,
                orderType,
                limitOrder.getOriginalAmount(),
                limitOrder.getLimitPrice());
      } else if (o instanceof MarketOrder) {
        MarketOrder marketOrder = (MarketOrder) o;
        String pair = BitfinexUtils.toPairStringV1(marketOrder.getCurrencyPair());
        String type =
            (marketOrder.getType().equals(OrderType.BID)
                    || marketOrder.getType().equals(OrderType.EXIT_ASK))
                ? "buy"
                : "sell";
        String orderType = bitfinexOrderType.toString();
        bitfinexOrders[i] =
            new BitfinexNewOrder(
                pair, "bitfinex", type, orderType, marketOrder.getOriginalAmount(), BigDecimal.ONE);
      }
    }

    BitfinexNewOrderMultiRequest request = new BitfinexNewOrderMultiRequest(bitfinexOrders);
    return decorateApiCall(
            () -> bitfinex.newOrderMulti(apiKey, payloadCreator, signatureCreator, request))
        .call();
  }

  public BitfinexOfferStatusResponse placeBitfinexFixedRateLoanOrder(
      FixedRateLoanOrder loanOrder, BitfinexOrderType orderType) throws IOException {
    String direction = loanOrder.getType() == OrderType.BID ? "loan" : "lend";
    return decorateApiCall(
            () ->
                bitfinex.newOffer(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNewOfferRequest(
                        loanOrder.getCurrency(),
                        loanOrder.getOriginalAmount(),
                        loanOrder.getRate(),
                        loanOrder.getDayPeriod(),
                        direction)))
        .call();
  }

  public BitfinexOfferStatusResponse placeBitfinexFloatingRateLoanOrder(
      FloatingRateLoanOrder loanOrder, BitfinexOrderType orderType) throws IOException {

    String direction = loanOrder.getType() == OrderType.BID ? "loan" : "lend";

    return decorateApiCall(
            () ->
                bitfinex.newOffer(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNewOfferRequest(
                        loanOrder.getCurrency(),
                        loanOrder.getOriginalAmount(),
                        new BigDecimal("0.0"),
                        loanOrder.getDayPeriod(),
                        direction)))
        .call();
  }

  public boolean cancelBitfinexOrder(String orderId) throws IOException {

    try {
      decorateApiCall(
              () ->
                  bitfinex.cancelOrders(
                      apiKey,
                      payloadCreator,
                      signatureCreator,
                      new BitfinexCancelOrderRequest(Long.valueOf(orderId))))
          .call();
      return true;
    } catch (BitfinexException e) {
      if (e.getMessage().equals("Order could not be cancelled.")) {
        return false;
      } else {
        throw e;
      }
    }
  }

  public boolean cancelAllBitfinexOrders() throws IOException {

    try {
      decorateApiCall(
              () ->
                  bitfinex.cancelAllOrders(
                      apiKey,
                      payloadCreator,
                      signatureCreator,
                      new BitfinexCancelAllOrdersRequest()))
          .call();
      return true;
    } catch (BitfinexException e) {
      if (e.getMessage().equals("Orders could not be cancelled.")) {
        return false;
      } else {
        throw e;
      }
    }
  }

  public boolean cancelBitfinexOrderMulti(List<String> orderIds) throws IOException {

    long[] cancelOrderIds = new long[orderIds.size()];

    for (int i = 0; i < cancelOrderIds.length; i++) {
      cancelOrderIds[i] = Long.valueOf(orderIds.get(i));
    }

    decorateApiCall(
            () ->
                bitfinex.cancelOrderMulti(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexCancelOrderMultiRequest(cancelOrderIds)))
        .call();
    return true;
  }

  public BitfinexOfferStatusResponse cancelBitfinexOffer(String offerId) throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.cancelOffer(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexCancelOfferRequest(Long.valueOf(offerId))))
        .call();
  }

  public BitfinexOrderStatusResponse getBitfinexOrderStatus(String orderId) throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.orderStatus(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexOrderStatusRequest(Long.valueOf(orderId))))
        .call();
  }

  public BitfinexOfferStatusResponse getBitfinexOfferStatusResponse(String offerId)
      throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.offerStatus(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexOfferStatusRequest(Long.valueOf(offerId))))
        .call();
  }

  public BitfinexFundingTradeResponse[] getBitfinexFundingHistory(
      String symbol, Date until, int limit_trades) throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.pastFundingTrades(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexPastFundingTradesRequest(symbol, until, limit_trades)))
        .call();
  }

  public BitfinexTradeResponse[] getBitfinexTradeHistory(
      String symbol, long startTime, Long endTime, Integer limit, Integer reverse)
      throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.pastTrades(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexPastTradesRequest(symbol, startTime, endTime, limit, reverse)))
        .call();
  }

  public BitfinexCreditResponse[] getBitfinexActiveCredits() throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.activeCredits(
                    apiKey, payloadCreator, signatureCreator, new BitfinexActiveCreditsRequest()))
        .call();
  }

  public String withdraw(
      String withdrawType, String walletSelected, BigDecimal amount, String address)
      throws IOException {
    return withdraw(withdrawType, walletSelected, amount, address, null);
  }

  public String withdraw(
      String withdrawType,
      String walletSelected,
      BigDecimal amount,
      String address,
      String paymentId)
      throws IOException {

    BitfinexWithdrawalResponse[] withdrawRepsonse =
        decorateApiCall(
                () ->
                    bitfinex.withdraw(
                        apiKey,
                        payloadCreator,
                        signatureCreator,
                        new BitfinexWithdrawalRequest(
                            withdrawType, walletSelected, amount, address, paymentId)))
            .call();
    return withdrawRepsonse[0].getWithdrawalId();
  }

  public BitfinexActivePositionsResponse[] getBitfinexActivePositions() throws IOException {
    return decorateApiCall(
            () ->
                bitfinex.activePositions(
                    apiKey,
                    payloadCreator,
                    signatureCreator,
                    new BitfinexNonceOnlyRequest("/v1/positions")))
        .call();
  }

  public List<BitfinexPosition> getBitfinexActivePositionsV2() throws IOException {
    return decorateApiCall(
            () ->
                bitfinexV2.activePositions(
                    exchange.getNonceFactory(), apiKey, signatureV2, EmptyRequest.INSTANCE))
        .call();
  }

  public List<BitfinexTrade> getBitfinexTradesV2(
      String symbol, Long startTimeMillis, Long endTimeMillis, Long limit, Long sort)
      throws IOException {
    if (StringUtils.isBlank(symbol)) {
      return decorateApiCall(
              () ->
                  bitfinexV2.getTrades(
                      exchange.getNonceFactory(),
                      apiKey,
                      signatureV2,
                      startTimeMillis,
                      endTimeMillis,
                      limit,
                      sort,
                      EmptyRequest.INSTANCE))
          .call();
    }

    return decorateApiCall(
            () ->
                bitfinexV2.getTrades(
                    exchange.getNonceFactory(),
                    apiKey,
                    signatureV2,
                    symbol,
                    startTimeMillis,
                    endTimeMillis,
                    limit,
                    sort,
                    EmptyRequest.INSTANCE))
        .call();
  }

  public List<BitfinexOrderDetails> getBitfinexActiveOrdersV2(
      CurrencyPair currencyPair, List<Long> ids) throws IOException {
    String symbol = BitfinexUtils.toPairString(currencyPair);
    return decorateApiCall(
            () -> {
              if (symbol == null) {
                return bitfinexV2.getActiveOrders(
                    exchange.getNonceFactory(),
                    apiKey,
                    signatureV2,
                    BitfinexOpenOrdersRequest.builder().ids(ids).build());
              } else {
                return bitfinexV2.getActiveOrdersBySymbol(
                    exchange.getNonceFactory(),
                    apiKey,
                    signatureV2,
                    symbol,
                    BitfinexOpenOrdersRequest.builder().ids(ids).build());
              }
            })
        .call();
  }

  public List<BitfinexOrderDetails> getBitfinexOrdersHistory(
      CurrencyPair currencyPair, List<Long> ids, Instant start, Instant end, Long limit)
      throws IOException {
    String symbol = BitfinexUtils.toPairString(currencyPair);
    return decorateApiCall(
            () -> {
              if (symbol == null) {
                return bitfinexV2.getOrdersHistory(
                    exchange.getNonceFactory(),
                    apiKey,
                    signatureV2,
                    org.knowm.xchange.bitfinex.v2.dto.trade.BitfinexOrdersHistoryRequest.builder()
                        .ids(ids)
                        .from(start)
                        .to(end)
                        .limit(limit)
                        .build());
              } else {
                return bitfinexV2.getOrdersHistoryBySymbol(
                    exchange.getNonceFactory(),
                    apiKey,
                    signatureV2,
                    symbol,
                    org.knowm.xchange.bitfinex.v2.dto.trade.BitfinexOrdersHistoryRequest.builder()
                        .ids(ids)
                        .from(start)
                        .to(end)
                        .limit(limit)
                        .build());
              }
            })
        .call();
  }

  public List<OrderTrade> getBitfinexOrderTradesV2(final String symbol, final Long orderId)
      throws IOException {
    if (symbol == null || orderId == null)
      throw new NullPointerException(
          String.format(
              "Invalid request fields symbol [%s] and orderId [%s] are mandatory for get order trades call",
              symbol, orderId));

    return decorateApiCall(
            () ->
                bitfinexV2.getOrderTrades(
                    exchange.getNonceFactory(),
                    apiKey,
                    signatureV2,
                    symbol,
                    orderId,
                    EmptyRequest.INSTANCE))
        .call();
  }
}
