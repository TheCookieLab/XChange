package org.knowm.xchange.uniswap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.uniswap.client.UniswapNodeClient;
import org.knowm.xchange.uniswap.dto.UniswapOrder;
import org.knowm.xchange.uniswap.dto.UniswapOrderStatus;
import org.knowm.xchange.uniswap.dto.UniswapQuote;
import org.knowm.xchange.uniswap.protocol.Abi;
import org.knowm.xchange.uniswap.protocol.UniswapAbiEncoder;
import org.knowm.xchange.uniswap.service.UniswapTradeService;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * End-to-end order flow against a mocked JSON-RPC client: quote limits, bounded Permit2 approval,
 * exact protocol calldata, precomputed hashes, at-most-once broadcast, and receipt reconciliation
 * (AC3, AC5).
 */
class UniswapTradeServiceTest {

  @TempDir Path tempDir;

  private static final char[] PASSWORD = "s3cret".toCharArray();
  private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);
  private static final PoolKeyRegistry.PoolDefinition POOL =
      TestFixtures.pools().byPair(TestFixtures.ETH_USDC_PAIR);

  private final byte[] quoteInSelector =
      Arrays.copyOfRange(
          UniswapAbiEncoder.quoteExactInputSingleCalldata(POOL.encodedPoolKey(), false, BigInteger.ONE, new byte[0]), 0, 4);
  private final byte[] quoteOutSelector =
      Arrays.copyOfRange(
          UniswapAbiEncoder.quoteExactOutputSingleCalldata(POOL.encodedPoolKey(), false, BigInteger.ONE, new byte[0]), 0, 4);
  private final byte[] allowanceSelector =
      Arrays.copyOfRange(
          UniswapAbiEncoder.permit2AllowanceCalldata(TestFixtures.WALLET, TestFixtures.WETH, TestFixtures.ROUTER), 0, 4);
  private final byte[] balanceOfSelector =
      Arrays.copyOfRange(UniswapAbiEncoder.balanceOfCalldata(TestFixtures.WALLET), 0, 4);

  private UniswapNodeClient client;
  private UniswapExchange exchange;
  private UniswapTradeService trade;

  /** Broadcast transactions in order: (approve?, signed bytes). */
  private final List<byte[]> broadcast = new ArrayList<>();
  private final AtomicInteger approvalReceiptPolls = new AtomicInteger();
  private BigInteger allowanceAmount = BigInteger.ONE.shiftLeft(160).subtract(BigInteger.ONE);

  @BeforeEach
  void setUp() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, PASSWORD);
    client = mock(UniswapNodeClient.class);

    when(client.blockNumber()).thenReturn(BigInteger.valueOf(100));
    when(client.pendingTransactionCount(anyString())).thenReturn(BigInteger.valueOf(5));
    when(client.baseFeePerGas()).thenReturn(GWEI.multiply(BigInteger.TEN));
    when(client.priorityFeePerGas()).thenReturn(GWEI);
    when(client.estimateGas(anyString(), anyString(), any(byte[].class))).thenReturn(BigInteger.valueOf(300_000));
    when(client.tokenBalance(anyString(), anyString(), any(BigInteger.class)))
        .thenReturn(BigInteger.TEN.pow(24));

    when(client.call(anyString(), anyString(), any(byte[].class), any(BigInteger.class)))
        .thenAnswer(
            invocation -> {
              byte[] data = invocation.getArgument(2);
              if (startsWith(data, quoteInSelector) || startsWith(data, quoteOutSelector)) {
                // (amountOut|amountIn = 2_000_000, gasEstimate = 150_000)
                return words(BigInteger.valueOf(2_000_000), BigInteger.valueOf(150_000));
              }
              if (startsWith(data, allowanceSelector)) {
                return words(allowanceAmount, BigInteger.ZERO, BigInteger.ZERO);
              }
              if (startsWith(data, balanceOfSelector)) {
                return words(BigInteger.TEN.pow(24));
              }
              throw new AssertionError("unexpected eth_call calldata: " + Abi.toHex(data));
            });

    when(client.sendRawTransaction(any(byte[].class)))
        .thenAnswer(
            invocation -> {
              byte[] signed = invocation.getArgument(0);
              broadcast.add(signed);
              return "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(Hash.sha3(signed));
            });

    when(client.transactionByHash(anyString())).thenReturn(Optional.empty());
    when(client.transactionReceipt(anyString())).thenReturn(Optional.empty());

    exchange = new UniswapExchange();
    exchange.setNodeClientForTesting(client);
    exchange.applySpecification(TestFixtures.specification(keystore));
    trade = (UniswapTradeService) exchange.getTradeService();
  }

  @Test
  void asKOrderBroadcastsExactInputSwapWithSlippageLimit() throws Exception {
    UniswapOrder order =
        trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR));

    assertThat(order.status()).isEqualTo(UniswapOrderStatus.PENDING);
    assertThat(order.orderId()).startsWith("0x").hasSize(66);
    assertThat(broadcast).hasSize(1);

    ParsedExecute parsed = parseExecute(broadcast.get(0));
    assertThat(parsed.commands).containsExactly(UniswapAbiEncoder.COMMAND_V4_SWAP);
    assertThat(parsed.actions).containsExactly(
        UniswapAbiEncoder.ACTION_SWAP_EXACT_IN_SINGLE, UniswapAbiEncoder.ACTION_SETTLE, UniswapAbiEncoder.ACTION_TAKE);
    // settle input from the wallet: WETH, full open delta, payerIsUser
    assertThat(parsed.settleCurrency).isEqualTo(TestFixtures.WETH.toLowerCase());
    assertThat(parsed.settleAmount).isEqualTo(BigInteger.ZERO);
    assertThat(parsed.settlePayerIsUser).isTrue();
    // take output to the caller: USDC, full open credit
    assertThat(parsed.takeCurrency).isEqualTo(TestFixtures.USDC.toLowerCase());
    assertThat(parsed.takeRecipient).isEqualTo(UniswapAbiEncoder.MSG_SENDER);
    // swap: exact input 0.001 ETH raw, minimum out = 2_000_000 * 99% = 1_980_000
    assertThat(parsed.swapAmountSpecified).isEqualTo(BigInteger.valueOf(10L).pow(15));
    assertThat(parsed.swapLimit).isEqualTo(BigInteger.valueOf(1_980_000));
    assertThat(parsed.swapZeroForOne).isFalse();
    // deadline is now + 600s
    long now = System.currentTimeMillis() / 1000;
    assertThat(parsed.deadline.longValue()).isBetween(now + 590, now + 610);
  }

  @Test
  void bidOrderBroadcastsExactOutputSwap() throws Exception {
    UniswapOrder order =
        trade.submitMarketOrder(new MarketOrder(OrderType.BID, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR));

    ParsedExecute parsed = parseExecute(broadcast.get(0));
    assertThat(parsed.actions.get(0)).isEqualTo(UniswapAbiEncoder.ACTION_SWAP_EXACT_OUT_SINGLE);
    // exact output 0.001 ETH raw; max input = 2_000_000 * 101% = 2_020_000 (USDC in)
    assertThat(parsed.swapAmountSpecified).isEqualTo(BigInteger.valueOf(10L).pow(15));
    assertThat(parsed.swapLimit).isEqualTo(BigInteger.valueOf(2_020_000));
    assertThat(parsed.swapZeroForOne).isTrue();
    assertThat(parsed.settleCurrency).isEqualTo(TestFixtures.USDC.toLowerCase());
    assertThat(parsed.takeCurrency).isEqualTo(TestFixtures.WETH.toLowerCase());
    assertThat(order.status()).isEqualTo(UniswapOrderStatus.PENDING);
  }

  @Test
  void rejectsAStaleReferenceQuote() throws Exception {
    UniswapQuote stale =
        new UniswapQuote(
            TestFixtures.ETH_USDC_PAIR,
            true,
            new java.math.BigDecimal("0.001"),
            new java.math.BigDecimal("2"),
            BigInteger.valueOf(10L).pow(15),
            BigInteger.valueOf(2_000_000),
            BigInteger.valueOf(100),
            java.time.Instant.now().minusSeconds(120),
            BigInteger.valueOf(150_000));
    assertThatThrownBy(
            () ->
                trade.submitMarketOrder(
                    new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR), stale))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("120s old");
    assertThat(broadcast).isEmpty();
  }

  @Test
  void rejectsInsufficientInputBalance() throws Exception {
    when(client.tokenBalance(anyString(), anyString(), any(BigInteger.class)))
        .thenReturn(BigInteger.valueOf(1_000));
    assertThatThrownBy(
            () ->
                trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR)))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("insufficient");
    assertThat(broadcast).isEmpty();
  }

  @Test
  void rejectsGasEstimateAboveConfiguredCap() throws Exception {
    when(client.estimateGas(anyString(), anyString(), any(byte[].class)))
        .thenReturn(BigInteger.valueOf(2_000_000));
    assertThatThrownBy(
            () ->
                trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR)))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("gas estimate");
    assertThat(broadcast).isEmpty();
  }

  @Test
  void ambiguousBroadcastWithNoNodeRecordYieldsUnknownOrder() throws Exception {
    when(client.sendRawTransaction(any(byte[].class)))
        .thenThrow(new java.io.IOException("connection reset"));
    when(client.transactionByHash(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR)))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("must be reconciled");
    // never retried: exactly one broadcast attempt
    assertThat(broadcast).isEmpty();
  }

  @Test
  void ambiguousBroadcastWithNodeRecordYieldsPendingOrder() throws Exception {
    when(client.sendRawTransaction(any(byte[].class)))
        .thenThrow(new java.io.IOException("connection reset"));
    when(client.transactionByHash(anyString()))
        .thenAnswer(
            invocation -> {
              Transaction tx = new Transaction();
              tx.setHash(invocation.getArgument(0));
              tx.setFrom(TestFixtures.WALLET);
              tx.setTo(TestFixtures.ROUTER);
              tx.setNonce("0x5");
              return Optional.of(tx);
            });

    UniswapOrder order =
        trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR));
    assertThat(order.status()).isEqualTo(UniswapOrderStatus.PENDING);
    assertThat(order.note()).contains("ambiguous");
  }

  @Test
  void boundedPermit2ApprovalPrecedesTheSwapWhenAllowanceIsShort() throws Exception {
    allowanceAmount = BigInteger.ZERO;
    // approval receipt appears on the second poll
    when(client.transactionReceipt(anyString()))
        .thenAnswer(
            invocation -> {
              if (broadcast.size() == 1 && approvalReceiptPolls.incrementAndGet() >= 2) {
                TransactionReceipt receipt = new TransactionReceipt();
                receipt.setStatus("0x1");
                receipt.setTransactionHash(invocation.getArgument(0));
                return Optional.of(receipt);
              }
              return Optional.empty();
            });

    UniswapOrder order =
        trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR));

    assertThat(order.status()).isEqualTo(UniswapOrderStatus.PENDING);
    assertThat(broadcast).hasSize(2); // approve + swap

    // the approve tx carries a bounded amount: needed 10^15 * 105% = 1_050_000_000_000_000,
    // addressed to Permit2 with the router as spender
    byte[] approveData = dataOf(broadcast.get(0));
    BigInteger expectedAmount = BigInteger.valueOf(10L).pow(15).multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100));
    byte[] expectedPrefix =
        Abi.concat(
            Arrays.copyOfRange(
                UniswapAbiEncoder.permit2ApproveCalldata(
                    TestFixtures.WETH.toLowerCase(), TestFixtures.ROUTER.toLowerCase(), expectedAmount, BigInteger.ONE),
                0,
                4 + 32 + 32 + 32));
    assertThat(startsWith(approveData, expectedPrefix)).isTrue();
    // the swap tx is the second broadcast
    ParsedExecute swap = parseExecute(broadcast.get(1));
    assertThat(swap.actions.get(0)).isEqualTo(UniswapAbiEncoder.ACTION_SWAP_EXACT_IN_SINGLE);
  }

  @Test
  void orderStatusReconcilesReceiptIntoMinedWithFills() throws Exception {
    UniswapOrder placed =
        trade.submitMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR));

    Transaction tx = new Transaction();
    tx.setHash(placed.orderId());
    tx.setFrom(TestFixtures.WALLET);
    tx.setTo(TestFixtures.ROUTER);
    tx.setNonce("0x5");
    when(client.transactionByHash(anyString())).thenReturn(Optional.of(tx));
    when(client.transactionReceipt(anyString()))
        .thenReturn(Optional.of(TestFixtures.successReceipt(POOL.poolId(), TestFixtures.ROUTER, TestFixtures.POOL_MANAGER)));

    UniswapOrder mined = trade.getOrderStatus(placed.orderId());

    assertThat(mined.status()).isEqualTo(UniswapOrderStatus.MINED);
    assertThat(mined.fills()).hasSize(2);
    assertThat(mined.fee()).isNotNull();
  }

  @Test
  void orderStatusForUnknownHashIsUnknown() throws Exception {
    UniswapOrder unknown = trade.getOrderStatus("0x" + "ab".repeat(32));
    assertThat(unknown.status()).isEqualTo(UniswapOrderStatus.UNKNOWN);
  }

  @Test
  void standardPlaceMarketOrderReturnsTheTransactionHash() throws Exception {
    String id = trade.placeMarketOrder(new MarketOrder(OrderType.ASK, new java.math.BigDecimal("0.001"), TestFixtures.ETH_USDC_PAIR));
    assertThat(id).startsWith("0x").hasSize(66);
  }

  // ---------------------------------------------------------------------------------------------
  // Calldata parsing and helpers
  // ---------------------------------------------------------------------------------------------

  private static boolean startsWith(byte[] data, byte[] prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] words(BigInteger... values) {
    byte[] out = new byte[values.length * 32];
    for (int i = 0; i < values.length; i++) {
      byte[] raw = values[i].toByteArray();
      System.arraycopy(raw, 0, out, i * 32 + 32 - raw.length, raw.length);
    }
    return out;
  }

  private static final class ParsedExecute {
    List<Byte> commands = new ArrayList<>();
    List<Byte> actions = new ArrayList<>();
    List<byte[]> rawInputs = new ArrayList<>();
    BigInteger deadline;
    BigInteger settleAmount;
    boolean settlePayerIsUser;
    String settleCurrency;
    String takeCurrency;
    String takeRecipient;
    BigInteger swapAmountSpecified;
    BigInteger swapLimit;
    boolean swapZeroForOne;
  }

  /** Parses the execute() calldata and the nested v4 action bundle into assertions. */
  private static ParsedExecute parseExecute(byte[] signed) throws Exception {
    // EIP-1559 signed payload = 0x02 || rlp([chainId, nonce, maxPriority, maxFee, gasLimit,
    // to, value, data, accessList, yParity, r, s])
    List<byte[]> rlp = decodeRlp(signed);
    assertThat(rlp).hasSize(12);
    byte[] data = rlp.get(7);

    // execute(bytes, bytes[], uint256): selector, commands offset, inputs offset, deadline, blobs
    int commandsOffset = wordAt(data, 0).intValueExact();
    int inputsOffset = wordAt(data, 1).intValueExact();
    ParsedExecute parsed = new ParsedExecute();
    parsed.deadline = wordAt(data, 2);
    // ABI offsets are relative to the argument area, which starts after the 4-byte selector
    int commandsStart = 4 + commandsOffset;
    int inputsStart = 4 + inputsOffset;
    int commandsLength = new BigInteger(Arrays.copyOfRange(data, commandsStart, commandsStart + 32)).intValueExact();
    for (int i = 0; i < commandsLength; i++) {
      parsed.commands.add(data[commandsStart + 32 + i]);
    }
    int inputsLength = new BigInteger(Arrays.copyOfRange(data, inputsStart, inputsStart + 32)).intValueExact();
    for (int i = 0; i < inputsLength; i++) {
      int offsetWord =
          new BigInteger(
                  Arrays.copyOfRange(data, inputsStart + 32 + i * 32, inputsStart + 64 + i * 32))
              .intValueExact();
      int elementOffset = inputsStart + offsetWord;
      int elementLength =
          new BigInteger(Arrays.copyOfRange(data, elementOffset, elementOffset + 32)).intValueExact();
      parsed.rawInputs.add(Arrays.copyOfRange(data, elementOffset + 32, elementOffset + 32 + elementLength));
    }
    if (parsed.commands.isEmpty()) {
      return parsed;
    }
    // v4 actions blob: abi.encode(bytes, bytes[]) — no selector, plain word layout
    byte[] actionsBlob = parsed.rawInputs.get(0);
    int actionsOffset = wordAtRaw(actionsBlob, 0).intValueExact();
    int paramsOffset = wordAtRaw(actionsBlob, 1).intValueExact();
    int actionCount =
        new BigInteger(Arrays.copyOfRange(actionsBlob, actionsOffset, actionsOffset + 32)).intValueExact();
    for (int i = 0; i < actionCount; i++) {
      parsed.actions.add(actionsBlob[actionsOffset + 32 + i]);
    }
    int paramCount =
        new BigInteger(Arrays.copyOfRange(actionsBlob, paramsOffset, paramsOffset + 32)).intValueExact();
    List<byte[]> params = new ArrayList<>();
    for (int i = 0; i < paramCount; i++) {
      int offsetWord =
          new BigInteger(
                  Arrays.copyOfRange(
                      actionsBlob, paramsOffset + 32 + i * 32, paramsOffset + 64 + i * 32))
              .intValueExact();
      int elementOffset = paramsOffset + offsetWord;
      int elementLength =
          new BigInteger(Arrays.copyOfRange(actionsBlob, elementOffset, elementOffset + 32))
              .intValueExact();
      params.add(Arrays.copyOfRange(actionsBlob, elementOffset + 32, elementOffset + 32 + elementLength));
    }
    // swap params: poolKey(5) zeroForOne amount limit minHopPrice hookDataOffset (no selector)
    byte[] swap = params.get(0);
    parsed.swapZeroForOne = wordAtRaw(swap, 5).signum() != 0;
    parsed.swapAmountSpecified = wordAtRaw(swap, 6);
    parsed.swapLimit = wordAtRaw(swap, 7);
    // settle params: (currency, amount, payerIsUser)
    byte[] settle = params.get(1);
    parsed.settleCurrency = addressWord(settle, 0);
    parsed.settleAmount = wordAt(settle, 1);
    parsed.settlePayerIsUser = wordAt(settle, 2).signum() != 0;
    // take params: (currency, recipient, amount)
    byte[] take = params.get(2);
    parsed.takeCurrency = addressWord(take, 0);
    parsed.takeRecipient = addressWord(take, 1);
    return parsed;
  }

  private static BigInteger wordAt(byte[] data, int wordIndex) {
    // calldata starts with the 4-byte execute() selector
    int offset = 4 + wordIndex * 32;
    return new BigInteger(Arrays.copyOfRange(data, offset, offset + 32));
  }

  /** Word accessor for blobs without a function selector. */
  private static BigInteger wordAtRaw(byte[] data, int wordIndex) {
    int offset = wordIndex * 32;
    return new BigInteger(Arrays.copyOfRange(data, offset, offset + 32));
  }

  private static String addressWord(byte[] data, int wordIndex) {
    byte[] word = Arrays.copyOfRange(data, wordIndex * 32, wordIndex * 32 + 32);
    return "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(Arrays.copyOfRange(word, 12, 32)).toLowerCase();
  }

  /** Recovers the calldata field of a signed EIP-1559 transaction. */
  private static byte[] dataOf(byte[] signed) {
    List<byte[]> rlp = decodeRlp(signed);
    return rlp.get(7);
  }

  /** Minimal RLP decoder for a signed EIP-1559 transaction (type byte 0x02 + 11-item list). */
  private static List<byte[]> decodeRlp(byte[] payload) {
    int cursor = 1; // skip the 0x02 type byte
    int prefix = payload[cursor] & 0xff;
    cursor++;
    int listLength;
    if (prefix <= 0xf7) {
      listLength = prefix - 0xc0;
    } else {
      int lenBytes = prefix - 0xf7;
      listLength = new BigInteger(1, Arrays.copyOfRange(payload, cursor, cursor + lenBytes)).intValueExact();
      cursor += lenBytes;
    }
    int end = cursor + listLength;
    List<byte[]> items = new ArrayList<>();
    while (cursor < end) {
      int itemPrefix = payload[cursor] & 0xff;
      cursor++;
      int length;
      if (itemPrefix < 0x80) {
        items.add(new byte[] {payload[cursor - 1]});
        continue;
      } else if (itemPrefix <= 0xb7) {
        length = itemPrefix - 0x80;
      } else if (itemPrefix <= 0xbf) {
        int lenBytes = itemPrefix - 0xb7;
        length = new BigInteger(1, Arrays.copyOfRange(payload, cursor, cursor + lenBytes)).intValueExact();
        cursor += lenBytes;
      } else if (itemPrefix <= 0xf7) {
        // nested list (the EIP-1559 access list, empty in our transactions)
        length = itemPrefix - 0xc0;
      } else {
        int lenBytes = itemPrefix - 0xf7;
        length = new BigInteger(1, Arrays.copyOfRange(payload, cursor, cursor + lenBytes)).intValueExact();
        cursor += lenBytes;
      }
      items.add(Arrays.copyOfRange(payload, cursor, cursor + length));
      cursor += length;
    }
    return items;
  }
}
