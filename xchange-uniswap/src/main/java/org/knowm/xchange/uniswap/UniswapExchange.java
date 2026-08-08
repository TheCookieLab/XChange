package org.knowm.xchange.uniswap;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.uniswap.DeploymentRegistry.Contract;
import org.knowm.xchange.uniswap.DeploymentRegistry.Deployment;
import org.knowm.xchange.uniswap.client.UniswapNodeClient;
import org.knowm.xchange.uniswap.protocol.Abi;
import org.knowm.xchange.uniswap.service.UniswapAccountService;
import org.knowm.xchange.uniswap.service.UniswapMarketDataService;
import org.knowm.xchange.uniswap.service.UniswapTradeService;
import org.knowm.xchange.uniswap.signing.LocalKeystoreSigner;
import org.knowm.xchange.uniswap.signing.NonceManager;
import org.knowm.xchange.uniswap.signing.SecretProvider;

/**
 * XChange exchange for Uniswap v4 on Ethereum mainnet.
 *
 * <p>Configuration is fail-closed: the specification is parsed into a typed {@link UniswapConfig}
 * and, unless verification is disabled, the node is checked for the configured chain id and pinned
 * deployment bytecode before the services become usable. Keys never leave the host: signing uses a
 * password-encrypted Web3 V3 keystore through a {@link SecretProvider}.
 */
public class UniswapExchange extends BaseExchange {

  private UniswapConfig config;
  private UniswapNodeClient nodeClient;
  private LocalKeystoreSigner signer;
  private NonceManager nonceManager;

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    ExchangeSpecification specification = new ExchangeSpecification(this.getClass());
    specification.setShouldLoadRemoteMetaData(false);
    specification.setExchangeName("Uniswap");
    specification.setExchangeDescription("Uniswap v4 (Ethereum mainnet)");
    return specification;
  }

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    // Parse and validate the typed configuration before anything becomes usable.
    this.config = UniswapConfig.from(exchangeSpecification);
    if (this.nodeClient == null) {
      this.nodeClient =
          UniswapNodeClient.create(
              config.rpcUrl(),
              exchangeSpecification.getHttpConnTimeout(),
              exchangeSpecification.getHttpReadTimeout());
    }
    if (this.signer == null) {
      this.signer =
          new LocalKeystoreSigner(
              config.keystorePath(), secretProvider(config.passwordProviderClass()), config.walletAddress());
    }
    if (this.nonceManager == null) {
      this.nonceManager = new NonceManager();
    }
    super.applySpecification(exchangeSpecification);
    if (config.verifyOnStartup()) {
      verifyChainAndDeployments();
    }
  }

  @Override
  protected void initServices() {
    this.marketDataService = new UniswapMarketDataService(this);
    this.tradeService = new UniswapTradeService(this);
    this.accountService = new UniswapAccountService(this);
  }

  @Override
  public void remoteInit() {
    // metadata is static; no remote initialization
  }

  @Override
  public List<Instrument> getExchangeInstruments() {
    List<Instrument> instruments = new ArrayList<>();
    for (PoolKeyRegistry.PoolDefinition pool : config.pools().all()) {
      instruments.add(new CurrencyPair(pool.baseSymbol(), pool.quoteSymbol()));
    }
    return instruments;
  }

  /**
   * Fail-closed startup verification: the node must report the configured chain id and every
   * pinned deployment contract must carry the expected runtime bytecode.
   */
  private void verifyChainAndDeployments() {
    try {
      BigInteger chainId = nodeClient.chainId();
      if (!chainId.equals(config.chainId())) {
        throw new ExchangeException(
            "node reports chain id " + chainId + " but configuration requires " + config.chainId());
      }
      Deployment deployment = config.deployment();
      BigInteger atBlock = nodeClient.blockNumber();
      for (Contract contract : Contract.values()) {
        String address = deployment.address(contract);
        String code = nodeClient.codeAt(address, atBlock);
        if (code == null || code.length() <= 2) {
          throw new ExchangeException(
              "no runtime code at " + contract + " address " + address + "; wrong chain or deployment?");
        }
        String actualHash =
            Abi.toHex(org.web3j.crypto.Hash.sha3(Abi.hexToBytes(code)));
        String expectedHash = deployment.expectedCodeHash(contract);
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
          throw new ExchangeException(
              "runtime code mismatch for "
                  + contract
                  + " at "
                  + address
                  + ": expected "
                  + expectedHash
                  + " but observed "
                  + actualHash);
        }
      }
    } catch (IOException e) {
      throw new ExchangeException(
          "failed to verify chain id and deployment bytecode: " + e.getMessage(), e);
    }
  }

  private static SecretProvider secretProvider(String className) {
    try {
      Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
      if (!(instance instanceof SecretProvider)) {
        throw new ExchangeException("password provider " + className + " does not implement SecretProvider");
      }
      return (SecretProvider) instance;
    } catch (ReflectiveOperationException e) {
      throw new ExchangeException("cannot instantiate password provider " + className + ": " + e.getMessage(), e);
    }
  }

  /** The typed configuration of this instance. */
  public UniswapConfig getConfig() {
    return config;
  }

  /** The JSON-RPC client boundary. */
  public UniswapNodeClient getNodeClient() {
    return nodeClient;
  }

  /** The local keystore signer. */
  public LocalKeystoreSigner getSigner() {
    return signer;
  }

  /** The per-address nonce manager. */
  public NonceManager getNonceManager() {
    return nonceManager;
  }

  /** Test seam: replaces the node client before any service call. */
  void setNodeClientForTesting(UniswapNodeClient nodeClient) {
    this.nodeClient = nodeClient;
  }

  /** Releases the underlying JSON-RPC transport. */
  public void close() {
    if (nodeClient != null) {
      nodeClient.close();
    }
  }
}
