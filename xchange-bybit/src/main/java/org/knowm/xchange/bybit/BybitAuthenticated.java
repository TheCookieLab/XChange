package org.knowm.xchange.bybit;

import static org.knowm.xchange.bybit.service.BybitDigest.X_BAPI_API_KEY;
import static org.knowm.xchange.bybit.service.BybitDigest.X_BAPI_SIGN;
import static org.knowm.xchange.bybit.service.BybitDigest.X_BAPI_TIMESTAMP;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.account.BybitAccountInfoResponse;
import org.knowm.xchange.bybit.dto.account.BybitBorrowableAmount;
import org.knowm.xchange.bybit.dto.account.BybitBorrowHistory;
import org.knowm.xchange.bybit.dto.account.BybitCancelAllOrdersPayload;
import org.knowm.xchange.bybit.dto.account.BybitCancelAllOrdersResponse;
import org.knowm.xchange.bybit.dto.account.BybitCoinInfo;
import org.knowm.xchange.bybit.dto.account.BybitCoinInfos;
import org.knowm.xchange.bybit.dto.account.BybitCollateralInfo;
import org.knowm.xchange.bybit.dto.account.BybitCollateralInfos;
import org.knowm.xchange.bybit.dto.account.BybitDeliveryRecord;
import org.knowm.xchange.bybit.dto.account.BybitTransactionLog;
import org.knowm.xchange.bybit.dto.account.BybitTransferPayload;
import org.knowm.xchange.bybit.dto.account.BybitTransferResponse;
import org.knowm.xchange.bybit.dto.account.allcoins.BybitAllCoinsBalance;
import org.knowm.xchange.bybit.dto.account.feerates.BybitFeeRates;
import org.knowm.xchange.bybit.dto.account.position.BybitAddMarginPayload;
import org.knowm.xchange.bybit.dto.account.position.BybitClosedPnl;
import org.knowm.xchange.bybit.dto.account.position.BybitPosition;
import org.knowm.xchange.bybit.dto.account.position.BybitPositions;
import org.knowm.xchange.bybit.dto.account.position.BybitSetAutoAddMarginPayload;
import org.knowm.xchange.bybit.dto.account.position.BybitSetLeveragePayload;
import org.knowm.xchange.bybit.dto.account.position.BybitSetRiskLimitPayload;
import org.knowm.xchange.bybit.dto.account.position.BybitSwitchModePayload;
import org.knowm.xchange.bybit.dto.account.position.BybitTradingStopPayload;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitWalletBalance;
import org.knowm.xchange.bybit.dto.trade.BybitAmendOrderPayload;
import org.knowm.xchange.bybit.dto.trade.BybitPreCheckPayload;
import org.knowm.xchange.bybit.dto.trade.BybitPreCheckResult;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchAmendPayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchCancelPayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchPlacePayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchResult;
import org.knowm.xchange.bybit.dto.trade.execution.BybitExecutions;
import org.knowm.xchange.bybit.dto.trade.history.BybitOrderHistoryDetails;
import org.knowm.xchange.bybit.dto.trade.BybitCancelOrderPayload;
import org.knowm.xchange.bybit.dto.trade.BybitOrderResponse;
import org.knowm.xchange.bybit.dto.trade.BybitPlaceOrderPayload;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetail;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetails;
import org.knowm.xchange.bybit.service.BybitException;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

@Path("/v5")
@Produces(MediaType.APPLICATION_JSON)
public interface BybitAuthenticated {

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/account/wallet-balance">API</a>
   */
  @GET
  @Path("/account/wallet-balance")
  BybitResult<BybitWalletBalance> getWalletBalance(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("accountType") String accountType)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/asset/all-balance">API</a>
   */
  @GET
  @Path("/asset/transfer/query-account-coins-balance")
  BybitResult<BybitAllCoinsBalance> getAllCoinsBalance(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("accountType") String accountType)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/account/fee-rate">API</a>
   */
  @GET
  @Path("/account/fee-rate")
  BybitResult<BybitFeeRates> getFeeRate(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @Nonnull @QueryParam("symbol") String symbol)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/open-order">API</a>
   */
  @GET
  @Path("/order/realtime")
  BybitResult<BybitOrderDetails<BybitOrderDetail>> getOrders(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("orderId") String orderId,
      @QueryParam("orderLinkId") String orderLinkId)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/cancel-order">API</a>
   */
  @POST
  @Path("/order/cancel")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<BybitOrderResponse> cancelOrder(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitCancelOrderPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/amend-order">API</a>
   */
  @POST
  @Path("/order/amend")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<BybitOrderResponse> amendOrder(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitAmendOrderPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/order-history">API</a>
   */
  @GET
  @Path("/order/history")
  BybitResult<BybitOrderHistoryDetails> getOrderHistory(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("orderId") String orderId,
      @QueryParam("orderLinkId") String orderLinkId,
      @QueryParam("orderStatus") String orderStatus,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("baseCoin") String baseCoin,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/execution">API</a>
   */
  @GET
  @Path("/execution/list")
  BybitResult<BybitExecutions> getExecutions(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("baseCoin") String baseCoin,
      @QueryParam("orderId") String orderId,
      @QueryParam("orderLinkId") String orderLinkId,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/create-batch">API</a>
   */
  @POST
  @Path("/order/create-batch")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitBatchResult createBatch(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitBatchPlacePayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/amend-batch">API</a>
   */
  @POST
  @Path("/order/amend-batch")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitBatchResult amendBatch(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitBatchAmendPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/cancel-batch">API</a>
   */
  @POST
  @Path("/order/cancel-batch")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitBatchResult cancelBatch(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitBatchCancelPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/pre-check">API</a>
   */
  @POST
  @Path("/order/pre-check")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<BybitPreCheckResult> preCheck(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitPreCheckPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/set-leverage">API</a>
   */
  @POST
  @Path("/position/set-leverage")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<Object> setLeverage(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitSetLeveragePayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/position-mode">API</a>
   */
  @POST
  @Path("/position/switch-mode")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<Object> switchMode(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitSwitchModePayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/create-order">API</a>
   */
  @POST
  @Path("/order/create")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<BybitOrderResponse> placeOrder(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitPlaceOrderPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/account/account-info">API</a>
   */
  @GET
  @Path("/account/info")
  BybitResult<BybitAccountInfoResponse> getAccountInfo(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/order/cancel-all">API</a>
   */
  @POST
  @Path("/order/cancel-all")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<BybitCancelAllOrdersResponse> cancelAllOrders(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitCancelAllOrdersPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/account/transaction-log">API</a>
   */
  @GET
  @Path("/account/transaction-log")
  BybitResult<BybitCategorizedPayload<BybitTransactionLog>> getTransactionLog(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("accountType") String accountType,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("baseCoin") String baseCoin,
      @QueryParam("type") String type,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/asset/inter-transfer">API</a>
   */
  @POST
  @Path("/asset/transfer/inter-transfer")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<BybitTransferResponse> interTransfer(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitTransferPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/account/collateral-info">API</a>
   */
  @GET
  @Path("/account/collateral-info")
  BybitResult<BybitCollateralInfos> getCollateralInfo(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("currency") String currency)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/account/borrow-history">API</a>
   */
  @GET
  @Path("/account/borrow-history")
  BybitResult<BybitCategorizedPayload<BybitBorrowHistory>> getBorrowHistory(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("currency") String currency,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/asset/delivery">API</a>
   */
  @GET
  @Path("/asset/delivery-record")
  BybitResult<BybitCategorizedPayload<BybitDeliveryRecord>> getDeliveryRecord(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("expDate") String expDate,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/spot-margin-uta/max-borrowable">API</a>
   */
  @GET
  @Path("/spot-margin-trade/max-borrowable")
  BybitResult<BybitBorrowableAmount> getBorrowableAmount(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("currency") String currency)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/asset/coin-info">API</a>
   */
  @GET
  @Path("/asset/coin/query-info")
  BybitResult<BybitCoinInfos> getCoinInfo(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("coin") String coin)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/list">API</a>
   */
  @GET
  @Path("/position/list")
  BybitResult<BybitPositions> getPositions(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("baseCoin") String baseCoin,
      @QueryParam("settleCoin") String settleCoin,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/closed-pnl">API</a>
   */
  @GET
  @Path("/position/closed-pnl")
  BybitResult<BybitCategorizedPayload<BybitClosedPnl>> getClosedPnl(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") String limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/trading-stop">API</a>
   */
  @POST
  @Path("/position/trading-stop")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<Object> setTradingStop(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitTradingStopPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/set-risk-limit">API</a>
   */
  @POST
  @Path("/position/set-risk-limit")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<Object> setRiskLimit(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitSetRiskLimitPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/add-margin">API</a>
   */
  @POST
  @Path("/position/add-margin")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<Object> addMargin(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitAddMarginPayload payload)
      throws IOException, BybitException;

  /**
   * @apiSpec <a href="https://bybit-exchange.github.io/docs/v5/position/set-auto-add-margin">API</a>
   */
  @POST
  @Path("/position/set-auto-add-margin")
  @Consumes(MediaType.APPLICATION_JSON)
  BybitResult<Object> setAutoAddMargin(
      @HeaderParam(X_BAPI_API_KEY) String apiKey,
      @HeaderParam(X_BAPI_SIGN) ParamsDigest signature,
      @HeaderParam(X_BAPI_TIMESTAMP) SynchronizedValueFactory<Long> timestamp,
      BybitSetAutoAddMarginPayload payload)
      throws IOException, BybitException;
}
