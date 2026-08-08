package org.knowm.xchange.uniswap.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.uniswap.PoolKeyRegistry;
import org.knowm.xchange.uniswap.TestFixtures;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** Receipt and PoolManager-log decoding (acceptance criterion AC3/AC5). */
class UniswapReceiptDecoderTest {

  private static final PoolKeyRegistry.PoolDefinition POOL =
      TestFixtures.pools().byPair(TestFixtures.ETH_USDC_PAIR);

  @Test
  void decodesSuccessfulSwapIntoFillsAndFee() {
    UniswapOrder order = order();
    TransactionReceipt receipt = TestFixtures.successReceipt(POOL.poolId(), TestFixtures.ROUTER, TestFixtures.POOL_MANAGER);

    UniswapOrder decoded =
        UniswapReceiptDecoder.decode(
            order, receipt, TestFixtures.POOL_MANAGER, POOL.poolId(), TestFixtures.ROUTER, TestFixtures.pools(), TestFixtures.tokens());

    assertThat(decoded.status()).isEqualTo(UniswapOrderStatus.MINED);
    assertThat(decoded.blockNumber()).isEqualTo(BigInteger.valueOf(100));
    // input leg: -1 USDC raw (USDC is currency0, 6 decimals) and +1 ETH raw? fixture uses
    // -1_000_000 raw USDC = -1.0 USDC and +1_000_000_000 raw (10 ETH)
    assertThat(decoded.fills()).hasSize(2);
    UniswapFill usdc = decoded.fills().get(0);
    UniswapFill weth = decoded.fills().get(1);
    assertThat(usdc.currencySymbol()).isEqualTo("USDC");
    assertThat(usdc.amount()).isEqualByComparingTo("-1");
    assertThat(weth.currencySymbol()).isEqualTo("WETH");
    assertThat(weth.amount()).isEqualByComparingTo("0.000000001");
    // cumulative is the base leg (WETH received) and average price = quote/base
    assertThat(decoded.cumulativeAmount()).isEqualByComparingTo("0.000000001");
    assertThat(decoded.averagePrice()).isNotNull();
    // fee: gasUsed 21000 * 1 gwei = 0.000021 ETH
    assertThat(decoded.fee()).isEqualByComparingTo("0.000021");
    assertThat(decoded.note()).isNull();
  }

  @Test
  void decodesRevertedReceiptAsReverted() {
    UniswapOrder order = order();
    TransactionReceipt receipt = TestFixtures.successReceipt(POOL.poolId(), TestFixtures.ROUTER, TestFixtures.POOL_MANAGER);
    receipt.setStatus("0x0");

    UniswapOrder decoded =
        UniswapReceiptDecoder.decode(
            order, receipt, TestFixtures.POOL_MANAGER, POOL.poolId(), TestFixtures.ROUTER, TestFixtures.pools(), TestFixtures.tokens());

    assertThat(decoded.status()).isEqualTo(UniswapOrderStatus.REVERTED);
    assertThat(decoded.fills()).isEmpty();
    assertThat(decoded.note()).contains("reverted");
  }

  @Test
  void ignoresSwapLogsFromOtherPoolsOrRouters() {
    UniswapOrder order = order();
    TransactionReceipt receipt = TestFixtures.successReceipt("0x" + "22".repeat(32), TestFixtures.ROUTER, TestFixtures.POOL_MANAGER);

    UniswapOrder decoded =
        UniswapReceiptDecoder.decode(
            order, receipt, TestFixtures.POOL_MANAGER, POOL.poolId(), TestFixtures.ROUTER, TestFixtures.pools(), TestFixtures.tokens());

    assertThat(decoded.status()).isEqualTo(UniswapOrderStatus.MINED);
    assertThat(decoded.fills()).isEmpty();
  }

  @Test
  void sumsMultipleSwapLogsInOneReceipt() {
    TransactionReceipt receipt = TestFixtures.successReceipt(POOL.poolId(), TestFixtures.ROUTER, TestFixtures.POOL_MANAGER);
    Log second = new Log();
    second.setAddress(TestFixtures.POOL_MANAGER);
    second.setTopics(
        List.of(
            UniswapReceiptDecoder.SWAP_EVENT_TOPIC,
            POOL.poolId(),
            TestFixtures.ROUTER.toLowerCase(),
            "0x" + "00".repeat(32)));
    second.setData(TestFixtures.swapLogData(-500_000L, 500_000_000L));
    receipt.setLogs(List.of(receipt.getLogs().get(0), second));

    UniswapOrder decoded =
        UniswapReceiptDecoder.decode(
            order(), receipt, TestFixtures.POOL_MANAGER, POOL.poolId(), TestFixtures.ROUTER, TestFixtures.pools(), TestFixtures.tokens());

    assertThat(decoded.fills()).hasSize(4);
  }

  @Test
  void twosComplementDecodesNegativeDeltas() {
    assertThat(UniswapReceiptDecoder.twosComplement(TestFixtures.int128(-1))).isEqualTo(BigInteger.valueOf(-1));
    assertThat(UniswapReceiptDecoder.twosComplement(TestFixtures.int128(1))).isEqualTo(BigInteger.ONE);
    assertThat(UniswapReceiptDecoder.twosComplement(TestFixtures.int128(-1_000_000)))
        .isEqualTo(BigInteger.valueOf(-1_000_000));
  }

  @Test
  void splitWordsHandlesEmptyData() {
    assertThat(UniswapReceiptDecoder.splitWords("0x")).isEmpty();
    assertThat(UniswapReceiptDecoder.splitWords(TestFixtures.swapLogData(1, 2))).hasSize(6);
  }

  @Test
  void nullReceiptKeepsOrderState() {
    UniswapOrder order = order();
    UniswapOrder decoded =
        UniswapReceiptDecoder.decode(
            order, null, TestFixtures.POOL_MANAGER, POOL.poolId(), TestFixtures.ROUTER, TestFixtures.pools(), TestFixtures.tokens());
    assertThat(decoded).isSameAs(order);
  }

  private static UniswapOrder order() {
    return new UniswapOrder(
        "0x" + "11".repeat(32),
        TestFixtures.ETH_USDC_PAIR,
        OrderType.BID,
        UniswapOrderStatus.PENDING,
        new BigDecimal("0.001"),
        null,
        null,
        null,
        Instant.now(),
        Instant.now(),
        BigInteger.valueOf(99),
        List.of(),
        null);
  }
}
