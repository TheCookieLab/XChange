package org.knowm.xchange.coinbase.v3.dto.converts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Response from creating a convert quote.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_createconvertquote">Create Convert Quote</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseConvertQuoteResponse {

  private final String tradeId;
  private final String sourceAccount;
  private final String targetAccount;
  private final String fromAccount;
  private final String toAccount;
  private final BigDecimal amount;
  private final String fromCurrency;
  private final String toCurrency;

  @JsonCreator
  public CoinbaseConvertQuoteResponse(
      @JsonProperty("trade_id") String tradeId,
      @JsonProperty("source_account") String sourceAccount,
      @JsonProperty("target_account") String targetAccount,
      @JsonProperty("from_account") String fromAccount,
      @JsonProperty("to_account") String toAccount,
      @JsonProperty("amount") BigDecimal amount,
      @JsonProperty("from_currency") String fromCurrency,
      @JsonProperty("to_currency") String toCurrency) {
    this.tradeId = tradeId;
    this.sourceAccount = sourceAccount;
    this.targetAccount = targetAccount;
    this.fromAccount = fromAccount;
    this.toAccount = toAccount;
    this.amount = amount;
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
  }

  @Override
  public String toString() {
    return "CoinbaseConvertQuoteResponse [tradeId=" + tradeId + ", fromCurrency=" + fromCurrency + ", toCurrency=" + toCurrency + ", amount=" + amount + "]";
  }
}

