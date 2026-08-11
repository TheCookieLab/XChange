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
import org.knowm.xchange.kucoin.uta.dto.UtaAccountBalance;
import org.knowm.xchange.kucoin.uta.dto.UtaAccountModeResponse;
import org.knowm.xchange.kucoin.uta.dto.UtaAccountOverview;
import org.knowm.xchange.kucoin.uta.dto.UtaFeeRates;
import org.knowm.xchange.kucoin.uta.dto.UtaLedgerEntry;
import org.knowm.xchange.kucoin.uta.dto.UtaModifyLeverageRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaModifyLeverageResult;
import org.knowm.xchange.kucoin.uta.dto.UtaTransferQuota;
import org.knowm.xchange.kucoin.uta.dto.UtaTransferRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaTransferResult;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/** UTA account-domain REST endpoints. */
@Path("api/ua/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UtaAccountAPI {

  /** Probes the credential's account mode; data {@code selfAccountMode} is CLASSIC or UNIFIED. */
  @GET
  @Path("account/mode")
  UtaResponse<UtaAccountModeResponse> getAccountMode(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion)
      throws IOException;

  /** UTA account-level funds summary. */
  @GET
  @Path("unified/account/overview")
  UtaResponse<UtaAccountOverview> getAccountOverview(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion)
      throws IOException;

  /** Currency-level UTA balances including equity, liability and collateral status. */
  @GET
  @Path("unified/account/balance")
  UtaResponse<UtaAccountBalance> getAccountBalance(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion)
      throws IOException;

  /** Transferable balance of a specified account. */
  @GET
  @Path("account/transfer-quota")
  UtaResponse<UtaTransferQuota> getTransferQuota(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("currency") String currency,
      @QueryParam("accountType") String accountType,
      @QueryParam("symbol") String symbol)
      throws IOException;

  /** Flex transfer between master/sub accounts and account types. */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("account/transfer")
  UtaResponse<UtaTransferResult> transfer(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      UtaTransferRequest request)
      throws IOException;

  /** Actual maker/taker fee rates for up to 10 spot symbols or one futures symbol. */
  @GET
  @Path("user/fee-rate")
  UtaResponse<UtaFeeRates> getFeeRate(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("tradeType") String tradeType,
      @QueryParam("symbol") String symbol)
      throws IOException;

  /** Modifies the futures leverage of a symbol. */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("unified/account/modify-leverage")
  UtaResponse<UtaModifyLeverageResult> modifyLeverage(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      UtaModifyLeverageRequest request)
      throws IOException;

  /** Account ledger records; the response {@code data} is a bare array. */
  @GET
  @Path("account/ledger")
  UtaResponse<java.util.List<UtaLedgerEntry>> getLedger(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion,
      @QueryParam("accountType") String accountType,
      @QueryParam("currency") String currency,
      @QueryParam("direction") String direction,
      @QueryParam("businessType") String businessType,
      @QueryParam("lastId") Long lastId,
      @QueryParam("startAt") Long startAt,
      @QueryParam("endAt") Long endAt,
      @QueryParam("pageSize") Integer pageSize)
      throws IOException;
}
