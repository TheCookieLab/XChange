package org.knowm.xchange.bybit.service;

import static org.knowm.xchange.bybit.BybitAdapters.convertToBybitSymbol;
import static org.knowm.xchange.bybit.BybitAdapters.createBybitExceptionFromResult;
import static org.knowm.xchange.bybit.BybitResilience.GLOBAL_RATE_LIMITER;

import io.github.resilience4j.ratelimiter.RateLimiter;
import java.io.IOException;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.account.BybitCancelAllOrdersPayload;
import org.knowm.xchange.bybit.dto.account.BybitCancelAllOrdersResponse;
import org.knowm.xchange.bybit.dto.account.position.BybitAddMarginPayload;
import org.knowm.xchange.bybit.dto.account.position.BybitClosedPnl;
import org.knowm.xchange.bybit.dto.account.position.BybitPositions;
import org.knowm.xchange.bybit.dto.account.position.BybitSetAutoAddMarginPayload;
import org.knowm.xchange.bybit.dto.account.position.BybitSetRiskLimitPayload;
import org.knowm.xchange.bybit.dto.account.position.BybitTradingStopPayload;
import org.knowm.xchange.bybit.dto.trade.BybitPreCheckPayload;
import org.knowm.xchange.bybit.dto.trade.BybitPreCheckResult;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchAmendPayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchCancelPayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchPlacePayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchResult;
import org.knowm.xchange.bybit.dto.trade.execution.BybitExecutions;
import org.knowm.xchange.bybit.dto.trade.history.BybitOrderHistoryDetails;
import org.knowm.xchange.bybit.dto.trade.BybitAmendOrderPayload;
import org.knowm.xchange.bybit.dto.trade.BybitCancelOrderPayload;
import org.knowm.xchange.bybit.dto.trade.BybitOrderResponse;
import org.knowm.xchange.bybit.dto.trade.BybitPlaceOrderPayload;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetail;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetails;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.instrument.Instrument;

public class BybitTradeServiceRaw extends BybitBaseService {

  protected BybitTradeServiceRaw(
      BybitExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  BybitResult<BybitOrderDetails<BybitOrderDetail>> getBybitOrder(
      BybitCategory category, Instrument instrument, String orderId) throws IOException {
    String symbol = null;
    if (instrument != null) {
      symbol = convertToBybitSymbol(instrument);
    }

    BybitResult<BybitOrderDetails<BybitOrderDetail>> bybitOrder =
        bybitAuthenticated.getOrders(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            category.getValue(),
            symbol,
            orderId,
            null);

    if (!bybitOrder.isSuccess()) {
      throw createBybitExceptionFromResult(bybitOrder);
    }
    return bybitOrder;
  }

  BybitResult<BybitOrderResponse> amendOrder(BybitAmendOrderPayload payload, BybitCategory category)
      throws IOException {
    RateLimiter rateLimiter = getAmendOrderRateLimiter(category);
    BybitResult<BybitOrderResponse> amendOrder =
        decorateApiCall(
                () ->
                    bybitAuthenticated.amendOrder(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter)
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!amendOrder.isSuccess()) {
      throw createBybitExceptionFromResult(amendOrder);
    }
    return amendOrder;
  }

  BybitResult<BybitOrderResponse> placeOrder(BybitPlaceOrderPayload payload, BybitCategory category)
      throws IOException {
    BybitResult<BybitOrderResponse> placeOrder =
        decorateApiCall(
                () ->
                    bybitAuthenticated.placeOrder(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(getCreateOrderRateLimiter(category))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!placeOrder.isSuccess()) {
      throw createBybitExceptionFromResult(placeOrder);
    }
    return placeOrder;
  }

  BybitResult<BybitOrderResponse> cancelOrder(
      BybitCategory category, String symbol, String orderId, String orderLinkId)
      throws IOException {
    RateLimiter rateLimiter = getCancelOrderRateLimiter(category);
    BybitCancelOrderPayload payload =
        new BybitCancelOrderPayload(category, symbol, orderId, orderLinkId);
    return decorateApiCall(
            () ->
                bybitAuthenticated.cancelOrder(
                    apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
        .withRateLimiter(rateLimiter)
        .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
        .call();
  }

  BybitResult<BybitCancelAllOrdersResponse> cancelAllOrders(
      String category,
      String symbol,
      String baseCoin,
      String settleCoin,
      String orderFilter,
      String stopOrderType)
      throws IOException {
    BybitCancelAllOrdersPayload payload =
        new BybitCancelAllOrdersPayload(
            category, symbol, baseCoin, settleCoin, orderFilter, stopOrderType);
    BybitResult<BybitCancelAllOrdersResponse> response =
        bybitAuthenticated.cancelAllOrders(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), payload);
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  BybitResult<BybitPositions> getPositions(
      BybitCategory category,
      String symbol,
      String baseCoin,
      String settleCoin,
      String limit,
      String cursor)
      throws IOException {
    BybitResult<BybitPositions> response =
        bybitAuthenticated.getPositions(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            category.getValue(),
            symbol,
            baseCoin,
            settleCoin,
            limit,
            cursor);
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  BybitResult<BybitCategorizedPayload<BybitClosedPnl>> getClosedPnl(
      BybitCategory category,
      String symbol,
      String startTime,
      String endTime,
      String limit,
      String cursor)
      throws IOException {
    BybitResult<BybitCategorizedPayload<BybitClosedPnl>> response =
        bybitAuthenticated.getClosedPnl(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            category.getValue(),
            symbol,
            startTime,
            endTime,
            limit,
            cursor);
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  BybitResult<Object> setTradingStop(BybitTradingStopPayload payload) throws IOException {
    BybitResult<Object> response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.setTradingStop(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  BybitResult<Object> setRiskLimit(BybitSetRiskLimitPayload payload) throws IOException {
    BybitResult<Object> response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.setRiskLimit(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  BybitResult<Object> addMargin(BybitAddMarginPayload payload) throws IOException {
    BybitResult<Object> response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.addMargin(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  BybitResult<Object> setAutoAddMargin(BybitSetAutoAddMarginPayload payload) throws IOException {
    BybitResult<Object> response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.setAutoAddMargin(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/order-history">API</a>
   */
  BybitResult<BybitOrderHistoryDetails> getOrderHistory(
      BybitCategory category,
      String symbol,
      String orderId,
      String orderLinkId,
      String orderStatus,
      String startTime,
      String endTime,
      String baseCoin,
      Integer limit,
      String cursor)
      throws IOException {
    BybitResult<BybitOrderHistoryDetails> result =
        bybitAuthenticated.getOrderHistory(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            category.getValue(),
            symbol,
            orderId,
            orderLinkId,
            orderStatus,
            startTime,
            endTime,
            baseCoin,
            limit == null ? null : limit.toString(),
            cursor);
    if (!result.isSuccess()) {
      throw createBybitExceptionFromResult(result);
    }
    return result;
  }

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/execution">API</a>
   */
  BybitResult<BybitExecutions> getExecutions(
      BybitCategory category,
      String symbol,
      String baseCoin,
      String orderId,
      String orderLinkId,
      String startTime,
      String endTime,
      Integer limit,
      String cursor)
      throws IOException {
    BybitResult<BybitExecutions> result =
        bybitAuthenticated.getExecutions(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            category.getValue(),
            symbol,
            baseCoin,
            orderId,
            orderLinkId,
            startTime,
            endTime,
            limit == null ? null : limit.toString(),
            cursor);
    if (!result.isSuccess()) {
      throw createBybitExceptionFromResult(result);
    }
    return result;
  }

  /**
   * Looks up an order by its client-supplied {@code orderLinkId} via {@code GET /v5/order/realtime}.
   * Used to reconcile a placement whose outcome is ambiguous.
   */
  BybitResult<BybitOrderDetails<BybitOrderDetail>> getBybitOrderByLinkId(
      BybitCategory category, String symbol, String orderLinkId) throws IOException {
    BybitResult<BybitOrderDetails<BybitOrderDetail>> bybitOrder =
        bybitAuthenticated.getOrders(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            category.getValue(),
            symbol,
            null,
            orderLinkId);
    if (!bybitOrder.isSuccess()) {
      throw createBybitExceptionFromResult(bybitOrder);
    }
    return bybitOrder;
  }

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/create-batch">API</a>
   */
  BybitBatchResult createBatch(BybitBatchPlacePayload payload) throws IOException {
    BybitBatchResult response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.createBatch(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw new BybitException(response.getRetCode(), response.getRetMsg(), response.getRetExtInfo());
    }
    return response;
  }

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/amend-batch">API</a>
   */
  BybitBatchResult amendBatch(BybitBatchAmendPayload payload) throws IOException {
    BybitBatchResult response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.amendBatch(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw new BybitException(response.getRetCode(), response.getRetMsg(), response.getRetExtInfo());
    }
    return response;
  }

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/cancel-batch">API</a>
   */
  BybitBatchResult cancelBatch(BybitBatchCancelPayload payload) throws IOException {
    BybitBatchResult response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.cancelBatch(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw new BybitException(response.getRetCode(), response.getRetMsg(), response.getRetExtInfo());
    }
    return response;
  }

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/pre-check">API</a>
   */
  BybitResult<BybitPreCheckResult> preCheck(BybitPreCheckPayload payload) throws IOException {
    BybitResult<BybitPreCheckResult> response =
        decorateApiCall(
                () ->
                    bybitAuthenticated.preCheck(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    if (!response.isSuccess()) {
      throw createBybitExceptionFromResult(response);
    }
    return response;
  }
}
