package org.knowm.xchange.coinbase.v3;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountResponse;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseCommitConvertTradeRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteResponse;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertTradeResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseCurrentMarginWindowResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepRequest;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingRequest;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseClosePositionRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbasePreviewOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethodResponse;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethodsResponse;
import org.knowm.xchange.coinbase.v3.dto.permissions.CoinbaseKeyPermissionsResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseAllocatePortfolioRequest;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseAllocatePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseMultiAssetCollateralRequest;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseMultiAssetCollateralResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPortfolioSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbaseMovePortfolioFundsRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseBestBidAsksResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import si.mazi.rescu.ParamsDigest;

/**
 * Coinbase Advanced Trade API authenticated endpoints.
 *
 * <p>Extends {@link Coinbase} to inherit public endpoints. All methods in this interface require
 * authentication via the {@code Authorization} header containing a JWT token.
 */
@Path("/api/v3/brokerage")
@Produces(MediaType.APPLICATION_JSON)
public interface CoinbaseAuthenticated extends Coinbase {

  /**
   * All Advanced Trade API requests must include an Authorization Bearer header containing a JSON
   * Web Token (JWT) generated from the CDP API keys.
   *
   * <p><a href="https://docs.cdp.coinbase.com/advanced-trade/docs/rest-api-auth">REST API
   * authentication</a>
   *
   * <p>All request bodies should have content type application/json and be valid JSON.
   *
   * <p>The body is the request body string or omitted if there is no request body (typically for
   * GET requests).
   *
   * <p><a href="https://docs.cdp.coinbase.com/advanced-trade/docs/api-overview">API overview</a>
   */
  String CB_AUTHORIZATION_KEY = "Authorization";

  // ========== Account Endpoints ==========

  @GET
  @Path("accounts")
  CoinbaseAccountsResponse listAccounts(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException, CoinbaseException;

  @GET
  @Path("accounts/{account_id}")
  CoinbaseAccountResponse getAccount(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("account_id") String accountId)
      throws IOException, CoinbaseException;

  @POST
  @Path("orders")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseCreateOrderResponse createOrder(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseOrderRequest payload)
      throws IOException, CoinbaseException;

  @POST
  @Path("orders/edit")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse editOrder(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseEditOrderRequest payload)
      throws IOException, CoinbaseException;

  /**
   * Edits an order using Coinbase's current response schema.
   *
   * @param jwtDigest authenticated request digest
   * @param payload edit request
   * @return current edit response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @POST
  @Path("orders/edit")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseEditOrderResponse editOrderCurrent(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseEditOrderRequest payload)
      throws IOException, CoinbaseException;

  /**
   * Legacy batch-cancel transport contract retained for source and binary compatibility.
   *
   * @param jwtDigest authenticated request digest
   * @param payload batch-cancel request body
   * @return legacy order-shaped response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   */
  @POST
  @Path("orders/batch_cancel")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse cancelOrders(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, Object payload)
      throws IOException, CoinbaseException;

  /**
   * Current-schema batch-cancel transport contract.
   *
   * @param jwtDigest authenticated request digest
   * @param payload batch-cancel request body
   * @return per-order cancellation results
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   */
  @POST
  @Path("orders/batch_cancel")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseCancelOrdersResponse batchCancelOrders(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, Object payload)
      throws IOException, CoinbaseException;

  @GET
  @Path("orders/historical/batch")
  CoinbaseListOrdersResponse listOrders(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("order_ids") List<String> orderIds,
      @QueryParam("product_ids") List<String> productIds,
      @QueryParam("product_type") String productType,
      @QueryParam("order_status") List<String> orderStatus,
      @QueryParam("time_in_forces") List<String> timeInForces,
      @QueryParam("order_types") List<String> orderTypes,
      @QueryParam("order_side") String orderSide,
      @QueryParam("start_date") String startDate,
      @QueryParam("end_date") String endDate,
      @QueryParam("order_placement_source") String orderPlacementSource,
      @QueryParam("contract_expiry_type") String contractExpiryType,
      @QueryParam("asset_filters") List<String> assetFilters,
      @QueryParam("retail_portfolio_id") String retailPortfolioId,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor,
      @QueryParam("sort_by") String sortBy,
      @QueryParam("user_native_currency") String userNativeCurrency,
      @QueryParam("use_simplified_total_value_calculation")
          Boolean useSimplifiedTotalValueCalculation)
      throws IOException, CoinbaseException;

  /**
   * Lists fills over the supplied sequence window and optional response-driven filters.
   *
   * @param jwtDigest authenticated request digest
   * @param orderIds optional order identifiers
   * @param tradeIds optional trade identifiers
   * @param productIds optional product identifiers
   * @param startSequenceTimestamp inclusive window start
   * @param endSequenceTimestamp exclusive window end
   * @param retailPortfolioId optional portfolio scope
   * @param limit optional page size
   * @param cursor optional continuation cursor
   * @param sortBy optional API sort field
   * @param assetFilters optional asset filters
   * @param orderTypes optional order-type filters
   * @param orderSide optional order-side filter
   * @param productTypes optional product-type filters
   * @return current fills response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @GET
  @Path("orders/historical/fills")
  CoinbaseOrdersResponse listFills(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("order_ids") List<String> orderIds,
      @QueryParam("trade_ids") List<String> tradeIds,
      @QueryParam("product_ids") List<String> productIds,
      @QueryParam("start_sequence_timestamp") String startSequenceTimestamp,
      @QueryParam("end_sequence_timestamp") String endSequenceTimestamp,
      @QueryParam("retail_portfolio_id") String retailPortfolioId,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor,
      @QueryParam("sort_by") String sortBy,
      @QueryParam("asset_filters") List<String> assetFilters,
      @QueryParam("order_types") List<String> orderTypes,
      @QueryParam("order_side") String orderSide,
      @QueryParam("product_types") List<String> productTypes)
      throws IOException, CoinbaseException;

  /**
   * Lists fills using the pre-1.0.2 filter surface.
   *
   * @param jwtDigest authenticated request digest
   * @param orderIds optional order identifiers
   * @param tradeIds optional trade identifiers
   * @param productIds optional product identifiers
   * @param startSequenceTimestamp inclusive window start
   * @param endSequenceTimestamp exclusive window end
   * @param retailPortfolioId optional portfolio scope
   * @param limit optional page size
   * @param cursor optional continuation cursor
   * @param sortBy optional API sort field
   * @return current fills response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @deprecated use the complete filter overload
   */
  @Deprecated
  @GET
  @Path("orders/historical/fills")
  CoinbaseOrdersResponse listFills(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("order_ids") List<String> orderIds,
      @QueryParam("trade_ids") List<String> tradeIds,
      @QueryParam("product_ids") List<String> productIds,
      @QueryParam("start_sequence_timestamp") String startSequenceTimestamp,
      @QueryParam("end_sequence_timestamp") String endSequenceTimestamp,
      @QueryParam("retail_portfolio_id") String retailPortfolioId,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor,
      @QueryParam("sort_by") String sortBy)
      throws IOException, CoinbaseException;

  @GET
  @Path("orders/historical/{order_id}")
  CoinbaseOrderDetailResponse getOrder(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("order_id") String orderId)
      throws IOException, CoinbaseException;

  @POST
  @Path("orders/preview")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse previewOrder(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseOrderRequest payload)
      throws IOException, CoinbaseException;

  /**
   * Previews an order using Coinbase's current response schema.
   *
   * @param jwtDigest authenticated request digest
   * @param payload order request
   * @return current preview response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @POST
  @Path("orders/preview")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbasePreviewOrderResponse previewOrderCurrent(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseOrderRequest payload)
      throws IOException, CoinbaseException;

  @POST
  @Path("orders/edit_preview")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseOrdersResponse previewEditOrder(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseEditOrderRequest payload)
      throws IOException, CoinbaseException;

  /**
   * Previews an order edit using Coinbase's current response schema.
   *
   * @param jwtDigest authenticated request digest
   * @param payload edit request
   * @return current edit-preview response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @POST
  @Path("orders/edit_preview")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseEditOrderResponse previewEditOrderCurrent(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbaseEditOrderRequest payload)
      throws IOException, CoinbaseException;

  /**
   * Fetches best bids and asks for multiple product identifiers.
   *
   * @param jwtDigest authenticated request digest
   * @param productIds optional product identifiers; {@code null} requests all products
   * @return current best-bid-and-ask response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @GET
  @Path("best_bid_ask")
  CoinbaseBestBidAsksResponse getBestBidAsks(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_ids") List<String> productIds)
      throws IOException, CoinbaseException;

  /**
   * Fetches best bids and asks using the pre-1.0.2 single-product contract.
   *
   * @param jwtDigest authenticated request digest
   * @param productId optional single product identifier
   * @return current best-bid-and-ask response
   * @throws IOException when the request or response cannot be processed
   * @throws CoinbaseException when Coinbase rejects the request
   * @deprecated use {@link #getBestBidAsks(ParamsDigest, List)}
   */
  @Deprecated
  @GET
  @Path("best_bid_ask")
  CoinbaseBestBidAsksResponse getBestBidAsk(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_ids") String productId)
      throws IOException, CoinbaseException;

  @GET
  @Path("product_book")
  CoinbaseProductPriceBookResponse getProductBook(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_id") String productId,
      @QueryParam("limit") Integer limit,
      @QueryParam("aggregation_price_increment") String aggregationPriceIncrement)
      throws IOException, CoinbaseException;

  @GET
  @Path("products")
  CoinbaseProductsResponse listProducts(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset,
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
  CoinbaseProductResponse getProduct(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId,
      @QueryParam("get_tradability_status") Boolean getTradabilityStatus)
      throws IOException, CoinbaseException;

  @GET
  @Path("products/{product_id}/candles")
  CoinbaseProductCandlesResponse getProductCandles(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId,
      @QueryParam("start") String start,
      @QueryParam("end") String end,
      @QueryParam("granularity") String granularity,
      @QueryParam("limit") Integer limit)
      throws IOException, CoinbaseException;

  @GET
  @Path("products/{product_id}/ticker")
  CoinbaseProductMarketTradesResponse getMarketTrades(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId,
      @QueryParam("limit") Integer limit,
      @QueryParam("start") String start,
      @QueryParam("end") String end)
      throws IOException, CoinbaseException;

  /**
   * Returns the authenticated account's transaction and fee-tier summary for the supplied product
   * classification.
   *
   * @param jwtDigest request authorization digest
   * @param productType Coinbase product type, such as {@code FUTURE}
   * @param contractExpiryType Coinbase contract-expiry classification
   * @param productVenue Coinbase API venue, such as {@code FCM}
   * @return transaction summary containing the matching fee tier
   * @throws IOException when request transport or response decoding fails
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @GET
  @Path("transaction_summary")
  CoinbaseTransactionSummaryResponse getTransactionSummary(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("product_type") String productType,
      @QueryParam("contract_expiry_type") String contractExpiryType,
      @QueryParam("product_venue") String productVenue)
      throws IOException, CoinbaseException;

  @GET
  @Path("payment_methods")
  CoinbasePaymentMethodsResponse getPaymentMethods(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @GET
  @Path("payment_methods/{payment_method_id}")
  CoinbasePaymentMethodResponse getPaymentMethod(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("payment_method_id") String paymentMethodId)
      throws IOException, CoinbaseException;

  // ========== Portfolio Endpoints ==========

  @GET
  @Path("portfolios")
  CoinbasePortfoliosResponse listPortfolios(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("portfolio_type") String portfolioType)
      throws IOException, CoinbaseException;

  @POST
  @Path("portfolios")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbasePortfolioResponse createPortfolio(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest, CoinbasePortfolioRequest payload)
      throws IOException, CoinbaseException;

  @GET
  @Path("portfolios/{portfolio_uuid}")
  CoinbasePortfolioResponse getPortfolioBreakdown(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid)
      throws IOException, CoinbaseException;

  @PUT
  @Path("portfolios/{portfolio_uuid}")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbasePortfolioResponse editPortfolio(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid,
      CoinbasePortfolioRequest payload)
      throws IOException, CoinbaseException;

  @DELETE
  @Path("portfolios/{portfolio_uuid}")
  CoinbasePortfolioResponse deletePortfolio(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid)
      throws IOException, CoinbaseException;

  @POST
  @Path("portfolios/move_funds")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbasePortfolioResponse movePortfolioFunds(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseMovePortfolioFundsRequest payload)
      throws IOException, CoinbaseException;

  // ========== Convert Endpoints ==========

  @POST
  @Path("convert/quote")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseConvertQuoteResponse createConvertQuote(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseConvertQuoteRequest payload)
      throws IOException, CoinbaseException;

  @POST
  @Path("convert/trade/{trade_id}")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseConvertTradeResponse commitConvertTrade(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("trade_id") String tradeId,
      CoinbaseCommitConvertTradeRequest payload)
      throws IOException, CoinbaseException;

  @GET
  @Path("convert/trade/{trade_id}")
  CoinbaseConvertTradeResponse getConvertTrade(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("trade_id") String tradeId)
      throws IOException, CoinbaseException;

  // ========== Futures (CFM) Endpoints ==========

  @GET
  @Path("cfm/balance_summary")
  CoinbaseFuturesBalanceSummaryResponse getFuturesBalanceSummary(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @GET
  @Path("cfm/positions")
  CoinbaseFuturesPositionsResponse listFuturesPositions(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @GET
  @Path("cfm/positions/{product_id}")
  CoinbaseFuturesPositionResponse getFuturesPosition(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("product_id") String productId)
      throws IOException, CoinbaseException;

  @POST
  @Path("cfm/sweeps/schedule")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseFuturesSweepResponse scheduleFuturesSweep(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseFuturesSweepRequest payload)
      throws IOException, CoinbaseException;

  @GET
  @Path("cfm/sweeps")
  CoinbaseFuturesSweepsResponse listFuturesSweeps(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @DELETE
  @Path("cfm/sweeps")
  CoinbaseFuturesSweepResponse cancelFuturesSweep(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @GET
  @Path("cfm/intraday/margin_setting")
  CoinbaseIntradayMarginSettingResponse getIntradayMarginSetting(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  @POST
  @Path("cfm/intraday/margin_setting")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseIntradayMarginSettingResponse setIntradayMarginSetting(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseIntradayMarginSettingRequest payload)
      throws IOException, CoinbaseException;

  /**
   * Returns the current margin window for Coinbase's default margin profile.
   *
   * @since 1.0.2
   */
  @GET
  @Path("cfm/intraday/current_margin_window")
  CoinbaseCurrentMarginWindowResponse getCurrentMarginWindow(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;

  /**
   * Returns the current margin window for a selected margin profile.
   *
   * @param jwtDigest request authorization digest
   * @param marginProfileType optional Coinbase margin-profile type; {@code null} selects the
   *     provider default
   * @return the selected profile's current margin window
   * @throws IOException when request transport or response decoding fails
   * @throws CoinbaseException when Coinbase rejects the request
   * @since 1.0.2
   */
  @GET
  @Path("cfm/intraday/current_margin_window")
  CoinbaseCurrentMarginWindowResponse getCurrentMarginWindow(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("margin_profile_type") String marginProfileType)
      throws IOException, CoinbaseException;

  // ========== Perpetuals (INTX) Endpoints ==========

  @GET
  @Path("intx/portfolio/{portfolio_uuid}")
  CoinbasePerpetualsPortfolioSummaryResponse getPerpetualsPortfolioSummary(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid)
      throws IOException, CoinbaseException;

  @GET
  @Path("intx/positions/{portfolio_uuid}")
  CoinbasePerpetualsPositionsResponse listPerpetualsPositions(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid)
      throws IOException, CoinbaseException;

  @GET
  @Path("intx/positions/{portfolio_uuid}/{symbol}")
  CoinbasePerpetualsPositionResponse getPerpetualsPosition(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid,
      @PathParam("symbol") String symbol)
      throws IOException, CoinbaseException;

  @GET
  @Path("intx/balances/{portfolio_uuid}")
  CoinbasePerpetualsBalancesResponse getPerpetualsPortfolioBalances(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("portfolio_uuid") String portfolioUuid)
      throws IOException, CoinbaseException;

  @POST
  @Path("intx/multi_asset_collateral")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseMultiAssetCollateralResponse optInMultiAssetCollateral(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseMultiAssetCollateralRequest payload)
      throws IOException, CoinbaseException;

  @POST
  @Path("intx/allocate")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseAllocatePortfolioResponse allocatePortfolio(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseAllocatePortfolioRequest payload)
      throws IOException, CoinbaseException;

  // ========== Additional Order Endpoints ==========

  @POST
  @Path("orders/close_position")
  @Consumes(MediaType.APPLICATION_JSON)
  CoinbaseCreateOrderResponse closePosition(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      CoinbaseClosePositionRequest payload)
      throws IOException, CoinbaseException;

  // ========== API Key Permissions ==========

  @GET
  @Path("key_permissions")
  CoinbaseKeyPermissionsResponse getKeyPermissions(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest)
      throws IOException, CoinbaseException;
}
