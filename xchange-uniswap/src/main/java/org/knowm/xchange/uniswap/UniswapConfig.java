package org.knowm.xchange.uniswap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.uniswap.DeploymentRegistry.Deployment;
import org.knowm.xchange.uniswap.PoolKeyRegistry.PoolDefinition;
import org.knowm.xchange.uniswap.TokenRegistry.Token;
import org.knowm.xchange.uniswap.protocol.UniswapAbiEncoder;
import org.knowm.xchange.uniswap.signing.EnvironmentSecretProvider;
import org.knowm.xchange.uniswap.util.Addresses;

/**
 * Typed, immutable configuration of a {@link UniswapExchange} instance, parsed fail-closed from an
 * {@link ExchangeSpecification}.
 *
 * <p>All Uniswap-specific values travel as exchange-specific parameters; secrets (the keystore
 * password) never do — they come from a {@link
 * org.knowm.xchange.uniswap.signing.SecretProvider} at signing time.
 */
public final class UniswapConfig {

  /** Parameter keys understood by {@link #from(ExchangeSpecification)}. */
  public static final class Keys {
    public static final String RPC_URL = "uniswap.rpc-url";
    public static final String CHAIN_ID = "uniswap.chain-id";
    public static final String WALLET_ADDRESS = "uniswap.wallet-address";
    public static final String KEYSTORE_PATH = "uniswap.keystore-path";
    public static final String PASSWORD_PROVIDER_CLASS = "uniswap.password-provider-class";
    public static final String TOKENS = "uniswap.tokens";
    public static final String POOL_KEYS = "uniswap.pool-keys";
    public static final String DEPLOYMENTS = "uniswap.deployments";
    public static final String QUOTE_REF_SIZE = "uniswap.quote-ref-size";
    public static final String MAX_SLIPPAGE_BPS = "uniswap.max-slippage-bps";
    public static final String MAX_DEADLINE_SECONDS = "uniswap.max-deadline-seconds";
    public static final String MAX_QUOTE_AGE_SECONDS = "uniswap.max-quote-age-seconds";
    public static final String MAX_FEE_PER_GAS_GWEI = "uniswap.max-fee-per-gas-gwei";
    public static final String MAX_PRIORITY_FEE_PER_GAS_GWEI = "uniswap.max-priority-fee-per-gas-gwei";
    public static final String MAX_GAS_LIMIT = "uniswap.max-gas-limit";
    public static final String ALLOWANCE_MARGIN_BPS = "uniswap.allowance-margin-bps";
    public static final String VERIFY_ON_STARTUP = "uniswap.verify-on-startup";

    private Keys() {}
  }

  /** Ethereum mainnet chain id. */
  public static final BigInteger MAINNET_CHAIN_ID = BigInteger.ONE;

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_SLIPPAGE_BPS = 10_000;

  private final BigInteger chainId;
  private final String rpcUrl;
  private final String walletAddress;
  private final Path keystorePath;
  private final String passwordProviderClass;
  private final TokenRegistry tokens;
  private final PoolKeyRegistry pools;
  private final Deployment deployment;
  private final BigDecimal quoteRefSize;
  private final int maxSlippageBps;
  private final long maxDeadlineSeconds;
  private final long maxQuoteAgeSeconds;
  private final BigDecimal maxFeePerGasGwei;
  private final BigDecimal maxPriorityFeePerGasGwei;
  private final long maxGasLimit;
  private final int allowanceMarginBps;
  private final boolean verifyOnStartup;

  private UniswapConfig(Builder builder) {
    this.chainId = builder.chainId;
    this.rpcUrl = builder.rpcUrl;
    this.walletAddress = builder.walletAddress;
    this.keystorePath = builder.keystorePath;
    this.passwordProviderClass = builder.passwordProviderClass;
    this.tokens = builder.tokens;
    this.pools = builder.pools;
    this.deployment = builder.deployment;
    this.quoteRefSize = builder.quoteRefSize;
    this.maxSlippageBps = builder.maxSlippageBps;
    this.maxDeadlineSeconds = builder.maxDeadlineSeconds;
    this.maxQuoteAgeSeconds = builder.maxQuoteAgeSeconds;
    this.maxFeePerGasGwei = builder.maxFeePerGasGwei;
    this.maxPriorityFeePerGasGwei = builder.maxPriorityFeePerGasGwei;
    this.maxGasLimit = builder.maxGasLimit;
    this.allowanceMarginBps = builder.allowanceMarginBps;
    this.verifyOnStartup = builder.verifyOnStartup;
  }

  /** Chain id the node must report. */
  public BigInteger chainId() {
    return chainId;
  }

  /** JSON-RPC endpoint of the (loopback or SSH-forwarded) Ethereum node. */
  public String rpcUrl() {
    return rpcUrl;
  }

  /** The configured wallet address (normalized lowercase). */
  public String walletAddress() {
    return walletAddress;
  }

  /** Path of the encrypted Web3 V3 keystore. */
  public Path keystorePath() {
    return keystorePath;
  }

  /** Class name of the {@link org.knowm.xchange.uniswap.signing.SecretProvider}. */
  public String passwordProviderClass() {
    return passwordProviderClass;
  }

  /** The configured token registry. */
  public TokenRegistry tokens() {
    return tokens;
  }

  /** The configured pool registry. */
  public PoolKeyRegistry pools() {
    return pools;
  }

  /** The configured on-chain deployment. */
  public Deployment deployment() {
    return deployment;
  }

  /** Reference base-currency size used by the standard ticker. */
  public BigDecimal quoteRefSize() {
    return quoteRefSize;
  }

  /** Maximum allowed slippage in basis points (1 bp = 0.01%). */
  public int maxSlippageBps() {
    return maxSlippageBps;
  }

  /** Maximum transaction deadline in seconds from submission. */
  public long maxDeadlineSeconds() {
    return maxDeadlineSeconds;
  }

  /** Maximum age of a caller-supplied reference quote before it is rejected. */
  public long maxQuoteAgeSeconds() {
    return maxQuoteAgeSeconds;
  }

  /** Upper bound for the EIP-1559 max fee per gas in gwei. */
  public BigDecimal maxFeePerGasGwei() {
    return maxFeePerGasGwei;
  }

  /** Upper bound for the EIP-1559 max priority fee per gas in gwei. */
  public BigDecimal maxPriorityFeePerGasGwei() {
    return maxPriorityFeePerGasGwei;
  }

  /** Upper bound for the gas limit of submitted transactions. */
  public long maxGasLimit() {
    return maxGasLimit;
  }

  /** Extra margin over the quoted input applied to bounded Permit2 approvals, in basis points. */
  public int allowanceMarginBps() {
    return allowanceMarginBps;
  }

  /** Whether chain id and deployment bytecode are verified at startup. */
  public boolean verifyOnStartup() {
    return verifyOnStartup;
  }

  /**
   * Parses and validates the configuration from an {@link ExchangeSpecification}, failing closed
   * with {@link IllegalArgumentException} on any missing, malformed, or unsafe value.
   */
  public static UniswapConfig from(ExchangeSpecification specification) {
    Objects.requireNonNull(specification, "specification");
    Builder builder = new Builder();

    builder.chainId = chainId(specification);
    builder.rpcUrl = rpcUrl(specification);
    builder.walletAddress = Addresses.requireValidAddress(required(specification, Keys.WALLET_ADDRESS));
    builder.keystorePath = keystorePath(required(specification, Keys.KEYSTORE_PATH));
    builder.passwordProviderClass =
        optional(specification, Keys.PASSWORD_PROVIDER_CLASS, EnvironmentSecretProvider.class.getName());

    List<Token> tokens = tokens(required(specification, Keys.TOKENS));
    builder.tokens = TokenRegistry.of(tokens);
    builder.pools = PoolKeyRegistry.of(pools(required(specification, Keys.POOL_KEYS), builder.tokens), builder.tokens);
    builder.deployment = deployment(required(specification, Keys.DEPLOYMENTS));

    builder.quoteRefSize = positiveDecimal(optional(specification, Keys.QUOTE_REF_SIZE, "1"), Keys.QUOTE_REF_SIZE);
    builder.maxSlippageBps = bps(required(specification, Keys.MAX_SLIPPAGE_BPS), Keys.MAX_SLIPPAGE_BPS);
    builder.maxDeadlineSeconds = positiveLong(required(specification, Keys.MAX_DEADLINE_SECONDS), Keys.MAX_DEADLINE_SECONDS);
    builder.maxQuoteAgeSeconds = positiveLong(required(specification, Keys.MAX_QUOTE_AGE_SECONDS), Keys.MAX_QUOTE_AGE_SECONDS);
    builder.maxFeePerGasGwei = positiveDecimal(required(specification, Keys.MAX_FEE_PER_GAS_GWEI), Keys.MAX_FEE_PER_GAS_GWEI);
    builder.maxPriorityFeePerGasGwei =
        positiveDecimal(required(specification, Keys.MAX_PRIORITY_FEE_PER_GAS_GWEI), Keys.MAX_PRIORITY_FEE_PER_GAS_GWEI);
    builder.maxGasLimit = positiveLong(required(specification, Keys.MAX_GAS_LIMIT), Keys.MAX_GAS_LIMIT);
    builder.allowanceMarginBps = nonNegativeInt(optional(specification, Keys.ALLOWANCE_MARGIN_BPS, "500"), Keys.ALLOWANCE_MARGIN_BPS);
    builder.verifyOnStartup = Boolean.parseBoolean(optional(specification, Keys.VERIFY_ON_STARTUP, "true"));

    return new UniswapConfig(builder);
  }

  private static BigInteger chainId(ExchangeSpecification specification) {
    String value = optional(specification, Keys.CHAIN_ID, MAINNET_CHAIN_ID.toString());
    try {
      BigInteger chainId = new BigInteger(value.trim());
      if (chainId.signum() <= 0) {
        throw new IllegalArgumentException(Keys.CHAIN_ID + " must be positive");
      }
      return chainId;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(Keys.CHAIN_ID + " is not a number: " + value, e);
    }
  }

  private static String rpcUrl(ExchangeSpecification specification) {
    String url = optional(specification, Keys.RPC_URL, null);
    if (url == null) {
      url = specification.getSslUri();
    }
    if (url == null) {
      url = specification.getPlainTextUri();
    }
    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("no RPC endpoint: set " + Keys.RPC_URL + " or sslUri");
    }
    if (!(url.startsWith("http://") || url.startsWith("https://"))) {
      throw new IllegalArgumentException("RPC endpoint must be http(s): " + url);
    }
    return url.trim();
  }

  private static Path keystorePath(String value) {
    Path path = Path.of(value.trim()).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("keystore is not a regular file: " + path);
    }
    try {
      java.util.Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
      if (permissions.contains(PosixFilePermission.GROUP_WRITE)
          || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
        throw new IllegalArgumentException(
            "keystore is writable by group or others; restrict it to the owner: " + path);
      }
    } catch (UnsupportedOperationException e) {
      // non-POSIX filesystem: existence and regular-file checks above still apply
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("cannot read keystore permissions: " + path, e);
    }
    return path;
  }

  private static List<Token> tokens(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      if (!root.isArray()) {
        throw new IllegalArgumentException(Keys.TOKENS + " must be a JSON array");
      }
      List<Token> tokens = new ArrayList<>();
      Iterator<JsonNode> elements = root.elements();
      while (elements.hasNext()) {
        JsonNode node = elements.next();
        String symbol = node.path("symbol").asText("");
        String address = node.path("address").asText("");
        int decimals = node.path("decimals").asInt(-1);
        if (symbol.trim().isEmpty()) {
          throw new IllegalArgumentException(Keys.TOKENS + ": token is missing symbol");
        }
        if (address.trim().isEmpty()) {
          throw new IllegalArgumentException(Keys.TOKENS + ": token " + symbol + " is missing address");
        }
        if (decimals < 0 || decimals > 36) {
          throw new IllegalArgumentException(Keys.TOKENS + ": token " + symbol + " has invalid decimals");
        }
        boolean nativeCurrency = node.path("native").asBoolean(false);
        tokens.add(new Token(symbol, address, decimals, nativeCurrency));
      }
      return tokens;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(Keys.TOKENS + " is not valid JSON", e);
    }
  }

  private static List<PoolDefinition> pools(String json, TokenRegistry tokens) {
    try {
      JsonNode root = MAPPER.readTree(json);
      if (!root.isArray()) {
        throw new IllegalArgumentException(Keys.POOL_KEYS + " must be a JSON array");
      }
      List<PoolDefinition> pools = new ArrayList<>();
      Iterator<JsonNode> elements = root.elements();
      while (elements.hasNext()) {
        JsonNode node = elements.next();
        String pair = node.path("pair").asText("");
        String[] symbols = pair.split("/");
        if (symbols.length != 2 || symbols[0].trim().isEmpty() || symbols[1].trim().isEmpty()) {
          throw new IllegalArgumentException(Keys.POOL_KEYS + ": invalid pair " + pair);
        }
        String base = symbols[0].trim().toUpperCase(Locale.ROOT);
        String quote = symbols[1].trim().toUpperCase(Locale.ROOT);
        String currency0 = node.path("currency0").asText("");
        String currency1 = node.path("currency1").asText("");
        String hooks = node.path("hooks").asText("0x0000000000000000000000000000000000000000");
        int fee = node.path("fee").asInt(-1);
        int tickSpacing = node.path("tickSpacing").asInt(0);
        if (currency0.trim().isEmpty() || currency1.trim().isEmpty()) {
          throw new IllegalArgumentException(Keys.POOL_KEYS + ": pool " + pair + " is missing currencies");
        }
        if (fee < 0) {
          throw new IllegalArgumentException(Keys.POOL_KEYS + ": pool " + pair + " is missing fee");
        }
        pools.add(
            PoolDefinition.create(
                base, quote, currency0, currency1, fee, tickSpacing, hooks, tokens));
      }
      return pools;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(Keys.POOL_KEYS + " is not valid JSON", e);
    }
  }

  private static Deployment deployment(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      Map<DeploymentRegistry.Contract, String> codeHashes = new java.util.LinkedHashMap<>();
      JsonNode hashes = root.path("codeHashes");
      codeHashes.put(DeploymentRegistry.Contract.POOL_MANAGER, hashes.path("poolManager").asText(""));
      codeHashes.put(DeploymentRegistry.Contract.QUOTER, hashes.path("quoter").asText(""));
      codeHashes.put(
          DeploymentRegistry.Contract.UNIVERSAL_ROUTER, hashes.path("universalRouter").asText(""));
      codeHashes.put(DeploymentRegistry.Contract.PERMIT2, hashes.path("permit2").asText(""));
      return new Deployment(
          root.path("poolManager").asText(""),
          root.path("quoter").asText(""),
          root.path("universalRouter").asText(""),
          root.path("permit2").asText(""),
          root.path("weth").asText(UniswapAbiEncoder.MAINNET_WETH),
          codeHashes);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(Keys.DEPLOYMENTS + " is not valid JSON", e);
    }
  }

  private static String required(ExchangeSpecification specification, String key) {
    String value = optional(specification, key, null);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("missing required parameter " + key);
    }
    return value.trim();
  }

  private static String optional(ExchangeSpecification specification, String key, String defaultValue) {
    Object value = specification.getExchangeSpecificParametersItem(key);
    if (value == null) {
      return defaultValue;
    }
    return String.valueOf(value);
  }

  private static long positiveLong(String value, String key) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) {
        throw new IllegalArgumentException(key + " must be positive");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " is not a number: " + value, e);
    }
  }

  private static BigDecimal positiveDecimal(String value, String key) {
    try {
      BigDecimal parsed = new BigDecimal(value);
      if (parsed.signum() <= 0) {
        throw new IllegalArgumentException(key + " must be positive");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " is not a number: " + value, e);
    }
  }

  private static int bps(String value, String key) {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0 || parsed > MAX_SLIPPAGE_BPS) {
        throw new IllegalArgumentException(key + " must be between 1 and " + MAX_SLIPPAGE_BPS);
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " is not a number: " + value, e);
    }
  }

  private static int nonNegativeInt(String value, String key) {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < 0) {
        throw new IllegalArgumentException(key + " must not be negative");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " is not a number: " + value, e);
    }
  }

  /** Builder for tests and programmatic construction; validates the same way as parsing. */
  public static final class Builder {

    private BigInteger chainId = MAINNET_CHAIN_ID;
    private String rpcUrl;
    private String walletAddress;
    private Path keystorePath;
    private String passwordProviderClass = EnvironmentSecretProvider.class.getName();
    private TokenRegistry tokens;
    private PoolKeyRegistry pools;
    private Deployment deployment;
    private BigDecimal quoteRefSize = BigDecimal.ONE;
    private int maxSlippageBps;
    private long maxDeadlineSeconds;
    private long maxQuoteAgeSeconds;
    private BigDecimal maxFeePerGasGwei;
    private BigDecimal maxPriorityFeePerGasGwei;
    private long maxGasLimit;
    private int allowanceMarginBps = 500;
    private boolean verifyOnStartup = true;

    public Builder chainId(BigInteger chainId) {
      this.chainId = chainId;
      return this;
    }

    public Builder rpcUrl(String rpcUrl) {
      this.rpcUrl = rpcUrl;
      return this;
    }

    public Builder walletAddress(String walletAddress) {
      this.walletAddress = Addresses.requireValidAddress(walletAddress);
      return this;
    }

    public Builder keystorePath(Path keystorePath) {
      this.keystorePath = keystorePath;
      return this;
    }

    public Builder passwordProviderClass(String passwordProviderClass) {
      this.passwordProviderClass = passwordProviderClass;
      return this;
    }

    public Builder tokens(TokenRegistry tokens) {
      this.tokens = tokens;
      return this;
    }

    public Builder pools(PoolKeyRegistry pools) {
      this.pools = pools;
      return this;
    }

    public Builder deployment(Deployment deployment) {
      this.deployment = deployment;
      return this;
    }

    public Builder quoteRefSize(BigDecimal quoteRefSize) {
      this.quoteRefSize = quoteRefSize;
      return this;
    }

    public Builder maxSlippageBps(int maxSlippageBps) {
      this.maxSlippageBps = maxSlippageBps;
      return this;
    }

    public Builder maxDeadlineSeconds(long maxDeadlineSeconds) {
      this.maxDeadlineSeconds = maxDeadlineSeconds;
      return this;
    }

    public Builder maxQuoteAgeSeconds(long maxQuoteAgeSeconds) {
      this.maxQuoteAgeSeconds = maxQuoteAgeSeconds;
      return this;
    }

    public Builder maxFeePerGasGwei(BigDecimal maxFeePerGasGwei) {
      this.maxFeePerGasGwei = maxFeePerGasGwei;
      return this;
    }

    public Builder maxPriorityFeePerGasGwei(BigDecimal maxPriorityFeePerGasGwei) {
      this.maxPriorityFeePerGasGwei = maxPriorityFeePerGasGwei;
      return this;
    }

    public Builder maxGasLimit(long maxGasLimit) {
      this.maxGasLimit = maxGasLimit;
      return this;
    }

    public Builder allowanceMarginBps(int allowanceMarginBps) {
      this.allowanceMarginBps = allowanceMarginBps;
      return this;
    }

    public Builder verifyOnStartup(boolean verifyOnStartup) {
      this.verifyOnStartup = verifyOnStartup;
      return this;
    }

    public UniswapConfig build() {
      validate();
      return new UniswapConfig(this);
    }

    private void validate() {
      Objects.requireNonNull(rpcUrl, "rpcUrl");
      if (!(rpcUrl.startsWith("http://") || rpcUrl.startsWith("https://"))) {
        throw new IllegalArgumentException("rpcUrl must be http(s)");
      }
      Objects.requireNonNull(walletAddress, "walletAddress");
      Objects.requireNonNull(keystorePath, "keystorePath");
      Objects.requireNonNull(tokens, "tokens");
      Objects.requireNonNull(pools, "pools");
      Objects.requireNonNull(deployment, "deployment");
      if (quoteRefSize.signum() <= 0) {
        throw new IllegalArgumentException("quoteRefSize must be positive");
      }
      if (maxSlippageBps <= 0 || maxSlippageBps > MAX_SLIPPAGE_BPS) {
        throw new IllegalArgumentException("maxSlippageBps out of range");
      }
      if (maxDeadlineSeconds <= 0 || maxQuoteAgeSeconds <= 0 || maxGasLimit <= 0) {
        throw new IllegalArgumentException("deadline, quote age, and gas limit must be positive");
      }
      if (maxFeePerGasGwei.signum() <= 0 || maxPriorityFeePerGasGwei.signum() <= 0) {
        throw new IllegalArgumentException("fee caps must be positive");
      }
      if (allowanceMarginBps < 0) {
        throw new IllegalArgumentException("allowanceMarginBps must not be negative");
      }
      if (!Files.isRegularFile(keystorePath)) {
        throw new IllegalArgumentException("keystore is not a regular file: " + keystorePath);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UniswapConfig)) {
      return false;
    }
    UniswapConfig that = (UniswapConfig) o;
    return maxSlippageBps == that.maxSlippageBps
        && maxDeadlineSeconds == that.maxDeadlineSeconds
        && maxQuoteAgeSeconds == that.maxQuoteAgeSeconds
        && maxGasLimit == that.maxGasLimit
        && allowanceMarginBps == that.allowanceMarginBps
        && verifyOnStartup == that.verifyOnStartup
        && chainId.equals(that.chainId)
        && rpcUrl.equals(that.rpcUrl)
        && walletAddress.equals(that.walletAddress)
        && keystorePath.equals(that.keystorePath)
        && tokens.equals(that.tokens)
        && pools.equals(that.pools)
        && deployment.equals(that.deployment)
        && quoteRefSize.equals(that.quoteRefSize)
        && maxFeePerGasGwei.equals(that.maxFeePerGasGwei)
        && maxPriorityFeePerGasGwei.equals(that.maxPriorityFeePerGasGwei);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        chainId,
        rpcUrl,
        walletAddress,
        keystorePath,
        tokens,
        pools,
        deployment,
        quoteRefSize,
        maxSlippageBps,
        maxDeadlineSeconds,
        maxQuoteAgeSeconds,
        maxFeePerGasGwei,
        maxPriorityFeePerGasGwei,
        maxGasLimit,
        allowanceMarginBps,
        verifyOnStartup);
  }

  @Override
  public String toString() {
    return "UniswapConfig{chainId="
        + chainId
        + ", rpcUrl="
        + rpcUrl
        + ", walletAddress="
        + walletAddress
        + ", keystorePath="
        + keystorePath
        + ", pools="
        + pools
        + '}';
  }
}
