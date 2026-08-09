package org.knowm.xchange.uniswap.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.uniswap.PoolKeyRegistry.PoolDefinition;
import org.knowm.xchange.uniswap.TokenRegistry.Token;
import org.knowm.xchange.uniswap.UniswapConfig;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapQuote;
import org.knowm.xchange.uniswap.protocol.Abi;
import org.knowm.xchange.uniswap.protocol.UniswapAbiEncoder;
import org.knowm.xchange.uniswap.util.Amounts;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Raw Uniswap market data: amount-specific exact-input/exact-output quotes from the on-node v4
 * Quoter, each observed at a captured block.
 */
public class UniswapMarketDataServiceRaw extends BaseExchangeService<UniswapExchange> {

  protected UniswapMarketDataServiceRaw(UniswapExchange exchange) {
    super(exchange);
  }

  /** The latest block number, as observed by the node. */
  public BigInteger getLatestBlock() throws IOException {
    return exchange.getNodeClient().blockNumber();
  }

  /**
   * Exact-input quote: how much {@code amount} of the pair's base currency yields in quote
   * currency at the latest block.
   */
  public UniswapQuote quoteExactInput(CurrencyPair pair, BigDecimal amount) throws IOException {
    return quoteExactInput(pair, amount, getLatestBlock());
  }

  /**
   * Exact-output quote: how much quote currency {@code amount} of the pair's base currency costs at
   * the latest block.
   */
  public UniswapQuote quoteExactOutput(CurrencyPair pair, BigDecimal amount) throws IOException {
    return quoteExactOutput(pair, amount, getLatestBlock());
  }

  /**
   * Exact-input quote at a captured block; the caller controls the block for block-consistent
   * multi-quote reads.
   */
  public UniswapQuote quoteExactInput(CurrencyPair pair, BigDecimal amount, BigInteger atBlock)
      throws IOException {
    UniswapConfig config = exchange.getConfig();
    PoolDefinition pool = poolFor(pair);
    Token input = config.tokens().byAddress(pool.baseAddress());
    Token output = config.tokens().byAddress(pool.quoteAddress());
    BigInteger raw = Amounts.toRaw(amount, input.decimals());
    byte[] calldata =
        UniswapAbiEncoder.quoteExactInputSingleCalldata(
            pool.encodedPoolKey(), pool.zeroForOne(input.address()), raw, new byte[0]);
    BigInteger[] result = quoterCall(calldata, atBlock);
    return new UniswapQuote(
        pair,
        true,
        amount,
        Amounts.toHuman(result[0], output.decimals()),
        raw,
        result[0],
        atBlock,
        Instant.now(),
        result[1]);
  }

  /**
   * Exact-output quote at a captured block; the caller controls the block for block-consistent
   * multi-quote reads.
   */
  public UniswapQuote quoteExactOutput(CurrencyPair pair, BigDecimal amount, BigInteger atBlock)
      throws IOException {
    UniswapConfig config = exchange.getConfig();
    PoolDefinition pool = poolFor(pair);
    Token output = config.tokens().byAddress(pool.baseAddress());
    Token input = config.tokens().byAddress(pool.quoteAddress());
    BigInteger raw = Amounts.toRaw(amount, output.decimals());
    byte[] calldata =
        UniswapAbiEncoder.quoteExactOutputSingleCalldata(
            pool.encodedPoolKey(), pool.zeroForOne(input.address()), raw, new byte[0]);
    BigInteger[] result = quoterCall(calldata, atBlock);
    return new UniswapQuote(
        pair,
        false,
        Amounts.toHuman(result[0], input.decimals()),
        amount,
        result[0],
        raw,
        atBlock,
        Instant.now(),
        result[1]);
  }

  private BigInteger[] quoterCall(byte[] calldata, BigInteger atBlock) throws IOException {
    byte[] output =
        exchange
            .getNodeClient()
            .call(exchange.getConfig().walletAddress(), exchange.getConfig().deployment().quoter(), calldata, atBlock);
    List<Type> decoded =
        FunctionReturnDecoder.decode(
            "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(output),
            Abi.typeReferences(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    if (decoded.size() < 2) {
      throw new IOException("unexpected quoter output: " + org.web3j.utils.Numeric.toHexStringNoPrefix(output));
    }
    return new BigInteger[] {(BigInteger) decoded.get(0).getValue(), (BigInteger) decoded.get(1).getValue()};
  }

  private PoolDefinition poolFor(CurrencyPair pair) {
    PoolDefinition pool = exchange.getConfig().pools().byPair(pair);
    if (pool == null) {
      throw new NotAvailableFromExchangeException("no configured v4 pool for " + pair);
    }
    return pool;
  }
}
