package org.knowm.xchange.binance.wallet;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.binance.dto.BinanceException;
import org.knowm.xchange.binance.dto.account.AssetDetail;
import org.knowm.xchange.binance.dto.account.AssetDividendResponse;
import org.knowm.xchange.binance.dto.account.BinanceCurrencyInfo;
import org.knowm.xchange.binance.dto.account.BinanceDeposit;
import org.knowm.xchange.binance.dto.account.BinanceFiatOrdersResponse;
import org.knowm.xchange.binance.dto.account.BinanceFlexiblePositionResponse;
import org.knowm.xchange.binance.dto.account.BinanceLockedPositionResponse;
import org.knowm.xchange.binance.dto.account.BinanceSimpleAccount;
import org.knowm.xchange.binance.dto.account.BinanceTradeFee;
import org.knowm.xchange.binance.dto.account.BinanceWithdraw;
import org.knowm.xchange.binance.dto.account.DepositAddress;
import org.knowm.xchange.binance.dto.account.TransferHistory;
import org.knowm.xchange.binance.dto.account.TransferSubUserHistory;
import org.knowm.xchange.binance.dto.account.WithdrawResponse;
import org.knowm.xchange.binance.dto.meta.BinanceSystemStatus;
import org.knowm.xchange.binance.dto.trade.BinanceDustLog;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Wallet and SAPI endpoints ({@code /sapi/v1/*}).
 *
 * <p>This interface is the explicit owner of the Wallet/SAPI surface: system status, deposits,
 * withdrawals, dust conversion, dividends, transfers, fiat orders, asset details, trade fees, and
 * simple-earn positions. Secrets and withdrawal addresses returned by these endpoints must be
 * redacted before they reach logs or exception messages.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BinanceWalletApi {

  String SIGNATURE = "signature";
  String X_MBX_APIKEY = "X-MBX-APIKEY";

  /**
   * Fetch system status which is normal or system maintenance.
   *
   * @throws IOException
   */
  @GET
  @Path("sapi/v1/system/status")
  BinanceSystemStatus systemStatus() throws IOException;

  /**
   * Retrieves the dust log from Binance. If you have many currencies with low amount (=dust) that
   * cannot be traded, because their amount is less than the minimum amount required for trading
   * them, you can convert all these currencies at once into BNB with the button "Convert Small
   * Balance to BNB".
   *
   * @param startTime optional. If set, also the endTime must be set. If neither time is set, the
   *     100 most recent dust logs are returned.
   * @param endTime optional. If set, also the startTime must be set. If neither time is set, the
   *     100 most recent dust logs are returned.
   * @param recvWindow optional
   * @param timestamp mandatory
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/asset/dribblet")
  BinanceDustLog getDustLog(
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  @GET
  @Path("/sapi/v1/capital/config/getall")
  List<BinanceCurrencyInfo> getCurrencyInfos(
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Submit a withdraw request.
   *
   * @param coin
   * @param address
   * @param addressTag optional for Ripple
   * @param amount
   * @param name optional, description of the address
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @POST
  @Path("/sapi/v1/capital/withdraw/apply")
  WithdrawResponse withdraw(
      @QueryParam("coin") String coin,
      @QueryParam("address") String address,
      @QueryParam("addressTag") String addressTag,
      @QueryParam("amount") BigDecimal amount,
      @QueryParam("name") String name,
      @QueryParam("network") String network,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch deposit history.
   *
   * @param coin optional
   * @param startTime optional
   * @param endTime optional
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/capital/deposit/hisrec")
  List<BinanceDeposit> depositHistory(
      @QueryParam("coin") String coin,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch withdraw history.
   *
   * @param coin optional
   * @param startTime optional
   * @param endTime optional
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/capital/withdraw/history")
  List<BinanceWithdraw> withdrawHistory(
      @QueryParam("coin") String coin,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch small amounts of assets exchanged BNB records.
   *
   * @param asset optional
   * @param startTime optional
   * @param endTime optional
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/asset/assetDividend")
  AssetDividendResponse assetDividend(
      @QueryParam("asset") String asset,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  @GET
  @Path("/sapi/v1/sub-account/sub/transfer/history")
  List<TransferHistory> transferHistory(
      @QueryParam("fromEmail") String fromEmail,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("page") Integer page,
      @QueryParam("limit") Integer limit,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  @GET
  @Path("/sapi/v1/sub-account/transfer/subUserHistory")
  List<TransferSubUserHistory> transferSubUserHistory(
      @QueryParam("asset") String asset,
      @QueryParam("type") Integer type,
      @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("limit") Integer limit,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch fiat deposit/withdraw history.
   *
   * @param transactionType 0-deposit, 1-withdraw
   * @param beginTime optional
   * @param endTime optional
   * @param page optional, default 1
   * @param rows optional, default 100, max 500
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/fiat/orders")
  BinanceFiatOrdersResponse fiatOrders(
      @QueryParam("transactionType") String transactionType,
      @QueryParam("beginTime") Long beginTime,
      @QueryParam("endTime") Long endTime,
      @QueryParam("page") Integer page,
      @QueryParam("rows") Integer rows,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch deposit address.
   *
   * @param coin
   * @param recvWindow
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/capital/deposit/address")
  DepositAddress depositAddress(
      @QueryParam("coin") String coin,
      @QueryParam("network") String network,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch asset details.
   *
   * @param recvWindow
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/asset/assetDetail")
  Map<String, AssetDetail> assetDetail(
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Fetch trade fee for all Binance SPOT instruments
   *
   * @param symbol optional
   * @param recvWindow
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   */
  @GET
  @Path("/sapi/v1/asset/tradeFee")
  List<BinanceTradeFee> getTradeFee(
      @QueryParam("symbol") Long symbol,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Get Simple Earn account summary.
   *
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   * @see <a href="https://binance-docs.github.io/apidocs/spot/en/#simple-account-user_data">Simple
   *     Account - Spot API docs</a>
   */
  @GET
  @Path("/sapi/v1/simple-earn/account")
  BinanceSimpleAccount simpleAccount(
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Get Flexible Product Position.
   *
   * @param asset optional
   * @param productId optional
   * @param current optional, currently querying the page. Start from 1. Default:1
   * @param size optional, Default:10, Max:100
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   * @see <a
   *     href="https://binance-docs.github.io/apidocs/spot/en/#get-flexible-product-position-user_data">Get
   *     Flexible Product Position - Spot API docs</a>
   */
  @GET
  @Path("/sapi/v1/simple-earn/flexible/position")
  BinanceFlexiblePositionResponse flexiblePosition(
      @QueryParam("asset") String asset,
      @QueryParam("productId") String productId,
      @QueryParam("current") Long current,
      @QueryParam("size") Long size,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;

  /**
   * Get Locked Product Position.
   *
   * @param asset optional
   * @param positionId optional
   * @param projectId optional
   * @param current optional, currently querying the page. Start from 1. Default:1
   * @param size optional, Default:10, Max:100
   * @param recvWindow optional
   * @param timestamp
   * @param apiKey
   * @param signature
   * @return
   * @throws IOException
   * @throws BinanceException
   * @see <a
   *     href="https://binance-docs.github.io/apidocs/spot/en/#get-locked-product-position-user_data">Get
   *     Locked Product Position - Spot API docs</a>
   */
  @GET
  @Path("/sapi/v1/simple-earn/locked/position")
  BinanceLockedPositionResponse lockedPosition(
      @QueryParam("asset") String asset,
      @QueryParam("positionId") Long positionId,
      @QueryParam("projectId") String projectId,
      @QueryParam("current") Long current,
      @QueryParam("size") Long size,
      @QueryParam("recvWindow") Long recvWindow,
      @QueryParam("timestamp") SynchronizedValueFactory<Long> timestamp,
      @HeaderParam(X_MBX_APIKEY) String apiKey,
      @QueryParam(SIGNATURE) ParamsDigest signature)
      throws IOException, BinanceException;
}
