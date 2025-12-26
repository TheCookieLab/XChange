package org.knowm.xchange.coinbase.v3.dto.converts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Request payload for creating a convert quote.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/convert/create-convert-quote.md">Create Convert Quote</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseConvertQuoteRequest {

  private final String fromAccount;
  private final String toAccount;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal amount;
  private final CoinbaseTradeIncentiveMetadata tradeIncentiveMetadata;

  @JsonCreator
  public CoinbaseConvertQuoteRequest(
      @JsonProperty("from_account") String fromAccount,
      @JsonProperty("to_account") String toAccount,
      @JsonProperty("amount") BigDecimal amount,
      @JsonProperty("trade_incentive_metadata") CoinbaseTradeIncentiveMetadata tradeIncentiveMetadata) {
    this.fromAccount = fromAccount;
    this.toAccount = toAccount;
    this.amount = amount;
    this.tradeIncentiveMetadata = tradeIncentiveMetadata;
  }

  @Override
  public String toString() {
    return "CoinbaseConvertQuoteRequest [fromAccount=" + fromAccount
        + ", toAccount=" + toAccount + ", amount=" + amount
        + ", tradeIncentiveMetadata=" + tradeIncentiveMetadata + "]";
  }
}
