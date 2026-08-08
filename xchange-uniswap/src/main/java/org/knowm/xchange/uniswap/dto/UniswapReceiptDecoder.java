package org.knowm.xchange.uniswap.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.uniswap.DeploymentRegistry;
import org.knowm.xchange.uniswap.PoolKeyRegistry;
import org.knowm.xchange.uniswap.TokenRegistry;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * Decodes a swap transaction receipt into order state: success/failure from the receipt status,
 * fills from the PoolManager {@code Swap} events matching the pool and router, and the gas fee from
 * the receipt.
 *
 * <p>Pure and deterministic: all inputs are explicit, so fixture receipts fully exercise revert,
 * multi-swap, and fee cases.
 */
public final class UniswapReceiptDecoder {

  /** topic0 of the v4 PoolManager {@code Swap} event. */
  public static final String SWAP_EVENT_TOPIC =
      org.web3j.crypto.Hash.sha3("Swap(bytes32,address,int128,int128,uint160,uint128,int24,uint24)");

  private UniswapReceiptDecoder() {}

  /**
   * Decodes a receipt for an order identified by {@code poolId} and the router address.
   *
   * @return updated order state; the input order is not mutated
   */
  public static UniswapOrder decode(
      UniswapOrder order,
      TransactionReceipt receipt,
      String poolManagerAddress,
      String poolId,
      String routerAddress,
      PoolKeyRegistry pools,
      TokenRegistry tokens) {
    if (receipt == null) {
      return order;
    }
    boolean success = "0x1".equals(receipt.getStatus());
    UniswapOrderStatus status = success ? UniswapOrderStatus.MINED : UniswapOrderStatus.REVERTED;

    List<UniswapFill> fills = new ArrayList<>();
    // Reverted transactions carry no logs on chain; decode fills only for successes.
    if (success) {
      String poolManager = poolManagerAddress.toLowerCase();
      String router = routerAddress.toLowerCase();
      for (Log log : receipt.getLogs()) {
        if (log.getAddress() == null
            || !log.getAddress().toLowerCase().equals(poolManager)
            || log.getTopics() == null
            || log.getTopics().size() < 3
            || !SWAP_EVENT_TOPIC.equals(log.getTopics().get(0))) {
          continue;
        }
        String logPoolId = log.getTopics().get(1);
        String logSender = log.getTopics().get(2);
        if (!poolId.equals(logPoolId) || !router.equals(logSender)) {
          continue;
        }
        List<String> data = splitWords(log.getData());
        if (data.size() < 2) {
          continue;
        }
        BigInteger amount0 = twosComplement(data.get(0));
        BigInteger amount1 = twosComplement(data.get(1));
        PoolKeyRegistry.PoolDefinition pool = poolsFor(pools, poolId);
        if (pool == null) {
          continue;
        }
        TokenRegistry.Token token0 = tokens.byAddress(pool.currency0());
        TokenRegistry.Token token1 = tokens.byAddress(pool.currency1());
        if (token0 != null) {
          fills.add(UniswapFill.of(token0.symbol(), amount0, token0.decimals(), receipt.getBlockNumber()));
        }
        if (token1 != null) {
          fills.add(UniswapFill.of(token1.symbol(), amount1, token1.decimals(), receipt.getBlockNumber()));
        }
      }
    }

    BigInteger gasUsed = receipt.getGasUsed();
    String effectiveGasPriceRaw = receipt.getEffectiveGasPrice();
    BigDecimal fee = null;
    if (gasUsed != null && effectiveGasPriceRaw != null) {
      BigInteger effectiveGasPrice =
          effectiveGasPriceRaw.startsWith("0x")
              ? new BigInteger(effectiveGasPriceRaw.substring(2), 16)
              : new BigInteger(effectiveGasPriceRaw);
      fee = new BigDecimal(gasUsed.multiply(effectiveGasPrice)).movePointLeft(18);
    }

    // Cumulative amount and average price use the pool's derived base/quote sides, so a wrapped
    // base (ETH pair on a WETH pool) reports base-currency amounts.
    BigDecimal cumulative = null;
    BigDecimal average = null;
    PoolKeyRegistry.PoolDefinition pool = poolsFor(pools, poolId);
    if (pool != null) {
      TokenRegistry.Token baseToken = tokens.byAddress(pool.baseAddress());
      TokenRegistry.Token quoteToken = tokens.byAddress(pool.quoteAddress());
      UniswapFill baseFill = fillBySymbol(fills, baseToken);
      UniswapFill quoteFill = fillBySymbol(fills, quoteToken);
      if (baseFill != null && quoteFill != null && baseFill.amount().signum() != 0) {
        cumulative = baseFill.amount().abs();
        average =
            quoteFill
                .amount()
                .abs()
                .divide(cumulative, 18, java.math.RoundingMode.HALF_UP);
      }
    }

    return new UniswapOrder(
        order.orderId(),
        order.instrument(),
        order.type(),
        status,
        order.originalAmount(),
        cumulative,
        average,
        fee,
        order.createdAt(),
        Instant.now(),
        receipt.getBlockNumber(),
        fills,
        success ? null : "transaction reverted on chain");
  }

  private static UniswapFill fillBySymbol(List<UniswapFill> fills, TokenRegistry.Token token) {
    if (token == null) {
      return null;
    }
    for (UniswapFill fill : fills) {
      if (fill.currencySymbol().equals(token.symbol())) {
        return fill;
      }
    }
    return null;
  }

  private static PoolKeyRegistry.PoolDefinition poolsFor(PoolKeyRegistry pools, String poolId) {
    for (PoolKeyRegistry.PoolDefinition pool : pools.all()) {
      if (pool.poolId().equals(poolId)) {
        return pool;
      }
    }
    return null;
  }

  /** Splits a hex data blob into 32-byte words. */
  public static List<String> splitWords(String data) {
    String hex = data == null ? "" : data.startsWith("0x") ? data.substring(2) : data;
    List<String> words = new ArrayList<>();
    for (int i = 0; i + 64 <= hex.length(); i += 64) {
      words.add(hex.substring(i, i + 64));
    }
    return words;
  }

  /** Interprets a 32-byte word as a signed two's-complement value. */
  public static BigInteger twosComplement(String word) {
    BigInteger value = new BigInteger(word, 16);
    if (value.testBit(255)) {
      value = value.subtract(BigInteger.ONE.shiftLeft(256));
    }
    return value;
  }

  /** Computes the canonical topic0 for the PoolManager Swap event. */
  static String swapTopic() {
    return org.web3j.crypto.Hash.sha3("Swap(bytes32,address,int128,int128,uint160,uint128,int24,uint24)");
  }

  static {
    // keep the constant and the helper in agreement
    if (!SWAP_EVENT_TOPIC.equals(swapTopic())) {
      throw new IllegalStateException("swap event topic mismatch");
    }
  }
}
