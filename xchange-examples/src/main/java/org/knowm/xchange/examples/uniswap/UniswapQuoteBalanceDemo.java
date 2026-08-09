package org.knowm.xchange.examples.uniswap;

import java.io.IOException;
import java.math.BigDecimal;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.uniswap.UniswapConfig;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.client.UniswapNodeClient;
import org.knowm.xchange.uniswap.dto.UniswapQuote;
import org.knowm.xchange.uniswap.service.UniswapMarketDataServiceRaw;

/**
 * Unfunded Uniswap v4 read-only example: quotes, ticker, and balances through a loopback or
 * SSH-forwarded node. No secrets and no funded default — the keystore must exist and its password
 * comes from the {@code UNISWAP_KEYSTORE_PASSWORD} environment variable.
 *
 * <p>Run with:
 *
 * <pre>
 * UNISWAP_KEYSTORE_PASSWORD=… mvn -pl xchange-examples exec:java \
 *   -Dexec.mainClass=org.knowm.xchange.examples.uniswap.UniswapQuoteBalanceDemo
 * </pre>
 */
public final class UniswapQuoteBalanceDemo {

  private UniswapQuoteBalanceDemo() {}

  public static void main(String[] args) throws IOException {
    String keystore = System.getProperty("uniswap.keystore", System.getenv("UNISWAP_KEYSTORE_PATH"));
    if (keystore == null) {
      System.err.println(
          "Usage: -Duniswap.keystore=/path/to/keystore.json (create it with"
              + " LocalKeystoreSigner.createKeystore or a Web3 wallet tool)");
      return;
    }

    ExchangeSpecification specification = new ExchangeSpecification(UniswapExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    specification.setExchangeName("Uniswap");
    specification.setSslUri("http://127.0.0.1:18545"); // SSH local forward to the node's 8545
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.WALLET_ADDRESS, walletFromKeystore(keystore));
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.KEYSTORE_PATH, keystore);
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.TOKENS,
        "["
            + "{\"symbol\":\"ETH\",\"address\":\"0x0000000000000000000000000000000000000000\",\"decimals\":18,\"native\":true},"
            + "{\"symbol\":\"WETH\",\"address\":\"0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2\",\"decimals\":18},"
            + "{\"symbol\":\"USDC\",\"address\":\"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\",\"decimals\":6}"
            + "]");
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.POOL_KEYS,
        "[{\"pair\":\"ETH/USDC\",\"currency0\":\"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48\","
            + "\"currency1\":\"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2\",\"fee\":3000,\"tickSpacing\":60}]");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.DEPLOYMENTS, deployments());
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_SLIPPAGE_BPS, "100");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_DEADLINE_SECONDS, "600");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_QUOTE_AGE_SECONDS, "60");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_FEE_PER_GAS_GWEI, "100");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_PRIORITY_FEE_PER_GAS_GWEI, "10");
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.MAX_GAS_LIMIT, "1000000");
    // The demo is read-only; pin the deployment code hashes and enable verification when running
    // against a real funded configuration.
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "false");

    UniswapExchange exchange = new UniswapExchange();
    exchange.applySpecification(specification);
    try {
      UniswapMarketDataServiceRaw rawMarketData = (UniswapMarketDataServiceRaw) exchange.getMarketDataService();
      org.knowm.xchange.currency.CurrencyPair pair =
          new org.knowm.xchange.currency.CurrencyPair(
              org.knowm.xchange.currency.Currency.ETH, org.knowm.xchange.currency.Currency.USDC);
      UniswapQuote quote = rawMarketData.quoteExactInput(pair, new BigDecimal("0.01"));
      System.out.printf("0.01 ETH -> %.6f USDC @ block %d%n", quote.amountOut(), quote.blockNumber());

      Ticker ticker = exchange.getMarketDataService().getTicker(pair);
      System.out.printf("ETH/USDC ticker bid=%s ask=%s%n", ticker.getBid(), ticker.getAsk());

      AccountInfo account = exchange.getAccountService().getAccountInfo();
      account.getWallet().getBalances().values().forEach(
          balance -> System.out.printf("balance %s = %s%n", balance.getCurrency(), balance.getTotal()));
    } finally {
      exchange.close();
    }
  }

  /** Placeholder: pin keccak-256 of the runtime code from your node or the official registry. */
  private static String deployments() {
    String hash = System.getProperty("uniswap.codeHashes", "");
    if (hash.isEmpty()) {
      System.err.println(
          "WARNING: pin the deployment code hashes (-Duniswap.codeHashes=<poolManager>,<quoter>,<router>,<permit2>) "
              + "or the startup bytecode check will reject this configuration");
    }
    return "{\"poolManager\":\"0x000000000004444c5dc75cB358380D2e3dE08A90\","
        + "\"quoter\":\"0x52f0e24d1c21c8a0cb1e5a5dd6198556bd9e1203\","
        + "\"universalRouter\":\"0x4c82d1fbfe28c977cbb58d8c7ff8fcf9f70a2cca\","
        + "\"permit2\":\"0x000000000022D473030F116dDEE9F6B43aC78BA3\","
        + "\"codeHashes\":{}}";
  }

  private static String walletFromKeystore(String keystore) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(keystore))) {
        org.web3j.crypto.WalletFile walletFile = mapper.readValue(in, org.web3j.crypto.WalletFile.class);
        return ("0x" + walletFile.getAddress()).toLowerCase();
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("cannot read keystore address from " + keystore, e);
    }
  }
}
