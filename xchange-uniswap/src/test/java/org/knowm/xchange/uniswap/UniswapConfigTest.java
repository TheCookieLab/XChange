package org.knowm.xchange.uniswap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowm.xchange.ExchangeSpecification;

/** Fail-closed configuration parsing (acceptance criterion AC2). */
class UniswapConfigTest {

  @TempDir Path tempDir;

  @Test
  void parsesACompleteConfiguration() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    UniswapConfig config = UniswapConfig.from(TestFixtures.specification(keystore));

    assertThat(config.chainId()).isEqualTo(UniswapConfig.MAINNET_CHAIN_ID);
    assertThat(config.rpcUrl()).isEqualTo("http://127.0.0.1:18545");
    assertThat(config.walletAddress()).isEqualTo(TestFixtures.WALLET);
    assertThat(config.maxSlippageBps()).isEqualTo(100);
    assertThat(config.maxDeadlineSeconds()).isEqualTo(600);
    assertThat(config.maxGasLimit()).isEqualTo(1_000_000);
    assertThat(config.pools().byPair(TestFixtures.ETH_USDC_PAIR)).isNotNull();
    assertThat(config.tokens().bySymbol("WETH").decimals()).isEqualTo(18);
    assertThat(config.deployment().universalRouter()).isEqualTo(TestFixtures.ROUTER);
    assertThat(config.verifyOnStartup()).isFalse();
  }

  @Test
  void rejectsMissingWalletAddress() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.WALLET_ADDRESS, null);
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uniswap.wallet-address");
  }

  @Test
  void rejectsMalformedWalletAddress() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.WALLET_ADDRESS, "0x1234");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid Ethereum address");
  }

  @Test
  void rejectsMissingRpcEndpoint() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setSslUri(null);
    specification.setPlainTextUri(null);
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RPC endpoint");
  }

  @Test
  void rejectsKeystorePathThatIsADirectory() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.KEYSTORE_PATH, tempDir.toString());
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a regular file");
  }

  @Test
  void rejectsGroupWritableKeystore() throws Exception {
    Path keystore =
        TestFixtures.fileWithPermissions(
            tempDir, "loose.json", EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_WRITE));
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("writable by group or others");
  }

  @Test
  void rejectsWrongChainId() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.CHAIN_ID, "137");
    UniswapConfig config = UniswapConfig.from(specification);
    assertThat(config.chainId()).isEqualTo(BigInteger.valueOf(137));
  }

  @Test
  void rejectsGarbageChainId() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.CHAIN_ID, "mainnet");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chain-id");
  }

  @Test
  void rejectsPoolWithUnconfiguredToken() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.POOL_KEYS,
        "[{\"pair\":\"ETH/UNI\",\"currency0\":\"0x1111111111111111111111111111111111111111\","
            + "\"currency1\":\"0x1f9840a85d5af5bf1d1762f925bdaddc4201f984\",\"fee\":3000,\"tickSpacing\":60}]");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unconfigured quote token UNI");
  }

  @Test
  void rejectsUnsortedPoolCurrencies() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.POOL_KEYS,
        "[{\"pair\":\"ETH/USDC\",\"currency0\":\"0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2\","
            + "\"currency1\":\"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\",\"fee\":3000,\"tickSpacing\":60}]");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not sorted");
  }

  @Test
  void rejectsIdenticalPoolCurrencies() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.POOL_KEYS,
        "[{\"pair\":\"ETH/USDC\",\"currency0\":\"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\","
            + "\"currency1\":\"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\",\"fee\":3000,\"tickSpacing\":60}]");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("identical currencies");
  }

  @Test
  void rejectsInvalidPoolFee() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.POOL_KEYS,
        "[{\"pair\":\"ETH/USDC\",\"currency0\":\"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\","
            + "\"currency1\":\"0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2\",\"fee\":2000000,\"tickSpacing\":60}]");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fee");
  }

  @Test
  void rejectsDuplicateTokenSymbol() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.TOKENS,
        "[{\"symbol\":\"WETH\",\"address\":\"0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2\",\"decimals\":18},"
            + "{\"symbol\":\"WETH\",\"address\":\"0x0000000000000000000000000000000000000001\",\"decimals\":18}]");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate token symbol");
  }

  @Test
  void rejectsMissingRiskBounds() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_SLIPPAGE_BPS, null);
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uniswap.max-slippage-bps");
  }

  @Test
  void rejectsOutOfRangeSlippage() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_SLIPPAGE_BPS, "20000");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 10000");
  }

  @Test
  void rejectsDeploymentWithoutCodeHashes() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.DEPLOYMENTS,
        "{\"poolManager\":\"" + TestFixtures.POOL_MANAGER + "\",\"quoter\":\"" + TestFixtures.QUOTER
            + "\",\"universalRouter\":\"" + TestFixtures.ROUTER + "\",\"permit2\":\"" + TestFixtures.PERMIT2 + "\"}");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected code hash for POOL_MANAGER");
  }

  @Test
  void rejectsMalformedTokenJson() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.TOKENS, "not json");
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not valid JSON");
  }

  @Test
  void poolDefinitionComputesZeroForOneFromInputAddress() {
    PoolKeyRegistry.PoolDefinition pool = TestFixtures.pools().byPair(TestFixtures.ETH_USDC_PAIR);
    assertThat(pool.zeroForOne(TestFixtures.USDC)).isTrue();
    assertThat(pool.zeroForOne(TestFixtures.WETH)).isFalse();
    assertThatThrownBy(() -> pool.zeroForOne("0x00000000000000000000000000000000000000ff"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unsafeKeystoreFilePermissionsFailEvenWhenReadable() throws Exception {
    Path keystore =
        TestFixtures.fileWithPermissions(
            tempDir, "loose2.json", EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OTHERS_WRITE));
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    assertThatThrownBy(() -> UniswapConfig.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("writable by group or others");
  }
}
