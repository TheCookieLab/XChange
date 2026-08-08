package org.knowm.xchange.uniswap.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.uniswap.DeploymentRegistry;
import org.knowm.xchange.uniswap.DeploymentRegistry.Deployment;
import org.knowm.xchange.uniswap.PoolKeyRegistry.PoolDefinition;
import org.knowm.xchange.uniswap.TokenRegistry.Token;
import org.knowm.xchange.uniswap.UniswapConfig;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapOrder;
import org.knowm.xchange.uniswap.dto.UniswapOrderStatus;
import org.knowm.xchange.uniswap.dto.UniswapQuote;
import org.knowm.xchange.uniswap.dto.UniswapReceiptDecoder;
import org.knowm.xchange.uniswap.protocol.Abi;
import org.knowm.xchange.uniswap.protocol.UniswapAbiEncoder;
import org.knowm.xchange.uniswap.signing.LocalKeystoreSigner.SignedTransaction;
import org.knowm.xchange.uniswap.util.Amounts;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint48;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * Raw Uniswap trade service: bounded market swaps through the Universal Router with local EIP-1559
 * signing, serialized per-address nonces, precomputed transaction hashes, at-most-once broadcast,
 * and receipt/PoolManager-log reconciliation.
 *
 * <p>Safety invariants enforced here:
 *
 * <ul>
 *   <li>ASK maps to exact-input base, BID maps to exact-output base.
 *   <li>Every order re-quotes at execution time and enforces slippage, deadline, gas, route, token,
 *       and quote-age limits against the quote.
 *   <li>Permit2 approvals are bounded by the quoted input plus the configured margin and expire
 *       after the deadline; nothing is ever approved unlimited.
 *   <li>An ambiguous send is never blindly retried: the transaction hash is reconciled against the
 *       node first.
 * </ul>
 */
public class UniswapTradeServiceRaw extends BaseExchangeService<UniswapExchange> {

  private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);
  private static final long APPROVAL_CONFIRM_TIMEOUT_SECONDS = 120;
  private static final long APPROVAL_POLL_INTERVAL_MILLIS = 2_000;

  /** Orders placed by this process, for status queries that need placement context. */
  private final Map<String, UniswapOrder> placedOrders = new ConcurrentHashMap<>();

  protected UniswapTradeServiceRaw(UniswapExchange exchange) {
    super(exchange);
  }

  /**
   * Submits a market order: ASK sells the base currency (exact input), BID buys the base currency
   * (exact output). Returns the order whose id is the locally precomputed transaction hash.
   *
   * <p>Named {@code submitMarketOrder} (not {@code placeMarketOrder}) so the standard {@link
   * org.knowm.xchange.service.trade.TradeService#placeMarketOrder} contract — which returns the id
   * as a {@code String} — can delegate without a signature clash.
   */
  public UniswapOrder submitMarketOrder(MarketOrder order) throws IOException {
    return submitMarketOrder(order, null);
  }

  /**
   * Submits a market order against an explicit reference quote; the order is rejected when the
   * quote is older than the configured maximum quote age. With a {@code null} reference quote the
   * service re-quotes at execution time.
   */
  public UniswapOrder submitMarketOrder(MarketOrder order, UniswapQuote referenceQuote) throws IOException {
    if (order == null) {
      throw new IllegalArgumentException("order must not be null");
    }
    if (order.getOriginalAmount() == null || order.getOriginalAmount().signum() <= 0) {
      throw new IllegalArgumentException("order amount must be positive");
    }
    UniswapConfig config = exchange.getConfig();
    if (!(order.getInstrument() instanceof org.knowm.xchange.currency.CurrencyPair)) {
      throw new NotAvailableFromExchangeException("only currency pairs are supported: " + order.getInstrument());
    }
    CurrencyPair pair = (CurrencyPair) order.getInstrument();
    PoolDefinition pool = poolFor(pair);
    boolean ask = order.getType() == OrderType.ASK;
    if (order.getType() != OrderType.ASK && order.getType() != OrderType.BID) {
      throw new NotAvailableFromExchangeException("only ASK and BID market orders are supported");
    }

    Token inputToken = config.tokens().byAddress(ask ? pool.baseAddress() : pool.quoteAddress());
    Token outputToken = config.tokens().byAddress(ask ? pool.quoteAddress() : pool.baseAddress());
    if (inputToken.nativeCurrency()) {
      throw new NotAvailableFromExchangeException(
          "native currency input is not supported; configure the wrapped token instead");
    }

    // Re-quote at execution time; the caller's reference quote is only a staleness gate.
    BigInteger block = exchange.getNodeClient().blockNumber();
    UniswapQuote quote =
        ask
            ? new UniswapMarketDataServiceRaw(exchange).quoteExactInput(pair, order.getOriginalAmount(), block)
            : new UniswapMarketDataServiceRaw(exchange).quoteExactOutput(pair, order.getOriginalAmount(), block);
    if (referenceQuote != null) {
      if (referenceQuote.ageSeconds() > config.maxQuoteAgeSeconds()) {
        throw new ExchangeException(
            "reference quote is " + referenceQuote.ageSeconds() + "s old; maximum is " + config.maxQuoteAgeSeconds() + "s");
      }
      if (!referenceQuote.instrument().equals(pair)) {
        throw new IllegalArgumentException("reference quote is for a different instrument");
      }
    }

    BigInteger amountSpecified =
        ask
            ? Amounts.toRaw(order.getOriginalAmount(), inputToken.decimals())
            : Amounts.toRaw(order.getOriginalAmount(), outputToken.decimals());
    BigInteger limitAmount =
        ask
            ? applyBpsFloor(quote.amountOutRaw(), 10_000 - config.maxSlippageBps())
            : applyBpsCeil(quote.amountInRaw(), 10_000 + config.maxSlippageBps());

    boolean zeroForOne = pool.zeroForOne(inputToken.address());
    String router = config.deployment().universalRouter();
    String inputAddress = inputToken.address();
    String outputAddress = outputToken.address();

    long deadlineEpochSeconds = Instant.now().plusSeconds(config.maxDeadlineSeconds()).getEpochSecond();
    BigInteger deadline = BigInteger.valueOf(deadlineEpochSeconds);
    long now = Instant.now().getEpochSecond();
    if (deadlineEpochSeconds <= now) {
      throw new ExchangeException("deadline already expired");
    }

    BigInteger maxFeePerGas = maxFeePerGas(config);
    BigInteger maxPriorityFeePerGas = maxPriorityFeePerGas(config);

    // Build the router calldata exactly once, before any state changes.
    List<byte[]> actionParams =
        UniswapAbiEncoder.singlePoolSwapActions(
            ask, pool.encodedPoolKey(), zeroForOne, amountSpecified, limitAmount, inputAddress, outputAddress, new byte[0]);
    byte[] v4Actions = UniswapAbiEncoder.encodeV4Actions(
        new byte[] {
          ask ? UniswapAbiEncoder.ACTION_SWAP_EXACT_IN_SINGLE : UniswapAbiEncoder.ACTION_SWAP_EXACT_OUT_SINGLE,
          UniswapAbiEncoder.ACTION_SETTLE,
          UniswapAbiEncoder.ACTION_TAKE
        },
        actionParams);
    byte[] executeCalldata =
        UniswapAbiEncoder.encodeExecuteCalldata(
            new byte[] {UniswapAbiEncoder.COMMAND_V4_SWAP}, List.of(v4Actions), deadline);

    long gasLimit = gasLimit(config, router, executeCalldata, maxFeePerGas);
    BigInteger inputBalance = exchange.getNodeClient().tokenBalance(inputAddress, config.walletAddress(), block);
    BigInteger requiredInput = ask ? amountSpecified : limitAmount;
    if (inputBalance.compareTo(requiredInput) < 0) {
      throw new ExchangeException(
          "insufficient " + inputToken.symbol() + " balance: have " + Amounts.toHuman(inputBalance, inputToken.decimals())
              + ", need " + Amounts.toHuman(requiredInput, inputToken.decimals()));
    }

    // Bounded Permit2 allowance first; the swap is only submitted once it is confirmed.
    ensurePermit2Allowance(inputToken, router, requiredInput, deadline);

    UniswapOrder orderState =
        new UniswapOrder(
            "",
            pair,
            order.getType(),
            UniswapOrderStatus.PENDING,
            order.getOriginalAmount(),
            null,
            quote.price(),
            null,
            Instant.now(),
            Instant.now(),
            block,
            List.of(),
            null);

    SignedTransaction signed = sign(router, executeCalldata, gasLimit, maxFeePerGas, maxPriorityFeePerGas, config);
    orderState = withOrderId(orderState, signed.hashHex());
    placedOrders.put(signed.hashHex(), orderState);

    try {
      String reportedHash = exchange.getNodeClient().sendRawTransaction(signed.signedBytes());
      if (reportedHash != null && !reportedHash.equalsIgnoreCase(signed.hashHex())) {
        throw new ExchangeException(
            "node reported a different hash than locally computed: " + reportedHash + " vs " + signed.hashHex());
      }
      return orderState;
    } catch (IOException e) {
      // Ambiguous send: never blindly retry. Reconcile by hash and report the resulting state.
      UniswapOrder reconciled = reconcileAfterAmbiguousSend(signed.hashHex(), orderState);
      if (reconciled.status() != UniswapOrderStatus.UNKNOWN) {
        return reconciled;
      }
      throw new ExchangeException(
          "broadcast result ambiguous and the node has no record of the transaction; order id "
              + signed.hashHex()
              + " must be reconciled manually before any retry: "
              + e.getMessage(),
          e);
    }
  }

  /** Current on-chain state of an order by its transaction hash. */
  public UniswapOrder getOrderStatus(String orderId) throws IOException {
    String hash = normalizeHash(orderId);
    UniswapOrder placed = placedOrders.get(hash);
    Optional<Transaction> tx = exchange.getNodeClient().transactionByHash(hash);
    if (tx.isEmpty()) {
      return withStatus(
          placed == null
              ? skeleton(hash, null, null, UniswapOrderStatus.UNKNOWN)
              : placed,
          UniswapOrderStatus.UNKNOWN,
          "node has no record of this transaction; treat as ambiguous");
    }
    Transaction transaction = tx.get();
    UniswapConfig config = exchange.getConfig();
    if (!config.walletAddress().equalsIgnoreCase(transaction.getFrom())) {
      return withStatus(
          placed == null ? skeleton(hash, null, null, UniswapOrderStatus.UNKNOWN) : placed,
          UniswapOrderStatus.UNKNOWN,
          "transaction sender is not the configured wallet");
    }
    Optional<TransactionReceipt> receipt = exchange.getNodeClient().transactionReceipt(hash);
    if (receipt.isEmpty()) {
      return withStatus(
          placed == null ? skeleton(hash, null, null, UniswapOrderStatus.PENDING) : placed,
          UniswapOrderStatus.PENDING,
          "broadcast accepted; waiting for a receipt");
    }
    UniswapOrder base = placed;
    if (base == null) {
      base = skeletonFromReceipt(hash, receipt.get());
    }
    if (base == null) {
      return withStatus(
          skeleton(hash, null, null, UniswapOrderStatus.MINED),
          UniswapOrderStatus.MINED,
          "receipt found but no matching pool swap logs were decoded");
    }
    UniswapOrder decoded = UniswapReceiptDecoder.decode(
        base, receipt.get(), config.deployment().poolManager(), poolIdFor(base), routerAddress(config), config.pools(), config.tokens());
    if (decoded.status() == UniswapOrderStatus.MINED) {
      exchange
          .getNonceManager()
          .sync(
              config.walletAddress(),
              transaction.getNonce().add(BigInteger.ONE).longValueExact());
    }
    placedOrders.put(hash, decoded);
    return decoded;
  }

  /** Current Permit2 allowance of the router for a configured token, in smallest units. */
  public BigInteger permit2Allowance(Currency currency) throws IOException {
    UniswapConfig config = exchange.getConfig();
    Token token = config.tokens().bySymbol(currency.getCurrencyCode());
    if (token == null) {
      throw new NotAvailableFromExchangeException("no configured token for " + currency);
    }
    return permit2Allowance(token, config.deployment().universalRouter(), config.walletAddress());
  }

  /**
   * Approves a bounded Permit2 allowance of a configured token for the Universal Router with the
   * given expiration and returns the approval transaction hash. The amount is not capped beyond
   * uint160.
   */
  public String approveSpend(Currency currency, BigInteger amount, long expirationEpochSeconds) throws IOException {
    UniswapConfig config = exchange.getConfig();
    Token token = config.tokens().bySymbol(currency.getCurrencyCode());
    if (token == null) {
      throw new NotAvailableFromExchangeException("no configured token for " + currency);
    }
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("approval amount must be positive");
    }
    if (amount.compareTo(BigInteger.ONE.shiftLeft(160)) >= 0) {
      throw new IllegalArgumentException("approval amount exceeds uint160");
    }
    String router = config.deployment().universalRouter();
    BigInteger maxFeePerGas = maxFeePerGas(config);
    BigInteger maxPriorityFeePerGas = maxPriorityFeePerGas(config);
    byte[] calldata =
        UniswapAbiEncoder.permit2ApproveCalldata(
            token.address(), router, amount, BigInteger.valueOf(expirationEpochSeconds));
    long gasLimit = gasLimit(config, config.deployment().permit2(), calldata, maxFeePerGas);
    SignedTransaction signed =
        sign(config.deployment().permit2(), calldata, gasLimit, maxFeePerGas, maxPriorityFeePerGas, config);
    exchange.getNodeClient().sendRawTransaction(signed.signedBytes());
    return signed.hashHex();
  }

  // ---------------------------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------------------------

  private void ensurePermit2Allowance(Token token, String router, BigInteger needed, BigInteger deadline)
      throws IOException {
    UniswapConfig config = exchange.getConfig();
    BigInteger current = permit2Allowance(token, router, config.walletAddress());
    if (current.compareTo(needed) >= 0) {
      return;
    }
    BigDecimal margin =
        new BigDecimal(needed)
            .multiply(BigDecimal.valueOf(10_000L + config.allowanceMarginBps()))
            .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.CEILING);
    BigInteger approveAmount = margin.toBigIntegerExact();
    if (approveAmount.compareTo(BigInteger.ONE.shiftLeft(160)) >= 0) {
      approveAmount = BigInteger.ONE.shiftLeft(160).subtract(BigInteger.ONE);
    }
    BigInteger maxFeePerGas = maxFeePerGas(config);
    BigInteger maxPriorityFeePerGas = maxPriorityFeePerGas(config);
    byte[] calldata =
        UniswapAbiEncoder.permit2ApproveCalldata(token.address(), router, approveAmount, deadline);
    long gasLimit = gasLimit(config, config.deployment().permit2(), calldata, maxFeePerGas);
    SignedTransaction signed =
        sign(config.deployment().permit2(), calldata, gasLimit, maxFeePerGas, maxPriorityFeePerGas, config);
    exchange.getNodeClient().sendRawTransaction(signed.signedBytes());
    long deadlineMillis = Instant.now().plusSeconds(APPROVAL_CONFIRM_TIMEOUT_SECONDS).toEpochMilli();
    while (Instant.now().toEpochMilli() < deadlineMillis) {
      Optional<TransactionReceipt> receipt = exchange.getNodeClient().transactionReceipt(signed.hashHex());
      if (receipt.isPresent()) {
        if (!receipt.get().isStatusOK()) {
          throw new ExchangeException("Permit2 approval reverted; swap not submitted. Approval id: " + signed.hashHex());
        }
        exchange
            .getNonceManager()
            .sync(
                config.walletAddress(),
                exchange
                    .getNodeClient()
                    .pendingTransactionCount(config.walletAddress())
                    .longValueExact());
        return;
      }
      try {
        Thread.sleep(APPROVAL_POLL_INTERVAL_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ExchangeException("interrupted while awaiting approval confirmation", e);
      }
    }
    throw new ExchangeException(
        "Permit2 approval not confirmed within " + APPROVAL_CONFIRM_TIMEOUT_SECONDS
            + "s; swap not submitted. Approval id: " + signed.hashHex());
  }

  private BigInteger permit2Allowance(Token token, String router, String wallet) throws IOException {
    byte[] output =
        exchange.getNodeClient().call(
            wallet,
            exchange.getConfig().deployment().permit2(),
            UniswapAbiEncoder.permit2AllowanceCalldata(wallet, token.address(), router),
            exchange.getNodeClient().blockNumber());
    List<Type> decoded =
        FunctionReturnDecoder.decode(
            "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(output),
            Abi.typeReferences(
                new TypeReference<Uint160>() {}, new TypeReference<Uint48>() {}, new TypeReference<Uint48>() {}));
    if (decoded.isEmpty()) {
      throw new IOException("unexpected permit2 allowance output");
    }
    return (BigInteger) decoded.get(0).getValue();
  }

  private SignedTransaction sign(
      String to, byte[] data, long gasLimit, BigInteger maxFeePerGas, BigInteger maxPriorityFeePerGas, UniswapConfig config)
      throws IOException {
    String wallet = config.walletAddress();
    BigInteger nonce =
        BigInteger.valueOf(
            exchange
                .getNonceManager()
                .reserve(
                    wallet,
                    () -> {
                      try {
                        return exchange.getNodeClient().pendingTransactionCount(wallet);
                      } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                      }
                    }));
    RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            config.chainId().longValueExact(),
            nonce,
            BigInteger.valueOf(gasLimit),
            to,
            BigInteger.ZERO,
            "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(data),
            maxPriorityFeePerGas,
            maxFeePerGas);
    return exchange.getSigner().sign(rawTransaction, config.chainId().longValueExact());
  }

  private BigInteger maxFeePerGas(UniswapConfig config) throws IOException {
    BigInteger cap = config.maxFeePerGasGwei().multiply(new BigDecimal(GWEI)).toBigIntegerExact();
    BigInteger baseFee = exchange.getNodeClient().baseFeePerGas();
    BigInteger priority = maxPriorityFeePerGas(config);
    BigInteger suggested = baseFee.multiply(BigInteger.TWO).add(priority);
    BigInteger fee = suggested.min(cap);
    if (fee.compareTo(baseFee.add(priority)) < 0) {
      throw new ExchangeException(
          "fee cap " + config.maxFeePerGasGwei() + " gwei is below base fee + priority (" + baseFee.add(priority) + " wei)");
    }
    return fee;
  }

  private BigInteger maxPriorityFeePerGas(UniswapConfig config) {
    BigInteger cap = config.maxPriorityFeePerGasGwei().multiply(new BigDecimal(GWEI)).toBigIntegerExact();
    BigInteger suggested = exchange.getNodeClient().priorityFeePerGas();
    return suggested.min(cap);
  }

  private long gasLimit(UniswapConfig config, String to, byte[] data, BigInteger maxFeePerGas) throws IOException {
    BigInteger estimate = exchange.getNodeClient().estimateGas(config.walletAddress(), to, data);
    if (estimate.signum() <= 0) {
      throw new ExchangeException("gas estimate is zero");
    }
    if (estimate.compareTo(BigInteger.valueOf(config.maxGasLimit())) > 0) {
      throw new ExchangeException(
          "gas estimate " + estimate + " exceeds configured max-gas-limit " + config.maxGasLimit());
    }
    BigInteger buffered = estimate.multiply(BigInteger.valueOf(6)).divide(BigInteger.valueOf(5));
    return buffered.min(BigInteger.valueOf(config.maxGasLimit())).longValueExact();
  }

  private UniswapOrder reconcileAfterAmbiguousSend(String hash, UniswapOrder order) {
    try {
      Optional<Transaction> tx = exchange.getNodeClient().transactionByHash(hash);
      if (tx.isPresent()) {
        return withStatus(order, UniswapOrderStatus.PENDING, "broadcast result ambiguous; node confirms the transaction exists");
      }
      return withStatus(order, UniswapOrderStatus.UNKNOWN, "broadcast result ambiguous; node has no record");
    } catch (IOException e) {
      return withStatus(order, UniswapOrderStatus.UNKNOWN, "broadcast result ambiguous and reconciliation failed");
    }
  }

  private PoolDefinition poolFor(CurrencyPair pair) {
    PoolDefinition pool = exchange.getConfig().pools().byPair(pair);
    if (pool == null) {
      throw new NotAvailableFromExchangeException("no configured v4 pool for " + pair);
    }
    return pool;
  }

  private String routerAddress(UniswapConfig config) {
    return config.deployment().universalRouter();
  }

  private String poolIdFor(UniswapOrder order) {
    PoolDefinition pool = exchange.getConfig().pools().byPair(order.instrument());
    return pool == null ? null : pool.poolId();
  }

  /** Reconstructs a minimal order from a receipt's pool swap logs when placement context is gone. */
  private UniswapOrder skeletonFromReceipt(String hash, TransactionReceipt receipt) {
    Deployment deployment = exchange.getConfig().deployment();
    String poolManager = deployment.poolManager().toLowerCase();
    String router = deployment.universalRouter().toLowerCase();
    for (org.web3j.protocol.core.methods.response.Log log : receipt.getLogs()) {
      if (log.getAddress() == null
          || !log.getAddress().toLowerCase().equals(poolManager)
          || log.getTopics() == null
          || log.getTopics().size() < 3
          || !UniswapReceiptDecoder.SWAP_EVENT_TOPIC.equals(log.getTopics().get(0))) {
        continue;
      }
      String poolId = log.getTopics().get(1);
      String sender = log.getTopics().get(2);
      if (!router.equals(sender)) {
        continue;
      }
      for (PoolDefinition pool : exchange.getConfig().pools().all()) {
        if (!pool.poolId().equals(poolId)) {
          continue;
        }
        List<String> words = UniswapReceiptDecoder.splitWords(log.getData());
        if (words.size() < 2) {
          continue;
        }
        BigInteger amount0 = UniswapReceiptDecoder.twosComplement(words.get(0));
        BigInteger amount1 = UniswapReceiptDecoder.twosComplement(words.get(1));
        boolean inputIsBase = amount0.signum() < 0
            ? pool.baseSymbol().equals(exchange.getConfig().tokens().byAddress(pool.currency0()).symbol())
            : pool.baseSymbol().equals(exchange.getConfig().tokens().byAddress(pool.currency1()).symbol());
        OrderType type = inputIsBase ? OrderType.ASK : OrderType.BID;
        Token baseToken = exchange.getConfig().tokens().bySymbol(pool.baseSymbol());
        BigInteger baseAmount = inputIsBase ? amount0.negate() : amount1;
        return new UniswapOrder(
            hash,
            new CurrencyPair(pool.baseSymbol(), pool.quoteSymbol()),
            type,
            UniswapOrderStatus.MINED,
            Amounts.toHuman(baseAmount, baseToken.decimals()),
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            receipt.getBlockNumber(),
            List.of(),
            null);
      }
    }
    return null;
  }

  private static UniswapOrder skeleton(String hash, CurrencyPair pair, OrderType type, UniswapOrderStatus status) {
    return new UniswapOrder(
        hash, pair, type, status, null, null, null, null, Instant.now(), Instant.now(), null, List.of(), null);
  }

  private static UniswapOrder withOrderId(UniswapOrder order, String orderId) {
    return new UniswapOrder(
        orderId,
        order.instrument(),
        order.type(),
        order.status(),
        order.originalAmount(),
        order.cumulativeAmount(),
        order.averagePrice(),
        order.fee(),
        order.createdAt(),
        order.updatedAt(),
        order.blockNumber(),
        order.fills(),
        order.note());
  }

  private static UniswapOrder withStatus(UniswapOrder order, UniswapOrderStatus status, String note) {
    return new UniswapOrder(
        order.orderId(),
        order.instrument(),
        order.type(),
        status,
        order.originalAmount(),
        order.cumulativeAmount(),
        order.averagePrice(),
        order.fee(),
        order.createdAt(),
        Instant.now(),
        order.blockNumber(),
        order.fills(),
        note);
  }

  private static String normalizeHash(String orderId) {
    String hash = orderId == null ? "" : orderId.trim().toLowerCase();
    if (hash.length() != 66 || !hash.startsWith("0x")) {
      throw new IllegalArgumentException("order id must be a 0x transaction hash");
    }
    return hash;
  }

  private static BigInteger applyBpsFloor(BigInteger value, int bps) {
    return value.multiply(BigInteger.valueOf(bps)).divide(BigInteger.valueOf(10_000));
  }

  private static BigInteger applyBpsCeil(BigInteger value, int bps) {
    return value
        .multiply(BigInteger.valueOf(bps))
        .add(BigInteger.valueOf(9_999))
        .divide(BigInteger.valueOf(10_000));
  }
}
