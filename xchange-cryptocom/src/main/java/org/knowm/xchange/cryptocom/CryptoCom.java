package org.knowm.xchange.cryptocom;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;

/**
 * Crypto.com Exchange v1 REST API. Every call, public or private, shares the same {@code {id,
 * method, code, result}} envelope ({@link CryptoComResponse}); private calls additionally sign a
 * {@link CryptoComRequest} body, so a single interface (rather than a public/private split) is
 * enough here - unlike most exchanges, Crypto.com does not authenticate via headers or query-string
 * digests.
 */
@Path("/exchange/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface CryptoCom {

  @GET
  @Path("public/get-instruments")
  CryptoComResponse getInstruments(@QueryParam("cursor") String cursor)
      throws IOException, CryptoComException;

  @GET
  @Path("public/get-book")
  CryptoComResponse getBook(
      @QueryParam("instrument_name") String instrumentName, @QueryParam("depth") Integer depth)
      throws IOException, CryptoComException;

  @GET
  @Path("public/get-trades")
  CryptoComResponse getPublicTrades(
      @QueryParam("instrument_name") String instrumentName, @QueryParam("count") Integer count)
      throws IOException, CryptoComException;

  @GET
  @Path("public/get-tickers")
  CryptoComResponse getTickers(@QueryParam("instrument_name") String instrumentName)
      throws IOException, CryptoComException;

  /**
   * Candlestick history. Either {@code count} or the {@code startTs}/{@code endTs} window may be
   * supplied; official parameter names are used verbatim (including the millisecond-unix {@code
   * start_ts}/{@code end_ts} form).
   */
  @GET
  @Path("public/get-candlestick")
  CryptoComResponse getCandlestick(
      @QueryParam("instrument_name") String instrumentName,
      @QueryParam("timeframe") String timeframe,
      @QueryParam("count") Integer count,
      @QueryParam("start_ts") Long startTs,
      @QueryParam("end_ts") Long endTs)
      throws IOException, CryptoComException;

  /** Expired settlement reference prices for dated instruments (official params: instrument_type, page). */
  @GET
  @Path("public/get-expired-settlement-price")
  CryptoComResponse getExpiredSettlementPrice(
      @QueryParam("instrument_type") String instrumentType, @QueryParam("page") Integer page)
      throws IOException, CryptoComException;

  /** Smart Cross Margin derivative risk reference data. */
  @GET
  @Path("public/get-risk-parameters")
  CryptoComResponse getRiskParameters() throws IOException, CryptoComException;

  @POST
  @Path("private/user-balance")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse userBalance(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/create-order")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse createOrder(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/cancel-order")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse cancelOrder(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/cancel-all-orders")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse cancelAllOrders(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/get-open-orders")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getOpenOrders(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/get-order-detail")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getOrderDetail(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/get-order-history")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getOrderHistory(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/get-trades")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getUserTrades(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/get-deposit-address")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getDepositAddress(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/get-deposit-history")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getDepositHistory(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/get-withdrawal-history")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getWithdrawalHistory(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/create-withdrawal")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse createWithdrawal(CryptoComRequest request)
      throws IOException, CryptoComException;

  /** Trigger/advanced order types (STOP_LOSS, STOP_LIMIT, TAKE_PROFIT, TAKE_PROFIT_LIMIT). */
  @POST
  @Path("private/advanced/create-order")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse createAdvancedOrder(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/get-positions")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getPositions(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/get-accounts")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getAccounts(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/get-fee-rate")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getFeeRate(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/get-fee-credit-balances")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getFeeCredits(CryptoComRequest request) throws IOException, CryptoComException;

  @POST
  @Path("private/user-balance-history")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getUserBalanceHistory(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/get-transactions")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse getTransactions(CryptoComRequest request)
      throws IOException, CryptoComException;

  @POST
  @Path("private/close-position")
  @Consumes(MediaType.APPLICATION_JSON)
  CryptoComResponse closePosition(CryptoComRequest request) throws IOException, CryptoComException;
}
