package org.knowm.xchange.gateio;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.knowm.xchange.gateio.dto.GateioException;
import org.knowm.xchange.gateio.dto.account.*;
import org.knowm.xchange.gateio.dto.trade.*;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Path("api/v4")
@Produces(MediaType.APPLICATION_JSON)
public interface GateioV4Authenticated {

  @GET
  @Path("wallet/deposit_address")
  GateioDepositAddress getDepositAddress(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency)
      throws IOException, GateioException;

  @GET
  @Path("wallet/withdraw_status")
  List<GateioWithdrawStatus> getWithdrawStatus(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency)
      throws IOException, GateioException;

  @GET
  @Path("spot/accounts")
  List<GateioCurrencyBalance> getSpotAccounts(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency)
      throws IOException, GateioException;

  @GET
  @Path("/wallet/fee")
  GateioSpotFee getSpotFee(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency_pair") String currencyPair)
      throws IOException, GateioException;

  @GET
  @Path("futures/{settle}/fee")
  Map<String, GateioFuturesFee> getFuturesFee(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @PathParam("settle") String settle,
      @QueryParam("contract") String contract)
      throws IOException, GateioException;

  @GET
  @Path("spot/account_book")
  List<GateioAccountBookRecord> getAccountBookRecords(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency,
      @QueryParam("from") Long from,
      @QueryParam("to") Long to,
      @QueryParam("limit") Integer pageLength,
      @QueryParam("page") Integer pageNumber,
      @QueryParam("type") String type)
      throws IOException, GateioException;

  @GET
  @Path("spot/orders")
  List<GateioSpotOrderResponse> listOrders(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency_pair") String currencyPair,
      @QueryParam("status") String status)
      throws IOException, GateioException;

  @GET
  @Path("spot/orders/{order_id}")
  GateioSpotOrderResponse getOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @PathParam("order_id") String orderId,
      @QueryParam("currency_pair") String currencyPair)
      throws IOException, GateioException;

  @GET
  @Path("futures/{settle}/orders/{order_id}")
  GateioFuturesOrderResponse getFuturesOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @HeaderParam("x-gate-exptime") Long expirationTime,
      @PathParam("settle") String settle,
      @PathParam("order_id") String orderId)
      throws IOException, GateioException;

  @DELETE
  @Path("spot/orders/{order_id}")
  GateioSpotOrderResponse cancelOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @PathParam("order_id") String orderId,
      @QueryParam("currency_pair") String currencyPair)
      throws IOException, GateioException;

  @DELETE
  @Path("futures/{settle}/orders/{order_id}")
  GateioFuturesOrderResponse cancelFuturesOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @HeaderParam("x-gate-exptime") Long expirationTime,
      @PathParam("settle") String settle,
      @PathParam("order_id") String orderId)
      throws IOException, GateioException;

  /**
   * Amends an existing spot order from a raw request map.
   *
   * <p>{@code request} carries only the mutable fields to replace ({@code amount}, {@code price},
   * {@code amend_text}, ...); the response is the amended order. Gate amends asynchronously: a
   * non-terminal {@code status} means the amendment is still being applied.
   */
  @PATCH
  @Path("spot/orders/{order_id}")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioSpotOrderResponse amendOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @PathParam("order_id") String orderId,
      @QueryParam("currency_pair") String currencyPair,
      Map<String, Object> request)
      throws IOException, GateioException;

  @PUT
  @Path("futures/{settle}/orders/{order_id}")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioFuturesOrderResponse amendFuturesOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @HeaderParam("x-gate-exptime") Long expirationTime,
      @PathParam("settle") String settle,
      @PathParam("order_id") String orderId,
      Map<String, Object> request)
      throws IOException, GateioException;

  @POST
  @Path("spot/orders")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioSpotOrderResponse createOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      GateioSpotOrderRequest gateioOrder)
      throws IOException, GateioException;

  @POST
  @Path("futures/{settle}/orders")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioFuturesOrderResponse createFuturesOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @HeaderParam("x-gate-exptime") Long expirationTime,
      @PathParam("settle") String settle,
      GateioFuturesOrderRequest gateioFuturesOrder)
      throws IOException, GateioException;

  /** Lists all open spot orders, optionally scoped by page, page size, and account. */
  @GET
  @Path("spot/open_orders")
  List<GateioOpenOrders> getOpenOrders(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("page") Integer page,
      @QueryParam("limit") Integer limit,
      @QueryParam("account") String account)
      throws IOException, GateioException;

  /** Amends an existing spot order by replacing the supplied mutable fields. */
  @PATCH
  @Path("spot/orders/{order_id}")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioSpotOrderResponse amendOrder(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @PathParam("order_id") String orderId,
      @QueryParam("currency_pair") String currencyPair,
      @QueryParam("account") String account,
      GateioAmendOrderRequest amendRequest)
      throws IOException, GateioException;

  /**
   * Cancels all matching open spot orders, optionally filtered by pair, side, and account.
   *
   * <p>Each response element is a Gate {@code OrderCancel}: order fields plus {@code succeeded}/
   * {@code label}/{@code message} outcome fields, since bulk cancellation succeeds partially.
   */
  @DELETE
  @Path("spot/orders")
  List<GateioCancelOrderResult> cancelAllOrders(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency_pair") String currencyPair,
      @QueryParam("side") String side,
      @QueryParam("account") String account,
      @QueryParam("action_mode") String actionMode)
      throws IOException, GateioException;

  /** Creates multiple spot orders in one request and returns per-order outcomes. */
  @POST
  @Path("spot/batch_orders")
  @Consumes(MediaType.APPLICATION_JSON)
  List<GateioBatchOrderResult> createBatchOrders(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      List<GateioSpotOrderRequest> gateioOrders)
      throws IOException, GateioException;

  /** Cancels multiple spot orders in one request and returns per-order outcomes. */
  @POST
  @Path("spot/cancel_batch_orders")
  @Consumes(MediaType.APPLICATION_JSON)
  List<GateioCancelOrderResult> cancelBatchOrders(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      List<GateioCancelBatchRequest> cancelRequests)
      throws IOException, GateioException;

  /** Starts or updates the spot countdown that cancels matching open orders. */
  @POST
  @Path("spot/countdown_cancel_all")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioTriggerTime countdownCancelAll(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      GateioCountdownCancelRequest countdownRequest)
      throws IOException, GateioException;

  @GET
  @Path("spot/my_trades")
  List<GateioUserTradeRaw> getTradingHistory(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency_pair") String currencyPair,
      @QueryParam("limit") Integer pageLength,
      @QueryParam("page") Integer pageNumber,
      @QueryParam("order_id") String orderId,
      @QueryParam("account") String account,
      @QueryParam("from") Long from,
      @QueryParam("to") Long to)
      throws IOException, GateioException;

  @GET
  @Path("wallet/saved_address")
  List<GateioAddressRecord> getSavedAddresses(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency)
      throws IOException, GateioException;

  @GET
  @Path("wallet/sub_account_transfers")
  List<GateioSubAccountTransfer> getSubAccountTransfers(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("sub_uid") String subAccountId,
      @QueryParam("from") Long from,
      @QueryParam("to") Long to,
      @QueryParam("limit") Integer pageLength,
      @QueryParam("offset") Integer zeroBasedPageNumber)
      throws IOException, GateioException;

  @GET
  @Path("wallet/withdrawals")
  List<GateioWithdrawalRecord> getWithdrawals(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency,
      @QueryParam("from") Long from,
      @QueryParam("to") Long to,
      @QueryParam("limit") Integer pageLength,
      @QueryParam("offset") Integer zeroBasedPageNumber)
      throws IOException, GateioException;

  @GET
  @Path("wallet/deposits")
  List<GateioDepositRecord> getDeposits(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @QueryParam("currency") String currency,
      @QueryParam("from") Long from,
      @QueryParam("to") Long to,
      @QueryParam("limit") Integer pageLength,
      @QueryParam("offset") Integer zeroBasedPageNumber)
      throws IOException, GateioException;

  @POST
  @Path("withdrawals")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioWithdrawalRecord withdraw(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      GateioWithdrawalRequest gateioWithdrawalRequest)
      throws IOException, GateioException;

  @POST
  @Path("futures/{settle}/positions/{contract}/leverage")
  @Consumes(MediaType.APPLICATION_JSON)
  GateioPositionLeverageUpdate updatePositionLeverage(
      @HeaderParam("KEY") String apiKey,
      @HeaderParam("Timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("SIGN") ParamsDigest signer,
      @PathParam("settle") String settle,
      @PathParam("contract") String contract,
      @QueryParam("leverage") String leverage,
      @QueryParam("cross_leverage_limit") String cross_leverage)
      throws IOException, GateioException;
}
