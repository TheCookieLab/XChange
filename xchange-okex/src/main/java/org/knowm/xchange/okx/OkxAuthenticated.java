package org.knowm.xchange.okx;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxAccountConfig;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;
import org.knowm.xchange.okx.dto.account.OkxAssetBalance;
import org.knowm.xchange.okx.dto.account.OkxBillDetails;
import org.knowm.xchange.okx.dto.account.OkxChangeMarginRequest;
import org.knowm.xchange.okx.dto.account.OkxChangeMarginResponse;
import org.knowm.xchange.okx.dto.account.OkxDepositAddress;
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageRequest;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageResponse;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalRequest;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse;
import org.knowm.xchange.okx.dto.account.PiggyBalance;
import org.knowm.xchange.okx.dto.marketdata.OkxCurrency;
import org.knowm.xchange.okx.dto.subaccount.OkxSubAccountDetails;
import org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import si.mazi.rescu.ParamsDigest;

@Path("/api/v5")
@Produces(MediaType.APPLICATION_JSON)
public interface OkxAuthenticated extends Okx {
  String balancePath = "/account/balance"; // Stated as 10 req/2 sec
  String tradeFeePath = "/account/trade-fee"; // Stated as 5 req/2 sec
  String configPath = "/account/config"; // Stated as 5 req/2 sec
  String getBillsPath = "/account/bills"; // Stated as 6 req/sec
  String changeMarginPath = "/account/position/margin-balance"; // Stated as 20 req/2 sec
  String currenciesPath = "/asset/currencies"; // Stated as 6 req/sec
  String assetBalancesPath = "/asset/balances"; // Stated as 6 req/sec
  String assetWithdrawalPath = "/asset/withdrawal"; // Stated as 6 req/sec
  String positionsPath = "/account/positions"; // Stated as 10 req/2 sec
  String accountPositionAtRiskPath = "/account/account-position-risk"; // Stated as 10 req/2 sec
  String setLeveragePath = "/account/set-leverage"; // Stated as 20 req/2 sec
  String pendingOrdersPath = "/trade/orders-pending"; // Stated as 20 req/2 sec
  String orderDetailsPath = "/trade/order";
  String placeOrderPath = "/trade/order"; // Stated as 60 req/2 sec
  String placeBatchOrderPath = "/trade/batch-orders"; // Stated as 300 req/2 sec
  String cancelOrderPath = "/trade/cancel-order"; // Stated as 60 req/2 sec
  String cancelBatchOrderPath = "/trade/cancel-batch-orders"; // Stated as 300 req/2 sec
  String amendOrderPath = "/trade/amend-order"; // Stated as 60 req/2 sec
  String amendBatchOrderPath = "trade/amend-batch-orders"; // Stated as 300 req/2 sec
  String depositAddressPath = "/asset/deposit-address"; // Stated as 6 req/sec
  String ordersHistoryPath = "/trade/orders-history"; // Stated as 40 req/2 sec
  String subAccountList = "/users/subaccount/list"; // Stated as 2 req/2 sec
  String subAccountBalance = "/account/subaccount/balances"; // Stated as 2 req/2 sec
  String piggyBalance = "/asset/piggy-balance"; // Stated as 6 req/1 sec

  // To avoid 429s, actual req/second may need to be lowered!
  Map<String, List<Integer>> privatePathRateLimits =
      new HashMap<String, List<Integer>>() {
        {
          put(balancePath, Arrays.asList(5, 1));
          put(currenciesPath, Arrays.asList(6, 1));
          put(assetBalancesPath, Arrays.asList(6, 1));
          put(positionsPath, Arrays.asList(5, 1));
          put(setLeveragePath, Arrays.asList(20, 2));
          put(pendingOrdersPath, Arrays.asList(20, 2));
          put(orderDetailsPath, Arrays.asList(60, 2));
          put(placeOrderPath, Arrays.asList(60, 2));
          put(placeBatchOrderPath, Arrays.asList(300, 2));
          put(cancelOrderPath, Arrays.asList(60, 2));
          put(cancelBatchOrderPath, Arrays.asList(300, 2));
          put(amendOrderPath, Arrays.asList(60, 2));
          put(amendBatchOrderPath, Arrays.asList(300, 2));
          put(depositAddressPath, Arrays.asList(6, 1));
          put(ordersHistoryPath, Arrays.asList(40, 2));
          put(tradeFeePath, Arrays.asList(5, 2));
          put(configPath, Arrays.asList(5, 2));
          put(getBillsPath, Arrays.asList(6, 1));
          put(changeMarginPath, Arrays.asList(20, 2));
          put(subAccountList, Arrays.asList(2, 2));
          put(subAccountBalance, Arrays.asList(2, 2));
          put(piggyBalance, Arrays.asList(6, 1));
        }
      };

  @GET
  @Path(tradeFeePath)
  OkxResponse<List<OkxTradeFee>> getTradeFee(
      @QueryParam("instType") String instrumentType,
      @QueryParam("instId") String instrumentId,
      @QueryParam("uly") String underlying,
      @QueryParam("instFamily") String instFamily,
      @QueryParam("ruleType") String ruleType,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading);

  @GET
  @Path(configPath)
  OkxResponse<List<OkxAccountConfig>> getAccountConfiguration(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws OkxException, IOException;

  @GET
  @Path(getBillsPath)
  OkxResponse<List<OkxBillDetails>> getBills(
      @QueryParam("instType") String instrumentType,
      @QueryParam("ccy") String currency,
      @QueryParam("mgnMode") String marginMode,
      @QueryParam("ctType") String contractType,
      @QueryParam("type") String billType,
      @QueryParam("subType") String billSubType,
      @QueryParam("after") String afterBillId,
      @QueryParam("before") String beforeBillId,
      @QueryParam("begin") String beginTimestamp,
      @QueryParam("end") String endTimestamp,
      @QueryParam("limit") String maxNumberOfResults,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws OkxException, IOException;

  @POST
  @Path(changeMarginPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxChangeMarginResponse>> changeMargin(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      OkxChangeMarginRequest requestPayload)
      throws OkxException, IOException;

  @GET
  @Path(ordersHistoryPath)
  OkxResponse<List<OkxOrderDetails>> getOrderHistory(
      @QueryParam("instType") String instType,
      @QueryParam("instId") String instrumentId,
      @QueryParam("ordType") String orderType,
      @QueryParam("state") String state,
      @QueryParam("after") String after,
      @QueryParam("before") String before,
      @QueryParam("limit") String limit,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading);

  @GET
  @Path(depositAddressPath)
  OkxResponse<List<OkxDepositAddress>> getDepositAddress(
      @QueryParam("ccy") String currency,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(balancePath)
  OkxResponse<List<OkxWalletBalance>> getWalletBalances(
      @QueryParam("ccy") List<Currency> currencies,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(currenciesPath)
  OkxResponse<List<OkxCurrency>> getCurrencies(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws OkxException, IOException;

  @GET
  @Path(assetBalancesPath)
  OkxResponse<List<OkxAssetBalance>> getAssetBalances(
      @QueryParam("ccy") List<Currency> currencies,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws OkxException, IOException;

  @POST
  @Path(assetWithdrawalPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxWithdrawalResponse>> assetWithdrawal(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      OkxWithdrawalRequest requestPayload)
      throws OkxException, IOException;

  @GET
  @Path(positionsPath)
  OkxResponse<List<OkxPosition>> getPositions(
      @QueryParam("instType") String instrumentType,
      @QueryParam("instId") String instrumentId,
      @QueryParam("posId") String positionId,
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @GET
  @Path(accountPositionAtRiskPath)
  OkxResponse<List<OkxAccountPositionRisk>> getAccountPositionRisk(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading)
      throws IOException, OkxException;

  @POST
  @Path(setLeveragePath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxSetLeverageResponse>> setLeverage(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      OkxSetLeverageRequest requestPayload)
      throws IOException, OkxException;

  @GET
  @Path(pendingOrdersPath)
  OkxResponse<List<OkxOrderDetails>> getPendingOrders(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      @QueryParam("instType") String instrumentType,
      @QueryParam("uly") String underlying,
      @QueryParam("instId") String instrumentId,
      @QueryParam("ordType") String orderType,
      @QueryParam("state") String state,
      @QueryParam("after") String after,
      @QueryParam("before") String before,
      @QueryParam("limit") String limit)
      throws OkxException, IOException;

  @GET
  @Path(orderDetailsPath)
  OkxResponse<List<OkxOrderDetails>> getOrderDetails(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      @QueryParam("instId") String instrumentId,
      @QueryParam("ordId") String orderId,
      @QueryParam("clOrdId") String clientOrderId)
      throws OkxException, IOException;

  @GET
  @Path(subAccountList)
  OkxResponse<List<OkxSubAccountDetails>> getSubAccountList(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      @QueryParam("enable") String enable,
      @QueryParam("subAcct") String subAcct)
      throws OkxException, IOException;

  @GET
  @Path(subAccountBalance)
  OkxResponse<List<OkxWalletBalance>> getSubAccountBalance(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      @QueryParam("subAcct") String subAcct)
      throws OkxException, IOException;

  @GET
  @Path(piggyBalance)
  OkxResponse<List<PiggyBalance>> getPiggyBalance(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      @QueryParam("ccy") String ccy)
      throws OkxException, IOException;

  @POST
  @Path(placeOrderPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxOrderResponse>> placeOrder(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      OkxOrderRequest requestPayload)
      throws OkxException, IOException;

  @POST
  @Path(placeBatchOrderPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxOrderResponse>> placeBatchOrder(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      List<OkxOrderRequest> requestPayload)
      throws OkxException, IOException;

  @POST
  @Path(cancelOrderPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxOrderResponse>> cancelOrder(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      OkxCancelOrderRequest requestPayload)
      throws OkxException, IOException;

  @POST
  @Path(cancelBatchOrderPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxOrderResponse>> cancelBatchOrder(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      List<OkxCancelOrderRequest> requestPayload)
      throws OkxException, IOException;

  @POST
  @Path(amendOrderPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxOrderResponse>> amendOrder(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      OkxAmendOrderRequest requestPayload)
      throws OkxException, IOException;

  @POST
  @Path(amendBatchOrderPath)
  @Consumes(MediaType.APPLICATION_JSON)
  OkxResponse<List<OkxOrderResponse>> amendBatchOrder(
      @HeaderParam("OK-ACCESS-KEY") String apiKey,
      @HeaderParam("OK-ACCESS-SIGN") ParamsDigest signature,
      @HeaderParam("OK-ACCESS-TIMESTAMP") String timestamp,
      @HeaderParam("OK-ACCESS-PASSPHRASE") String passphrase,
      @HeaderParam("X-SIMULATED-TRADING") String simulatedTrading,
      List<OkxAmendOrderRequest> requestPayload)
      throws OkxException, IOException;
}
