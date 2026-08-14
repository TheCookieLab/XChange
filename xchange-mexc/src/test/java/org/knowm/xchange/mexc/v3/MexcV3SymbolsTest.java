package org.knowm.xchange.mexc.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;

/** Symbol conversion between MEXC wire symbols and XChange instruments. */
public class MexcV3SymbolsTest {

  @Test
  public void toMexcSymbolUppercasesBaseAndQuote() {
    assertThat(MexcV3Symbols.toMexcSymbol(CurrencyPair.BTC_USDT)).isEqualTo("BTCUSDT");
    assertThat(MexcV3Symbols.toMexcSymbol(CurrencyPair.ETH_BTC)).isEqualTo("ETHBTC");
  }

  @Test
  public void toCurrencyPairResolvesKnownQuoteSuffix() {
    assertThat(MexcV3Symbols.toCurrencyPair("BTCUSDT")).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(MexcV3Symbols.toCurrencyPair("ETHUSDC"))
        .isEqualTo(new CurrencyPair("ETH", "USDC"));
    assertThat(MexcV3Symbols.toCurrencyPair("MXBTC")).isEqualTo(new CurrencyPair("MX", "BTC"));
  }

  @Test
  public void toCurrencyPairPrefersLongestQuoteMatch() {
    // Both "BTC" and "USDT" are known quotes; "BTCUSDT" must parse as BTC/USDT.
    assertThat(MexcV3Symbols.toCurrencyPair("BTCUSDT").getBase())
        .isEqualTo(CurrencyPair.BTC_USDT.getBase());
    assertThat(MexcV3Symbols.toCurrencyPair("BTCUSDT").getCounter())
        .isEqualTo(CurrencyPair.BTC_USDT.getCounter());
  }

  @Test
  public void toCurrencyPairRejectsUnknownQuote() {
    assertThatThrownBy(() -> MexcV3Symbols.toCurrencyPair("BTCXYZ"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BTCXYZ");
  }

  @Test
  public void toCurrencyPairRejectsTooShortSymbol() {
    assertThatThrownBy(() -> MexcV3Symbols.toCurrencyPair("USDT"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
