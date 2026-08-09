package org.knowm.xchange.uniswap.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.uniswap.PoolKeyRegistry;
import org.knowm.xchange.uniswap.TestFixtures;

/**
 * Pins the ABI encoding of every calldata the module broadcasts or quotes, cross-checked against
 * an independent reference encoder written from the ABI specification (not from the module code).
 */
class UniswapAbiTest {

  private static final PoolKeyRegistry.PoolDefinition POOL =
      TestFixtures.pools().byPair(TestFixtures.ETH_USDC_PAIR);

  @Test
  void poolKeyEncodingIsFiveWords() {
    byte[] key = POOL.encodedPoolKey();
    assertThat(key).hasSize(5 * 32);
    // word 0: USDC address right-aligned
    assertThat(hex(key, 0, 12)).isEqualTo("0".repeat(24));
    assertThat(hex(key, 12, 20)).isEqualTo(TestFixtures.USDC.substring(2).toLowerCase());
    // word 1: WETH address
    assertThat(hex(key, 32 + 12, 20)).isEqualTo(TestFixtures.WETH.substring(2).toLowerCase());
    // word 2: fee 3000, word 3: tickSpacing 60, word 4: zero hooks
    assertThat(hex(key, 64, 32)).isEqualTo(TestFixtures.word(3000));
    assertThat(hex(key, 96, 32)).isEqualTo(TestFixtures.word(60));
    assertThat(hex(key, 128, 32)).isEqualTo("0".repeat(64));
  }

  @Test
  void quoteCalldataMatchesReferenceEncoding() {
    byte[] actual =
        UniswapAbiEncoder.quoteExactInputSingleCalldata(
            POOL.encodedPoolKey(), true, BigInteger.valueOf(1_000_000), new byte[0]);
    byte[] expected =
        ReferenceEncoder.quoteCalldata("quoteExactInputSingle", POOL, true, 1_000_000, new byte[0]);
    assertThat(hex(actual)).isEqualTo(hex(expected));
  }

  @Test
  void executeCalldataMatchesReferenceEncodingForExactInputSwap() {
    byte[] actions =
        UniswapAbiEncoder.encodeV4Actions(
            new byte[] {
              UniswapAbiEncoder.ACTION_SWAP_EXACT_IN_SINGLE,
              UniswapAbiEncoder.ACTION_SETTLE,
              UniswapAbiEncoder.ACTION_TAKE
            },
            UniswapAbiEncoder.singlePoolSwapActions(
                true,
                POOL.encodedPoolKey(),
                false, // input WETH is currency1
                BigInteger.valueOf(10L).pow(15), // 0.001 ETH
                BigInteger.valueOf(1_900_000), // 1.9 USDC min out (5% slippage on 2.0)
                TestFixtures.WETH.toLowerCase(),
                TestFixtures.USDC.toLowerCase(),
                new byte[0]));
    byte[] actual =
        UniswapAbiEncoder.encodeExecuteCalldata(
            new byte[] {UniswapAbiEncoder.COMMAND_V4_SWAP}, List.of(actions), BigInteger.valueOf(1_800_000_000L));

    byte[] expected =
        ReferenceEncoder.executeCalldata(
            new byte[] {UniswapAbiEncoder.COMMAND_V4_SWAP},
            List.of(
                ReferenceEncoder.v4Actions(
                    new byte[] {
                      UniswapAbiEncoder.ACTION_SWAP_EXACT_IN_SINGLE,
                      UniswapAbiEncoder.ACTION_SETTLE,
                      UniswapAbiEncoder.ACTION_TAKE
                    },
                    List.of(
                        ReferenceEncoder.swapSingleParams(
                            POOL, false, 1_000_000_000_000_000L, 1_900_000L, BigInteger.ZERO, new byte[0]),
                        ReferenceEncoder.staticTuple(
                            List.of(
                                addressWord(TestFixtures.WETH),
                                wordBigInt(0),
                                wordBigInt(1))),
                        ReferenceEncoder.staticTuple(
                            List.of(
                                addressWord(TestFixtures.USDC),
                                addressWord("0x0000000000000000000000000000000000000001"),
                                wordBigInt(0)))))),
            BigInteger.valueOf(1_800_000_000L));

    assertThat(hex(actual)).isEqualTo(hex(expected));
  }

  @Test
  void poolIdIsKeccakOfEncodedPoolKey() {
    assertThat(POOL.poolId())
        .isEqualTo(
            "0x"
                + org.web3j.utils.Numeric.toHexStringNoPrefix(
                    org.web3j.crypto.Hash.sha3(POOL.encodedPoolKey())));
  }

  @Test
  void permit2ApproveCalldataMatchesReferenceEncoding() {
    byte[] actual =
        UniswapAbiEncoder.permit2ApproveCalldata(
            TestFixtures.WETH.toLowerCase(),
            TestFixtures.ROUTER.toLowerCase(),
            BigInteger.valueOf(2_000_000),
            BigInteger.valueOf(1_800_000_000L));
    byte[] expected =
        ReferenceEncoder.functionCall(
            "approve(address,address,uint160,uint48)",
            ReferenceEncoder.staticTuple(
                List.of(
                    addressWord(TestFixtures.WETH),
                    addressWord(TestFixtures.ROUTER),
                    wordBigInt(2_000_000),
                    wordBigInt(1_800_000_000L))));
    assertThat(hex(actual)).isEqualTo(hex(expected));
  }

  @Test
  void executeCalldataCarriesTheExecuteSelector() {
    byte[] calldata =
        UniswapAbiEncoder.encodeExecuteCalldata(
            new byte[] {UniswapAbiEncoder.COMMAND_V4_SWAP},
            List.of(new byte[96]),
            BigInteger.ONE);
    assertThat(hex(calldata, 0, 4))
        .isEqualTo(
            org.web3j.utils.Numeric.toHexStringNoPrefix(
                Abi.selector("execute(bytes,bytes[],uint256)")));
  }

  // -------------------------------------------------------------------------------------------
  // Reference encoder: independent ABI implementation for cross-checking
  // -------------------------------------------------------------------------------------------

  private static final class ReferenceEncoder {

    static byte[] quoteCalldata(
        String function, PoolKeyRegistry.PoolDefinition pool, boolean zeroForOne, long amount, byte[] hookData) {
      byte[] poolKey = pool.encodedPoolKey();
      byte[] head =
          concat(
              poolKey,
              wordBigInt(zeroForOne ? 1 : 0),
              wordBigInt(amount),
              wordBigInt(8 * 32L));
      return functionCall(
          function + "((address,address,uint24,int24,address),bool,uint128,bytes)",
          concat(head, dynamicBytes(hookData)));
    }

    static byte[] swapSingleParams(
        PoolKeyRegistry.PoolDefinition pool,
        boolean zeroForOne,
        long amountSpecified,
        long limit,
        BigInteger minHopPriceX36,
        byte[] hookData) {
      byte[] head =
          concat(
              pool.encodedPoolKey(),
              wordBigInt(zeroForOne ? 1 : 0),
              wordBigInt(amountSpecified),
              wordBigInt(limit),
              word(minHopPriceX36),
              wordBigInt(10 * 32L));
      return concat(head, dynamicBytes(hookData));
    }

    static byte[] v4Actions(byte[] actionIds, List<byte[]> params) {
      byte[] actionsBlob = dynamicBytes(actionIds);
      byte[] paramsBlob = dynamicBytesArray(params);
      return concat(
          wordBigInt(2 * 32L), wordBigInt(2 * 32L + actionsBlob.length), actionsBlob, paramsBlob);
    }

    static byte[] executeCalldata(byte[] commandIds, List<byte[]> inputs, BigInteger deadline) {
      byte[] commandsBlob = dynamicBytes(commandIds);
      byte[] inputsBlob = dynamicBytesArray(inputs);
      return concat(
          Abi.selector("execute(bytes,bytes[],uint256)"),
          wordBigInt(3 * 32L),
          wordBigInt(3 * 32L + commandsBlob.length),
          word(deadline),
          commandsBlob,
          inputsBlob);
    }

    static byte[] functionCall(String signature, byte[] args) {
      return concat(Abi.selector(signature), args);
    }

    static byte[] staticTuple(List<byte[]> words) {
      byte[] out = new byte[words.size() * 32];
      for (int i = 0; i < words.size(); i++) {
        System.arraycopy(words.get(i), 0, out, i * 32, 32);
      }
      return out;
    }

    static byte[] dynamicBytes(byte[] data) {
      int padded = ((data.length + 31) / 32) * 32;
      byte[] out = new byte[32 + padded];
      System.arraycopy(wordBigInt(data.length), 0, out, 0, 32);
      System.arraycopy(data, 0, out, 32, data.length);
      return out;
    }

    static byte[] dynamicBytesArray(List<byte[]> elements) {
      int[] lengths = new int[elements.size()];
      int total = 0;
      for (int i = 0; i < elements.size(); i++) {
        lengths[i] = 32 + ((elements.get(i).length + 31) / 32) * 32;
        total += lengths[i];
      }
      byte[] out = new byte[32 + elements.size() * 32 + total];
      System.arraycopy(wordBigInt(elements.size()), 0, out, 0, 32);
      int offset = 32 + elements.size() * 32;
      for (int i = 0; i < elements.size(); i++) {
        System.arraycopy(wordBigInt(offset), 0, out, 32 + i * 32, 32);
        offset += lengths[i];
      }
      int cursor = 32 + elements.size() * 32;
      for (int i = 0; i < elements.size(); i++) {
        byte[] element = dynamicBytes(elements.get(i));
        System.arraycopy(element, 0, out, cursor, element.length);
        cursor += lengths[i];
      }
      return out;
    }

    static byte[] concat(byte[]... chunks) {
      int total = 0;
      for (byte[] chunk : chunks) {
        total += chunk.length;
      }
      byte[] out = new byte[total];
      int cursor = 0;
      for (byte[] chunk : chunks) {
        System.arraycopy(chunk, 0, out, cursor, chunk.length);
        cursor += chunk.length;
      }
      return out;
    }

    static byte[] word(BigInteger value) {
      byte[] out = new byte[32];
      byte[] raw = value.toByteArray();
      System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
      return out;
    }

    /** Right-aligned 20-byte address word. */
    static byte[] addressWord(String address) {
      byte[] raw = org.knowm.xchange.uniswap.protocol.Abi.hexToBytes(address);
      byte[] out = new byte[32];
      System.arraycopy(raw, 0, out, 12, 20);
      return out;
    }

    static byte[] wordBigInt(long value) {
      return word(BigInteger.valueOf(value));
    }
  }

  private static byte[] wordBigInt(long value) {
    byte[] out = new byte[32];
    byte[] raw = BigInteger.valueOf(value).toByteArray();
    System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
    return out;
  }

  private static byte[] addressWord(String address) {
    byte[] raw = Abi.hexToBytes(address);
    byte[] out = new byte[32];
    System.arraycopy(raw, 0, out, 12, 20);
    return out;
  }

  private static String hex(byte[] bytes) {
    return "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(bytes);
  }

  private static String hex(byte[] bytes, int offset, int length) {
    return org.web3j.utils.Numeric.toHexStringNoPrefix(java.util.Arrays.copyOfRange(bytes, offset, offset + length));
  }
}
