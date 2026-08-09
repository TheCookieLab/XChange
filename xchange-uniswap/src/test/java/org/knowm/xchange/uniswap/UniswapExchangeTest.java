package org.knowm.xchange.uniswap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.uniswap.client.UniswapNodeClient;

/** Startup chain/deployment verification and service wiring (AC2, AC4). */
class UniswapExchangeTest {

  @TempDir Path tempDir;

  @Test
  void appliesSpecificationAndWiresServices() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "false");

    UniswapExchange exchange = new UniswapExchange();
    exchange.applySpecification(specification);

    assertThat(exchange.getMarketDataService()).isInstanceOf(org.knowm.xchange.uniswap.service.UniswapMarketDataService.class);
    assertThat(exchange.getTradeService()).isInstanceOf(org.knowm.xchange.uniswap.service.UniswapTradeService.class);
    assertThat(exchange.getAccountService()).isInstanceOf(org.knowm.xchange.uniswap.service.UniswapAccountService.class);
    assertThat(exchange.getExchangeInstruments()).contains(TestFixtures.ETH_USDC_PAIR);
  }

  @Test
  void rejectsWrongChainIdAtStartup() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "true");

    UniswapNodeClient client = mock(UniswapNodeClient.class);
    when(client.chainId()).thenReturn(BigInteger.valueOf(137));

    UniswapExchange exchange = new UniswapExchange();
    exchange.setNodeClientForTesting(client);
    assertThatThrownBy(() -> exchange.applySpecification(specification))
        .isInstanceOf(org.knowm.xchange.exceptions.ExchangeException.class)
        .hasMessageContaining("chain id 137");
  }

  @Test
  void rejectsDeploymentBytecodeMismatchAtStartup() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "true");

    UniswapNodeClient client = mock(UniswapNodeClient.class);
    when(client.chainId()).thenReturn(BigInteger.ONE);
    when(client.blockNumber()).thenReturn(BigInteger.valueOf(100));
    when(client.codeAt(anyString(), any())).thenReturn("0x6000");

    UniswapExchange exchange = new UniswapExchange();
    exchange.setNodeClientForTesting(client);
    assertThatThrownBy(() -> exchange.applySpecification(specification))
        .isInstanceOf(org.knowm.xchange.exceptions.ExchangeException.class)
        .hasMessageContaining("runtime code mismatch")
        .hasMessageContaining("POOL_MANAGER");
  }

  @Test
  void acceptsMatchingChainAndBytecodeAtStartup() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "true");

    // code bytes whose keccak equals the fixture hash is impractical to fabricate; instead verify
    // that a matching-hash stub passes by computing the hash of stub code and pinning it
    byte[] code = new byte[] {0x60, 0x00, 0x60, 0x00};
    String expectedHash =
        "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(org.web3j.crypto.Hash.sha3(code));
    specification.setExchangeSpecificParametersItem(
        UniswapConfig.Keys.DEPLOYMENTS,
        "{\"poolManager\":\"" + TestFixtures.POOL_MANAGER + "\",\"quoter\":\"" + TestFixtures.QUOTER
            + "\",\"universalRouter\":\"" + TestFixtures.ROUTER + "\",\"permit2\":\"" + TestFixtures.PERMIT2
            + "\",\"codeHashes\":{\"poolManager\":\"" + expectedHash + "\",\"quoter\":\"" + expectedHash
            + "\",\"universalRouter\":\"" + expectedHash + "\",\"permit2\":\"" + expectedHash + "\"}}");

    UniswapNodeClient client = mock(UniswapNodeClient.class);
    when(client.chainId()).thenReturn(BigInteger.ONE);
    when(client.blockNumber()).thenReturn(BigInteger.valueOf(100));
    when(client.codeAt(anyString(), any()))
        .thenReturn("0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(code));

    UniswapExchange exchange = new UniswapExchange();
    exchange.setNodeClientForTesting(client);
    exchange.applySpecification(specification);
    assertThat(exchange.getMarketDataService()).isNotNull();
  }

  @Test
  void rejectsNoCodeAtDeploymentAddress() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    ExchangeSpecification specification = TestFixtures.specification(keystore);
    specification.setExchangeSpecificParametersItem(UniswapConfig.Keys.VERIFY_ON_STARTUP, "true");

    UniswapNodeClient client = mock(UniswapNodeClient.class);
    when(client.chainId()).thenReturn(BigInteger.ONE);
    when(client.blockNumber()).thenReturn(BigInteger.valueOf(100));
    when(client.codeAt(anyString(), any())).thenReturn("0x");

    UniswapExchange exchange = new UniswapExchange();
    exchange.setNodeClientForTesting(client);
    assertThatThrownBy(() -> exchange.applySpecification(specification))
        .isInstanceOf(org.knowm.xchange.exceptions.ExchangeException.class)
        .hasMessageContaining("no runtime code");
  }

  @Test
  void tickerRequiresAConfiguredPool() throws Exception {
    Path keystore = TestFixtures.keystore(tempDir, "s3cret".toCharArray());
    UniswapExchange exchange = new UniswapExchange();
    exchange.applySpecification(TestFixtures.specification(keystore));
    org.knowm.xchange.instrument.Instrument instrument = org.knowm.xchange.currency.CurrencyPair.BTC_USD;
    assertThatThrownBy(() -> exchange.getMarketDataService().getTicker(instrument))
        .isInstanceOf(org.knowm.xchange.exceptions.NotAvailableFromExchangeException.class);
  }
}
