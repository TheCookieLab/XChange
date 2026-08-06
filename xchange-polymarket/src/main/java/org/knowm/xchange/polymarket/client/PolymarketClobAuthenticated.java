package org.knowm.xchange.polymarket.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.polymarket.dto.account.PolymarketApiCredentials;
import org.knowm.xchange.polymarket.dto.account.PolymarketBalanceResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketCancelRequest;
import org.knowm.xchange.polymarket.dto.trade.PolymarketCancelResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOpenOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderRequest;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrdersResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketPostOrderResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketTradesResponse;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Authenticated Polymarket CLOB endpoints.
 *
 * <p>{@code deriveApiKey} uses L1 (EIP-712 ClobAuth) headers; every other method uses L2
 * (HMAC) headers: {@code POLY_ADDRESS}, {@code POLY_TIMESTAMP}, {@code POLY_API_KEY}, {@code
 * POLY_PASSPHRASE}, and the computed {@code POLY_SIGNATURE}.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PolymarketClobAuthenticated {

  /** Derives (or returns) the L2 API credentials for the signing wallet; L1-signed. */
  @GET
  @Path("auth/derive-api-key")
  PolymarketApiCredentials deriveApiKey(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_NONCE") String nonce,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature)
      throws IOException;

  /** Posts a signed order; L2-signed. */
  @POST
  @Path("order")
  PolymarketPostOrderResponse postOrder(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_API_KEY") String apiKey,
      @HeaderParam("POLY_PASSPHRASE") String passphrase,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature,
      PolymarketOrderRequest request)
      throws IOException;

  /** Cancels one order by provider order id; L2-signed. */
  @DELETE
  @Path("order")
  PolymarketCancelResponse cancelOrder(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_API_KEY") String apiKey,
      @HeaderParam("POLY_PASSPHRASE") String passphrase,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature,
      PolymarketCancelRequest request)
      throws IOException;

  /**
   * Lists open orders, optionally filtered by market or token; L2-signed. CLOB V2 returns a
   * paginated envelope {@code {limit, next_cursor, count, data}}; pass {@code next_cursor} from
   * the previous page and stop when it is blank (see
   * https://docs.polymarket.com/api-reference/trade/get-user-orders).
   */
  @GET
  @Path("data/orders")
  PolymarketOrdersResponse getOrders(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_API_KEY") String apiKey,
      @HeaderParam("POLY_PASSPHRASE") String passphrase,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature,
      @QueryParam("next_cursor") String cursor,
      @QueryParam("market") String conditionId,
      @QueryParam("asset_id") String tokenId)
      throws IOException;

  /** Returns one order by provider order id; L2-signed. */
  @GET
  @Path("data/order/{orderId}")
  PolymarketOpenOrder getOrder(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_API_KEY") String apiKey,
      @HeaderParam("POLY_PASSPHRASE") String passphrase,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature,
      @PathParam("orderId") String orderId)
      throws IOException;

  /**
   * Lists user fills, optionally filtered by condition id; L2-signed. CLOB V2 requires the
   * account's {@code maker_address} and returns a paginated envelope {@code {limit, next_cursor,
   * count, data}}; pass {@code next_cursor} from the previous page and stop at the {@code LTE=}
   * sentinel or a blank cursor (see https://docs.polymarket.com/api-reference/trade/get-trades).
   */
  @GET
  @Path("data/trades")
  PolymarketTradesResponse getUserTrades(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_API_KEY") String apiKey,
      @HeaderParam("POLY_PASSPHRASE") String passphrase,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature,
      @QueryParam("maker_address") String makerAddress,
      @QueryParam("market") String conditionId,
      @QueryParam("next_cursor") String cursor)
      throws IOException;

  /** Returns the collateral (pUSD) balance in 6-decimal fixed-point; L2-signed. */
  @GET
  @Path("balance-allowance")
  PolymarketBalanceResponse getBalanceAllowance(
      @HeaderParam("POLY_ADDRESS") String walletAddress,
      @HeaderParam("POLY_TIMESTAMP") SynchronizedValueFactory<Long> timestampSeconds,
      @HeaderParam("POLY_API_KEY") String apiKey,
      @HeaderParam("POLY_PASSPHRASE") String passphrase,
      @HeaderParam("POLY_SIGNATURE") ParamsDigest signature,
      @QueryParam("asset_type") String assetType,
      @QueryParam("signature_type") Integer signatureType)
      throws IOException;
}
