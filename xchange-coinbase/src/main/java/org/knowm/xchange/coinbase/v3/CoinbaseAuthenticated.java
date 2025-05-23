package org.knowm.xchange.coinbase.v3;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.coinbase.v2.Coinbase;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseAccountsData;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBooksResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import si.mazi.rescu.ParamsDigest;

@Path("/api/v3/brokerage")
@Produces(MediaType.APPLICATION_JSON)
public interface CoinbaseAuthenticated extends Coinbase {

  /**
   * All Advanced Trade API requests must include an Authorization Bearer header containing a JSON
   * Web Token (JWT) generated from the CDP API keys.
   * <p><a href="https://docs.cdp.coinbase.com/advanced-trade/docs/rest-api-auth</a>
   *
   * <p>All request bodies should have content type application/json and be valid JSON.
   *
   * <p>The body is the request body string or omitted if there is no request body (typically for
   * GET requests).
   *
   * <p><a href=https://docs.cdp.coinbase.com/advanced-trade/docs/api-overview</a></p>
   */
  String CB_AUTHORIZATION_KEY = "Authorization";

  @GET
  @Path("accounts")
  CoinbaseAccountsResponse listAccounts(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("limit") Integer limit, @QueryParam("cursor") String cursor)
      throws IOException, CoinbaseException;

  @GET
  @Path("accounts/{account_id}")
  CoinbaseAccountsData getAccount(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("account_id") String accountId) throws IOException, CoinbaseException;

  @POST
  @Path("orders")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse createOrder(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      Object payload) throws IOException, CoinbaseException;

  @POST
  @Path("orders/batch_cancel")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse cancelOrders(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      Object payload) throws IOException, CoinbaseException;

  @GET
  @Path("orders/historical/batch")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse listOrders(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @GET
  @Path("orders/historical/fills")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse listFills(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @GET
  @Path("orders/historical/{order_id}}")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse getOrder(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("order_id") String orderId) throws IOException, CoinbaseException;

  @POST
  @Path("orders/preview")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse previewOrders(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      Object payload) throws IOException, CoinbaseException;

  @GET
  @Path("best_bid_ask")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbasePriceBooksResponse getBestBidAsk(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_id") String productId) throws IOException, CoinbaseException;

  @GET
  @Path("product_book")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbasePriceBooksResponse getProductBook(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_id") String productId, @QueryParam("limit") Integer limit,
      @QueryParam("aggregation_price_increment") String aggregationPriceIncrement)
      throws IOException, CoinbaseException;

  @GET
  @Path("products")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseProductsResponse listProducts(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset,
      @QueryParam("product_type") String productType,
      @QueryParam("product_ids") String[] productIds,
      @QueryParam("contract_expiry_type") String contractExpiryType,
      @QueryParam("expiring_contract_status") String expiringContractStatus,
      @QueryParam("get_tradability_status") Boolean getTradabilityStatus,
      @QueryParam("get_all_products") Boolean getAllProducts,
      @QueryParam("products_sort_order") String productsSortOrder)
      throws IOException, CoinbaseException;

  @GET
  @Path("products/{product_id}")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseProductsResponse getProduct(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId,
      @QueryParam("get_tradability_status") Boolean getTradabilityStatus)
      throws IOException, CoinbaseException;

  @GET
  @Path("products/{product_id}/candles")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseProductCandlesResponse getProductCandles(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId,
      @QueryParam("get_tradability_status") Boolean getTradabilityStatus)
      throws IOException, CoinbaseException;

  @GET
  @Path("products/{product_id}/ticker")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseProductMarketTradesResponse getMarketTrades(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId, @QueryParam("limit") Integer limit,
      @QueryParam("start") String start, @QueryParam("end") String end)
      throws IOException, CoinbaseException;

  @GET
  @Path("transaction_summary")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseTransactionSummaryResponse getTransactionSummary(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_type") String productType,
      @QueryParam("contract_expiry_type") String contractExpiryType,
      @QueryParam("product_venue") String productVenue) throws IOException, CoinbaseException;

//  @GET
//  @Path("accounts/{accountId}/transactions")
//  CoinbaseTransactionsResponse getTransactions(
//      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("accountId") String accountId) throws IOException, CoinbaseException;
//
//  @GET
//  @Path("accounts/{accountId}/buys")
//  CoinbaseBuySellResponse getBuys(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("accountId") String accountId, @QueryParam("limit") Integer limit,
//      @QueryParam("starting_after") String startingAfter) throws IOException, CoinbaseException;
//
//  @GET
//  @Path("accounts/{accountId}/sells")
//  CoinbaseBuySellResponse getSells(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("accountId") String accountId, @QueryParam("limit") Integer limit,
//      @QueryParam("starting_after") String startingAfter) throws IOException, CoinbaseException;
//
//  @GET
//  @Path("accounts/{accountId}/deposits")
//  Map getDeposits(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("accountId") String accountId) throws IOException, CoinbaseException;
//
//  @GET
//  @Path("accounts/{accountId}/withdrawals")
//  Map getWithdrawals(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("accountId") String accountId) throws IOException, CoinbaseException;
//
//  @GET
//  @Path("payment-methods")
//  CoinbasePaymentMethodsData getPaymentMethods(
//      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
//      throws IOException, CoinbaseException;
//
//  @POST
//  @Path("accounts/{account}/buys")
//  @Consumes(MediaType.APPLICATION_JSON)
//  CoinbaseBuyData buy(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("account") String accountId, Object payload) throws IOException, CoinbaseException;
//
//  @POST
//  @Path("accounts/{account}/sells")
//  @Consumes(MediaType.APPLICATION_JSON)
//  CoinbaseSellData sell(@HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
//      @PathParam("account") String accountId, Object payload) throws IOException, CoinbaseException;
}
