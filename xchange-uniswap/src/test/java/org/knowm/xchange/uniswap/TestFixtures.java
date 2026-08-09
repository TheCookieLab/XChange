package org.knowm.xchange.uniswap;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.uniswap.DeploymentRegistry.Deployment;
import org.knowm.xchange.uniswap.TokenRegistry.Token;
import org.knowm.xchange.uniswap.dto.UniswapReceiptDecoder;
import org.knowm.xchange.uniswap.signing.LocalKeystoreSigner;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** Shared fixtures for the Uniswap module tests. */
public final class TestFixtures {

  public static final String WETH = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";
  public static final String USDC = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
  public static final String ZERO = "0x0000000000000000000000000000000000000000";
  public static final String ROUTER = "0x4c82d1fbfe28c977cbb58d8c7ff8fcf9f70a2cca";
  public static final String POOL_MANAGER = "0x000000000004444c5dc75cB358380D2e3dE08A90";
  public static final String QUOTER = "0x52f0e24d1c21c8a0cb1e5a5dd6198556bd9e1203";
  public static final String PERMIT2 = "0x000000000022D473030F116dDEE9F6B43aC78BA3";

  /** Fixture wallet: the address of private key 1, matching {@link #keystore(Path, char[])}. */
  public static final String WALLET = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf";
  public static final String HASH_AB =
      "0xabababababababababababababababababababababababababababababababab";

  /** The fixture pair (ETH base, USDC quote). */
  public static final org.knowm.xchange.currency.CurrencyPair ETH_USDC_PAIR =
      new org.knowm.xchange.currency.CurrencyPair(
          org.knowm.xchange.currency.Currency.ETH, org.knowm.xchange.currency.Currency.USDC);

  private TestFixtures() {}

  public static TokenRegistry tokens() {
    return TokenRegistry.of(
        Arrays.asList(
            new Token("ETH", "0x0000000000000000000000000000000000000000", 18, true),
            new Token("WETH", WETH, 18, false),
            new Token("USDC", USDC, 6, false)));
  }

  /** Pool ETH/USDC: currencies sorted USDC (0xa0b8..) < WETH (0xC02a..). */
  public static PoolKeyRegistry pools() {
    return PoolKeyRegistry.of(
        java.util.List.of(
            PoolKeyRegistry.PoolDefinition.create(
                "ETH", "USDC", USDC.toLowerCase(), WETH.toLowerCase(), 3000, 60, ZERO, tokens())),
        tokens());
  }

  public static Deployment deployment() {
    return new Deployment(
        POOL_MANAGER,
        QUOTER,
        ROUTER,
        PERMIT2,
        WETH,
        java.util.Map.of(
            DeploymentRegistry.Contract.POOL_MANAGER, HASH_AB,
            DeploymentRegistry.Contract.QUOTER, HASH_AB,
            DeploymentRegistry.Contract.UNIVERSAL_ROUTER, HASH_AB,
            DeploymentRegistry.Contract.PERMIT2, HASH_AB));
  }

  public static String deploymentsJson() {
    return "{\"poolManager\":\"" + POOL_MANAGER + "\",\"quoter\":\"" + QUOTER
        + "\",\"universalRouter\":\"" + ROUTER + "\",\"permit2\":\"" + PERMIT2
        + "\",\"weth\":\"" + WETH + "\",\"codeHashes\":{"
        + "\"poolManager\":\"" + HASH_AB + "\",\"quoter\":\"" + HASH_AB
        + "\",\"universalRouter\":\"" + HASH_AB + "\",\"permit2\":\"" + HASH_AB + "\"}}";
  }

  public static String tokensJson() {
    return "["
        + "{\"symbol\":\"ETH\",\"address\":\"0x0000000000000000000000000000000000000000\",\"decimals\":18,\"native\":true},"
        + "{\"symbol\":\"WETH\",\"address\":\"" + WETH + "\",\"decimals\":18},"
        + "{\"symbol\":\"USDC\",\"address\":\"" + USDC + "\",\"decimals\":6}"
        + "]";
  }

  public static String poolsJson() {
    return "[{\"pair\":\"ETH/USDC\",\"currency0\":\"" + USDC.toLowerCase()
        + "\",\"currency1\":\"" + WETH.toLowerCase() + "\",\"fee\":3000,\"tickSpacing\":60,"
        + "\"hooks\":\"" + ZERO + "\"}]";
  }

  /** Creates a keystore for the fixture wallet (private key 1) with owner-only permissions. */
  public static Path keystore(Path tempDir, char[] password) throws Exception {
    Path path = tempDir.resolve("wallet.json");
    org.web3j.crypto.WalletFile walletFile =
        org.web3j.crypto.Wallet.createStandard(
            new String(password), org.web3j.crypto.ECKeyPair.create(java.math.BigInteger.ONE));
    byte[] json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(walletFile);
    Files.write(path, json, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    Files.setPosixFilePermissions(
        path,
        java.util.Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
    return path;
  }

  /** A specification with valid configuration; {@code verifyOnStartup} is off by default. */
  static ExchangeSpecification specification(Path keystorePath) {
    ExchangeSpecification specification =
        new ExchangeSpecification(org.knowm.xchange.uniswap.UniswapExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    specification.setExchangeName("Uniswap");
    specification.setSslUri("http://127.0.0.1:18545");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.WALLET_ADDRESS, WALLET);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.KEYSTORE_PATH, keystorePath.toString());
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.TOKENS, tokensJson());
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.POOL_KEYS, poolsJson());
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.DEPLOYMENTS, deploymentsJson());
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_SLIPPAGE_BPS, "100");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_DEADLINE_SECONDS, "600");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_QUOTE_AGE_SECONDS, "60");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_FEE_PER_GAS_GWEI, "100");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_PRIORITY_FEE_PER_GAS_GWEI, "10");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_GAS_LIMIT, "1000000");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "false");
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.PASSWORD_PROVIDER_CLASS, TestSecretProvider.class.getName());
    return specification;
  }

  /** Secret provider bound to the shared test password. */
  public static final class TestSecretProvider implements org.knowm.xchange.uniswap.signing.SecretProvider {
    @Override
    public char[] password() {
      return "s3cret".toCharArray();
    }
  }

  /** A success receipt with one matching Swap log for the fixture pool. */
  public static TransactionReceipt successReceipt(String poolId, String router, String poolManager) {
    TransactionReceipt receipt = new TransactionReceipt();
    receipt.setStatus("0x1");
    receipt.setTransactionHash("0x" + "11".repeat(32));
    receipt.setBlockNumber("0x64");
    receipt.setGasUsed("0x5208");
    receipt.setEffectiveGasPrice("0x3b9aca00"); // 1 gwei
    Log log = new Log();
    log.setAddress(poolManager);
    log.setTopics(
        Arrays.asList(
            UniswapReceiptDecoder.SWAP_EVENT_TOPIC,
            poolId,
            router.toLowerCase(),
            "0x0000000000000000000000000000000000000000000000000000000000000000"));
    log.setData(swapLogData(-1_000_000L, 1_000_000_000L));
    receipt.setLogs(java.util.List.of(log));
    return receipt;
  }

  /** Builds the six non-indexed words of the PoolManager Swap event. */
  public static String swapLogData(long amount0, long amount1) {
    return "0x"
        + int128(amount0)
        + int128(amount1)
        + "00".repeat(32) // sqrtPriceX96
        + "00".repeat(32) // liquidity
        + "00".repeat(32) // tick
        + "00".repeat(32); // fee
  }

  /** 64-hex-char (32-byte) two's-complement word of a signed long, sign-extended like Solidity. */
  public static String int128(long value) {
    if (value < 0) {
      return "f".repeat(48) + Long.toHexString(value);
    }
    String hex = Long.toHexString(value);
    return "0".repeat(64 - hex.length()) + hex;
  }

  /** 64-hex-char (32-byte) zero-padded word of a long. */
  public static String word(long value) {
    String hex = Long.toHexString(value);
    return "0".repeat(64 - hex.length()) + hex;
  }

  /** Writes a keystore-looking file with the given POSIX permissions. */
  public static Path fileWithPermissions(Path tempDir, String name, Set<java.nio.file.attribute.PosixFilePermission> permissions)
      throws IOException {
    Path path = tempDir.resolve(name);
    Files.write(path, "{}".getBytes());
    Files.setPosixFilePermissions(path, permissions);
    return path;
  }
}
