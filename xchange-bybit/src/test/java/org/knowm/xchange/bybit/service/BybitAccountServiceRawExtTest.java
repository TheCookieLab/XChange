package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import org.junit.Test;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.account.BybitBorrowHistory;
import org.knowm.xchange.bybit.dto.account.BybitBorrowableAmount;
import org.knowm.xchange.bybit.dto.account.BybitCoinInfo;
import org.knowm.xchange.bybit.dto.account.BybitCollateralInfo;
import org.knowm.xchange.bybit.dto.account.BybitTransactionLog;
import org.knowm.xchange.bybit.dto.account.BybitTransferResponse;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;

public class BybitAccountServiceRawExtTest extends BaseWiremockTest {

  @Test
  public void transactionLogPreservesExactDecimals() throws IOException {
    initGetStub(
        "/v5/account/transaction-log",
        "/getTransactionLog.json5",
        "accountType",
        equalTo("UNIFIED"));
    initGetStub(
        "/v5/account/transaction-log",
        "/getTransactionLog.json5",
        "category",
        equalTo("linear"));

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    BybitResult<org.knowm.xchange.bybit.dto.BybitCategorizedPayload<BybitTransactionLog>> result =
        raw.getTransactionLog(
            BybitAccountType.UNIFIED,
            BybitCategory.LINEAR,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertTrue(result.isSuccess());
    assertEquals("", result.getResult().getNextPageCursor());
    java.util.List<BybitTransactionLog> list = result.getResult().getList();
    assertEquals(2, list.size());
    BybitTransactionLog trade = list.get(0);
    assertEquals("1111111111111111111", trade.getId());
    assertEquals("TRADE", trade.getType());
    assertEquals("0.001", trade.getAmount());
    assertEquals("0.0000001", trade.getFee());
    assertEquals("1.23456789", trade.getCashBalance());
    assertEquals("65432.1", trade.getExecPrice());
    assertEquals("1672304894063", trade.getTradeTime());
    assertEquals("10", trade.getLeverage());
    assertEquals(
        new BigDecimal("0.0000001"), new BigDecimal(trade.getFee())); // exact string preserved
    BybitTransactionLog settlement = list.get(1);
    assertEquals("SETTLEMENT", settlement.getType());
    assertEquals("12.345678901234", settlement.getClosedPnl());
  }

  @Test
  public void interTransferReturnsTransferIdAndStatus() throws IOException {
    initPostStub("/v5/asset/transfer/inter-transfer", "/interTransfer.json5");

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    BybitResult<BybitTransferResponse> result =
        raw.interTransfer(
            "8888888888888888888",
            "USDT",
            "100.5",
            BybitAccountType.UNIFIED.name(),
            BybitAccountType.UNIFIED.name());

    assertTrue(result.isSuccess());
    assertEquals("8888888888888888888", result.getResult().getTransferId());
    assertEquals("SUCCESS", result.getResult().getStatus());
  }

  @Test
  public void collateralInfoParses() throws IOException {
    initGetStub(
        "/v5/account/collateral-info",
        "/getCollateralInfo.json5",
        "currency",
        equalTo("BTC"));

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    java.util.List<BybitCollateralInfo> list =
        raw.getCollateralInfo("BTC").getResult().getList();

    assertEquals(1, list.size());
    BybitCollateralInfo info = list.get(0);
    assertEquals("BTC", info.getCurrency());
    assertEquals("0.00000667", info.getHourlyBorrowRate());
    assertEquals("10.5", info.getMaxBorrowAmount());
    assertEquals("7.25", info.getAvailableToBorrow());
    assertEquals("0.95", info.getCollateralRatio());
    assertEquals("0.0001", info.getMinCollateralAmount());
    assertEquals("OK", info.getStatus());
  }

  @Test
  public void borrowHistoryParses() throws IOException {
    initGetStub(
        "/v5/account/borrow-history", "/getBorrowHistory.json5", "currency", equalTo("BTC"));

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    java.util.List<BybitBorrowHistory> list =
        raw.getBorrowHistory("BTC", null, null, null, null).getResult().getList();

    assertEquals(1, list.size());
    BybitBorrowHistory history = list.get(0);
    assertEquals("BTC", history.getCurrency());
    assertEquals("1672304894063", history.getCreatedTime());
    assertEquals("0.00000001", history.getBorrowCost());
    assertEquals("0.5", history.getBorrowAmount());
    assertEquals("0.25", history.getRepaidAmount());
    assertEquals("9999999999999999999", history.getBorrowOrderId());
    assertEquals("collateral", history.getBorrowType());
  }

  @Test
  public void borrowableAmountParses() throws IOException {
    initGetStub(
        "/v5/spot-margin-trade/max-borrowable",
        "/getBorrowableAmount.json5",
        "currency",
        equalTo("BTC"));

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    BybitBorrowableAmount amount = raw.getBorrowableAmount("BTC").getResult();

    assertNotNull(amount);
    assertEquals("BTC", amount.getCurrency());
    assertEquals("17.54689892", amount.getMaxLoan());
  }

  @Test
  public void coinInfoParsesChains() throws IOException {
    initGetStub("/v5/asset/coin/query-info", "/getCoinInfo.json5", "coin", equalTo("BTC"));

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    java.util.List<BybitCoinInfo> rows = raw.getCoinInfo("BTC").getResult().getRows();

    assertEquals(1, rows.size());
    BybitCoinInfo coin = rows.get(0);
    assertEquals("BTC", coin.getCoin());
    assertEquals("0.123456789", coin.getRemainAmount());
    assertEquals(2, coin.getChains().size());
    BybitCoinInfo.BybitCoinChain bsc = coin.getChains().get(1);
    assertEquals("BSC", bsc.getChain());
    assertEquals("BEP20", bsc.getChainType());
    assertEquals("0.0001", bsc.getWithdrawFee());
    assertEquals("0.0001", bsc.getDepositMin());
    assertEquals("0.0001", bsc.getWithdrawMin());
    assertEquals("8", bsc.getMinAccuracy());
    assertEquals("1", bsc.getChainDeposit());
    assertEquals("0", bsc.getChainWithdraw());
    assertEquals("0.022", bsc.getWithdrawPercentageFee());
    assertEquals("15", bsc.getSafeConfirmNumber());
    assertEquals("-1", bsc.getWithdrawMax());
    assertEquals("15", bsc.getConfirmation());
    assertEquals("0x7130d2a12b9bcbfae4f2634d864a1ee1ce3ead9c", bsc.getContractAddress());
  }
}
