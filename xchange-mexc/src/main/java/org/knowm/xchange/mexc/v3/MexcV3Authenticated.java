package org.knowm.xchange.mexc.v3;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3Account;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3ListenKey;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3ListenKeyList;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3TradeFeeResponse;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3MyTrade;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3Order;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderResponse;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderSide;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderType;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Authenticated MEXC Spot v3 REST surface.
 *
 * <p>Authentication: {@code X-MEXC-APIKEY} header plus a lowercase HMAC-SHA256 {@code signature}
 * over the query string concatenated with the request body. All signed parameters (including
 * {@code timestamp} and {@code recvWindow}) travel in the query string, which the provider accepts
 * for every HTTP method. rescu deserializes error envelopes into {@link MexcV3Exception}.
 */
@Path("/api/v3")
@Produces(MediaType.APPLICATION_JSON)
public interface MexcV3Authenticated {

  @GET
  @Path("/account")
  MexcV3Account account(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @GET
  @Path("/tradeFee")
  MexcV3TradeFeeResponse tradeFee(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @POST
  @Path("/order")
  MexcV3OrderResponse placeOrder(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("side") MexcV3OrderSide side,
      @QueryParam("type") MexcV3OrderType type,
      @QueryParam("quantity") String quantity,
      @QueryParam("quoteOrderQty") String quoteOrderQty,
      @QueryParam("price") String price,
      @QueryParam("newClientOrderId") String newClientOrderId,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @POST
  @Path("/order/test")
  MexcV3OrderResponse placeOrderTest(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("side") MexcV3OrderSide side,
      @QueryParam("type") MexcV3OrderType type,
      @QueryParam("quantity") String quantity,
      @QueryParam("quoteOrderQty") String quoteOrderQty,
      @QueryParam("price") String price,
      @QueryParam("newClientOrderId") String newClientOrderId,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @GET
  @Path("/order")
  MexcV3Order order(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("origClientOrderId") String origClientOrderId,
      @QueryParam("orderId") String orderId,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @DELETE
  @Path("/order")
  MexcV3Order cancelOrder(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("orderId") String orderId,
      @QueryParam("origClientOrderId") String origClientOrderId,
      @QueryParam("newClientOrderId") String newClientOrderId,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @DELETE
  @Path("/openOrders")
  List<MexcV3Order> cancelAllOpenOrders(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @GET
  @Path("/openOrders")
  List<MexcV3Order> openOrders(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @GET
  @Path("/allOrders")
  List<MexcV3Order> allOrders(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("limit") Integer limit,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @GET
  @Path("/myTrades")
  List<MexcV3MyTrade> myTrades(
      @HeaderParam("X-MEXC-APIKEY") String apiKey,
      @QueryParam("symbol") String symbol,
      @QueryParam("orderId") String orderId,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("limit") Integer limit,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("signature") ParamsDigest signature)
      throws IOException, MexcV3Exception;

  @POST
  @Path("/userDataStream")
  MexcV3ListenKey createListenKey(@HeaderParam("X-MEXC-APIKEY") String apiKey)
      throws IOException, MexcV3Exception;

  @GET
  @Path("/userDataStream")
  MexcV3ListenKeyList listListenKeys(@HeaderParam("X-MEXC-APIKEY") String apiKey)
      throws IOException, MexcV3Exception;

  @PUT
  @Path("/userDataStream")
  MexcV3ListenKey keepAliveListenKey(
      @HeaderParam("X-MEXC-APIKEY") String apiKey, @QueryParam("listenKey") String listenKey)
      throws IOException, MexcV3Exception;

  @DELETE
  @Path("/userDataStream")
  MexcV3ListenKey closeListenKey(
      @HeaderParam("X-MEXC-APIKEY") String apiKey, @QueryParam("listenKey") String listenKey)
      throws IOException, MexcV3Exception;
}
