package org.knowm.xchange.kalshi.client;

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
import org.knowm.xchange.kalshi.dto.account.KalshiBalanceResponse;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiCancelResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiCreateOrderResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderRequest;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrdersResponse;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Authenticated Kalshi v2 REST endpoints.
 *
 * <p>Order placement uses the V2 event-orders surface ({@code POST /portfolio/events/orders}),
 * which is YES-leg only with fixed-point dollar price strings. Order queries and cancellation use
 * the legacy {@code /portfolio/orders} surface, whose prices are integer cents.
 */
@Path("trade-api/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface KalshiAuthenticated {

  /** Returns the portfolio balance in integer cents. */
  @GET
  @Path("portfolio/balance")
  KalshiBalanceResponse getBalance(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature)
      throws IOException;

  /** Returns open event and market positions. */
  @GET
  @Path("portfolio/positions")
  KalshiPositionsResponse getPositions(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException;

  /** Lists orders, optionally filtered by ticker and lifecycle status. */
  @GET
  @Path("portfolio/orders")
  KalshiOrdersResponse getOrders(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature,
      @QueryParam("ticker") String ticker,
      @QueryParam("status") String status,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException;

  /** Returns a single order by provider order id. */
  @GET
  @Path("portfolio/orders/{order_id}")
  KalshiOrderResponse getOrder(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature,
      @PathParam("order_id") String orderId)
      throws IOException;

  /**
   * Places a V2 event-market order. The request body carries fixed-point dollar price and count
   * strings; the side is {@code bid} (buy YES) or {@code ask} (sell YES).
   */
  @POST
  @Path("portfolio/events/orders")
  KalshiCreateOrderResponse createOrder(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature,
      KalshiOrderRequest request)
      throws IOException;

  /** Cancels an open order by provider order id. */
  @DELETE
  @Path("portfolio/orders/{order_id}")
  KalshiCancelResponse cancelOrder(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature,
      @PathParam("order_id") String orderId)
      throws IOException;

  /** Lists user fills, optionally filtered by ticker or order id. */
  @GET
  @Path("portfolio/fills")
  KalshiFillsResponse getFills(
      @HeaderParam("KALSHI-ACCESS-KEY") String apiKey,
      @HeaderParam("KALSHI-ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("KALSHI-ACCESS-SIGNATURE") ParamsDigest signature,
      @QueryParam("ticker") String ticker,
      @QueryParam("order_id") String orderId,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException;
}
