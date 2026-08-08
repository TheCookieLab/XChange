package org.knowm.xchange.uniswap.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.uniswap.TokenRegistry.Token;
import org.knowm.xchange.uniswap.UniswapConfig;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.util.Amounts;

/**
 * Raw account reads: native and ERC-20 balances of the configured wallet, always read through the
 * node's public namespaces — the {@code personal} namespace and node-side unlock are never used.
 */
public class UniswapAccountServiceRaw extends BaseExchangeService<UniswapExchange> {

  protected UniswapAccountServiceRaw(UniswapExchange exchange) {
    super(exchange);
  }

  /** Native currency (ETH) balance in human-readable units at the latest block. */
  public BigDecimal getNativeBalance() throws IOException {
    UniswapConfig config = exchange.getConfig();
    BigInteger atBlock = exchange.getNodeClient().blockNumber();
    BigInteger raw = exchange.getNodeClient().nativeBalance(config.walletAddress(), atBlock);
    Token nativeToken = nativeToken(config);
    return Amounts.toHuman(raw, nativeToken == null ? 18 : nativeToken.decimals());
  }

  /** Balance of a configured token (or the native currency) in human-readable units. */
  public BigDecimal getBalance(Currency currency) throws IOException {
    UniswapConfig config = exchange.getConfig();
    BigInteger atBlock = exchange.getNodeClient().blockNumber();
    return getBalance(currency, atBlock);
  }

  /** Balance of a configured token (or the native currency) at a captured block. */
  public BigDecimal getBalance(Currency currency, BigInteger atBlock) throws IOException {
    UniswapConfig config = exchange.getConfig();
    Token token = config.tokens().bySymbol(currency.getCurrencyCode());
    if (token == null) {
      throw new org.knowm.xchange.exceptions.NotAvailableFromExchangeException(
          "no configured token for " + currency);
    }
    BigInteger raw;
    if (token.nativeCurrency()) {
      raw = exchange.getNodeClient().nativeBalance(config.walletAddress(), atBlock);
    } else {
      raw = exchange.getNodeClient().tokenBalance(token.address(), config.walletAddress(), atBlock);
    }
    return Amounts.toHuman(raw, token.decimals());
  }

  /** Balances of every configured token plus the native currency at the latest block. */
  public List<Balance> getBalances() throws IOException {
    UniswapConfig config = exchange.getConfig();
    BigInteger atBlock = exchange.getNodeClient().blockNumber();
    List<Balance> balances = new ArrayList<>();
    for (Token token : config.tokens().all()) {
      BigDecimal human;
      if (token.nativeCurrency()) {
        human = Amounts.toHuman(exchange.getNodeClient().nativeBalance(config.walletAddress(), atBlock), token.decimals());
      } else {
        human =
            Amounts.toHuman(
                exchange.getNodeClient().tokenBalance(token.address(), config.walletAddress(), atBlock),
                token.decimals());
      }
      balances.add(new Balance(Currency.getInstance(token.symbol()), human));
    }
    return balances;
  }

  private static Token nativeToken(UniswapConfig config) {
    for (Token token : config.tokens().all()) {
      if (token.nativeCurrency()) {
        return token;
      }
    }
    return null;
  }
}
