package org.knowm.xchange.uniswap.client;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.knowm.xchange.uniswap.protocol.Abi;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

/**
 * Small client boundary around web3j's JSON-RPC transport.
 *
 * <p>The module talks to the Ethereum node exclusively through this class; everything else works
 * with typed values. RPC timeouts come from the exchange specification's HTTP timeouts.
 */
public final class UniswapNodeClient implements AutoCloseable {

  private static final BigInteger DEFAULT_GWEI = BigInteger.valueOf(1_000_000_000L);

  private final Web3j web3j;

  private UniswapNodeClient(Web3j web3j) {
    this.web3j = web3j;
  }

  /** Builds a client for an http(s) endpoint with the given connect/read timeouts (0 = default). */
  public static UniswapNodeClient create(String rpcUrl, int connectTimeoutMillis, int readTimeoutMillis) {
    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (connectTimeoutMillis > 0) {
      builder.connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    if (readTimeoutMillis > 0) {
      builder.readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    HttpService service = new HttpService(rpcUrl, builder.build(), false);
    return new UniswapNodeClient(Web3j.build(service));
  }

  /** The chain id reported by the node. */
  public BigInteger chainId() throws IOException {
    return web3j.ethChainId().send().getChainId();
  }

  /** The latest observed block number. */
  public BigInteger blockNumber() throws IOException {
    return web3j.ethBlockNumber().send().getBlockNumber();
  }

  /** The runtime code at {@code address} as hex ({@code 0x} for no code). */
  public String codeAt(String address, BigInteger atBlock) throws IOException {
    return web3j.ethGetCode(address, DefaultBlockParameter.valueOf(atBlock)).send().getCode();
  }

  /** Result of an {@code eth_call}: raw output bytes, or empty for a revert with no data. */
  public byte[] call(String from, String to, byte[] data, BigInteger atBlock) throws IOException {
    Transaction transaction = Transaction.createEthCallTransaction(from, to, Abi.toHex(data));
    EthCall response =
        web3j.ethCall(transaction, DefaultBlockParameter.valueOf(atBlock)).send();
    if (response.hasError()) {
      throw new IOException("eth_call to " + to + " failed: " + response.getError().getMessage());
    }
    String value = response.getValue();
    if (value == null || value.isEmpty() || "0x".equals(value)) {
      throw new IOException("eth_call to " + to + " reverted");
    }
    return Abi.hexToBytes(value);
  }

  /** Native balance of an address at a block. */
  public BigInteger nativeBalance(String address, BigInteger atBlock) throws IOException {
    return web3j
        .ethGetBalance(address, DefaultBlockParameter.valueOf(atBlock))
        .send()
        .getBalance();
  }

  /** ERC-20 balance of an owner at a block, via {@code balanceOf}. */
  public BigInteger tokenBalance(String tokenAddress, String ownerAddress, BigInteger atBlock)
      throws IOException {
    byte[] output =
        call(
            ownerAddress,
            tokenAddress,
            org.knowm.xchange.uniswap.protocol.UniswapAbiEncoder.balanceOfCalldata(ownerAddress),
            atBlock);
    return decodeUint256(output);
  }

  /** Pending transaction count (next nonce) of an address. */
  public BigInteger pendingTransactionCount(String address) throws IOException {
    EthGetTransactionCount response =
        web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send();
    if (response.hasError()) {
      throw new IOException("eth_getTransactionCount failed: " + response.getError().getMessage());
    }
    return response.getTransactionCount();
  }

  /** Broadcasts a signed transaction and returns the node-reported hash. */
  public String sendRawTransaction(byte[] signedBytes) throws IOException {
    EthSendTransaction response =
        web3j.ethSendRawTransaction(Abi.toHex(signedBytes)).send();
    if (response.hasError()) {
      throw new IOException("eth_sendRawTransaction failed: " + response.getError().getMessage());
    }
    return response.getTransactionHash();
  }

  /** Looks up a transaction by hash; empty when the node has never seen it. */
  public Optional<org.web3j.protocol.core.methods.response.Transaction> transactionByHash(String hash)
      throws IOException {
    EthTransaction response = web3j.ethGetTransactionByHash(hash).send();
    return response.getTransaction();
  }

  /** Looks up a receipt by hash; empty while the transaction is pending or unknown. */
  public Optional<TransactionReceipt> transactionReceipt(String hash) throws IOException {
    return web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();
  }

  /** Base fee of the latest block, in wei. */
  public BigInteger baseFeePerGas() throws IOException {
    try {
      var feeHistory =
          web3j.ethFeeHistory(1, DefaultBlockParameterName.LATEST, Collections.emptyList()).send().getFeeHistory();
      List<BigInteger> baseFees = feeHistory.getBaseFeePerGas();
      if (baseFees != null && !baseFees.isEmpty() && baseFees.get(baseFees.size() - 1) != null) {
        return baseFees.get(baseFees.size() - 1);
      }
    } catch (IOException ignored) {
      // fall through to eth_getBlockByNumber
    }
    org.web3j.protocol.core.methods.response.EthBlock.Block block =
        web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
    if (block == null || block.getBaseFeePerGas() == null) {
      throw new IOException("node does not report a base fee; EIP-1559 is required");
    }
    return block.getBaseFeePerGas();
  }

  /** Node-recommended priority fee, or 1 gwei when the node does not support the call. */
  public BigInteger priorityFeePerGas() {
    try {
      BigInteger fee = web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
      return fee.signum() > 0 ? fee : DEFAULT_GWEI;
    } catch (Exception e) {
      return DEFAULT_GWEI;
    }
  }

  /** Gas estimate for a transaction built from the given from/to/data fields. */
  public BigInteger estimateGas(String from, String to, byte[] data) throws IOException {
    Transaction transaction =
        new Transaction(from, null, null, null, to, null, Abi.toHex(data));
    return web3j.ethEstimateGas(transaction).send().getAmountUsed();
  }

  /** Decodes an {@code eth_call} output carrying a single uint256. */
  public static BigInteger decodeUint256(byte[] output) {
    List<Type> decoded =
        FunctionReturnDecoder.decode(Abi.toHex(output), Abi.typeReferences(new TypeReference<Uint256>() {}));
    if (decoded.isEmpty()) {
      throw new IllegalArgumentException("call output is not a uint256: " + Abi.toHex(output));
    }
    return (BigInteger) decoded.get(0).getValue();
  }

  /** The address that emitted a log (normalized lowercase). */
  public static String logAddress(Log log) {
    return log.getAddress() == null ? null : log.getAddress().toLowerCase();
  }

  @Override
  public void close() {
    web3j.shutdown();
  }
}
