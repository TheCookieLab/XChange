package org.knowm.xchange.kucoin.uta.service;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.kucoin.uta.dto.UtaExecutionHistory;
import org.knowm.xchange.kucoin.uta.dto.UtaOrder;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderCancelRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderHistory;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderPlaceRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderResult;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/** UTA unified order-domain REST endpoints. */
@Path("api/ua/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UtaTradeAPI {

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("unified/order/place")
  UtaResponse<UtaOrderResult> placeOrder(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      UtaOrderPlaceRequest request)
      throws IOException;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("unified/order/cancel")
  UtaResponse<UtaOrderResult> cancelOrder(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      UtaOrderCancelRequest request)
      throws IOException;

  /** Single order detail; used for post-transmission reconciliation. */
  @GET
  @Path("unified/order/detail")
  UtaResponse<UtaOrder> getOrderDetail(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("tradeType") String tradeType,
      @QueryParam("symbol") String symbol,
      @QueryParam("orderId") String orderId,
      @QueryParam("clientOid") String clientOid)
      throws IOException;

  @GET
  @Path("unified/order/history")
  UtaResponse<UtaOrderHistory> getOrderHistory(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("tradeType") String tradeType,
      @QueryParam("symbol") String symbol,
      @QueryParam("side") String side,
      @QueryParam("orderFilter") String orderFilter,
      @QueryParam("startAt") Long startAt,
      @QueryParam("endAt") Long endAt,
      @QueryParam("lastId") Long lastId,
      @QueryParam("pageSize") Integer pageSize)
      throws IOException;

  @GET
  @Path("unified/order/execution")
  UtaResponse<UtaExecutionHistory> getExecutions(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("tradeType") String tradeType,
      @QueryParam("symbol") String symbol,
      @QueryParam("orderId") String orderId,
      @QueryParam("side") String side,
      @QueryParam("startAt") Long startAt,
      @QueryParam("endAt") Long endAt,
      @QueryParam("lastId") Long lastId,
      @QueryParam("pageSize") Integer pageSize)
      throws IOException;
}
