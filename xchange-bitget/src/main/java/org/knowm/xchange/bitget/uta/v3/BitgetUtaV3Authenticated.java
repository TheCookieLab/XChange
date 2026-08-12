package org.knowm.xchange.bitget.uta.v3;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3AccountInfo;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3Asset;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferRequest;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferResult;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferableCoin;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3CursorPage;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Response;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3CancelOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Fill;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3ModifyOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3OrderId;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3PlaceOrderRequest;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Position;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3StrategyOrderRequest;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Bitget UTA v3 private REST interface.
 *
 * <p>Authenticated headers: {@code ACCESS-KEY}, {@code ACCESS-SIGN} ({@link
 * org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3Digest}), {@code ACCESS-PASSPHRASE} and {@code
 * ACCESS-TIMESTAMP} (Unix milliseconds). The signer receives rescu's query string and re-sorts it
 * ascending by key before building the preimage.
 *
 * <p>Typed page shapes ({@link BitgetUtaV3CursorPage}) are used wherever the API paginates with
 * {@code list} + {@code cursor}.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BitgetUtaV3Authenticated {

  // ------------------------------------------------------------------------------------------
  // Account
  // ------------------------------------------------------------------------------------------

  @GET
  @Path("api/v3/account/assets")
  BitgetUtaV3Response<List<BitgetUtaV3Asset>> assets(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("coin") String coin)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/account/info")
  BitgetUtaV3Response<BitgetUtaV3AccountInfo> accountInfo(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/account/transferable-coins")
  BitgetUtaV3Response<List<BitgetUtaV3TransferableCoin>> transferableCoins(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("fromType") String fromType,
      @QueryParam("toType") String toType,
      @QueryParam("coin") String coin,
      @QueryParam("marginCoin") String marginCoin,
      @QueryParam("amount") String amount)
      throws IOException, BitgetUtaV3Exception;

  @POST
  @Path("api/v3/account/transfer")
  @Consumes(MediaType.APPLICATION_JSON)
  BitgetUtaV3Response<BitgetUtaV3TransferResult> transfer(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      BitgetUtaV3TransferRequest request)
      throws IOException, BitgetUtaV3Exception;

  // ------------------------------------------------------------------------------------------
  // Trade
  // ------------------------------------------------------------------------------------------

  @POST
  @Path("api/v3/trade/place-order")
  @Consumes(MediaType.APPLICATION_JSON)
  BitgetUtaV3Response<BitgetUtaV3OrderId> placeOrder(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      BitgetUtaV3PlaceOrderRequest request)
      throws IOException, BitgetUtaV3Exception;

  @POST
  @Path("api/v3/trade/cancel-order")
  @Consumes(MediaType.APPLICATION_JSON)
  BitgetUtaV3Response<BitgetUtaV3OrderId> cancelOrder(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      BitgetUtaV3CancelOrderRequest request)
      throws IOException, BitgetUtaV3Exception;

  @POST
  @Path("api/v3/trade/modify-order")
  @Consumes(MediaType.APPLICATION_JSON)
  BitgetUtaV3Response<BitgetUtaV3OrderId> modifyOrder(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      BitgetUtaV3ModifyOrderRequest request)
      throws IOException, BitgetUtaV3Exception;

  @POST
  @Path("api/v3/trade/place-strategy-order")
  @Consumes(MediaType.APPLICATION_JSON)
  BitgetUtaV3Response<BitgetUtaV3OrderId> placeStrategyOrder(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam("X-CHANNEL-API-CODE") String channelApiCode,
      BitgetUtaV3StrategyOrderRequest request)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/trade/unfilled-orders")
  BitgetUtaV3Response<BitgetUtaV3CursorPage<BitgetUtaV3Order>> unfilledOrders(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/trade/history-orders")
  BitgetUtaV3Response<BitgetUtaV3CursorPage<BitgetUtaV3Order>> historyOrders(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/trade/order-info")
  BitgetUtaV3Response<BitgetUtaV3Order> orderInfo(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("orderId") String orderId,
      @QueryParam("clientOid") String clientOid)
      throws IOException, BitgetUtaV3Exception;

  @GET
  @Path("api/v3/trade/fills")
  BitgetUtaV3Response<BitgetUtaV3CursorPage<BitgetUtaV3Fill>> fills(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("orderId") String orderId,
      @QueryParam("startTime") String startTime,
      @QueryParam("endTime") String endTime,
      @QueryParam("limit") Integer limit,
      @QueryParam("cursor") String cursor)
      throws IOException, BitgetUtaV3Exception;

  // ------------------------------------------------------------------------------------------
  // Position
  // ------------------------------------------------------------------------------------------

  @GET
  @Path("api/v3/position/current-position")
  BitgetUtaV3Response<BitgetUtaV3CursorPage<BitgetUtaV3Position>> currentPositions(
      @HeaderParam("ACCESS-KEY") String apiKey,
      @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
      @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
      @QueryParam("category") String category,
      @QueryParam("symbol") String symbol,
      @QueryParam("posSide") String posSide)
      throws IOException, BitgetUtaV3Exception;
}
