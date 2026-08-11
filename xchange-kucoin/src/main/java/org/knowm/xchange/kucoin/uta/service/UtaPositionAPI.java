package org.knowm.xchange.kucoin.uta.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.kucoin.uta.dto.UtaMarginMode;
import org.knowm.xchange.kucoin.uta.dto.UtaPosition;
import org.knowm.xchange.kucoin.uta.dto.UtaPositionHistory;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/** UTA position-domain REST endpoints. */
@Path("api/ua/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UtaPositionAPI {

  /** Open positions; {@code pageSize} max 200; the response {@code data} is a bare array. */
  @GET
  @Path("unified/position/open-list")
  UtaResponse<List<UtaPosition>> getOpenPositions(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("symbol") String symbol,
      @QueryParam("pageNumber") Integer pageNumber,
      @QueryParam("pageSize") Integer pageSize)
      throws IOException;

  /** Current margin mode (CROSS/ISOLATED) per futures symbol. */
  @GET
  @Path("unified/position/margin-mode")
  UtaResponse<UtaMarginMode> getMarginMode(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("symbol") String symbol)
      throws IOException;

  /** Closed-position history; cursor paginated by {@code lastId}, each query bounded to 7 days. */
  @GET
  @Path("position/history")
  UtaResponse<UtaPositionHistory> getPositionHistory(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("symbol") String symbol,
      @QueryParam("startAt") Long startAt,
      @QueryParam("endAt") Long endAt,
      @QueryParam("lastId") Long lastId,
      @QueryParam("pageSize") Integer pageSize)
      throws IOException;
}
