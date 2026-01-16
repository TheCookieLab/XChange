package org.knowm.xchange.coinbase.v3.dto.converts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Request payload for committing a convert trade.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/convert/commit-convert-trade.md">Commit Convert Trade</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseCommitConvertTradeRequest {

  private final String fromAccount;
  private final String toAccount;

  @JsonCreator
  public CoinbaseCommitConvertTradeRequest(
      @JsonProperty("from_account") String fromAccount,
      @JsonProperty("to_account") String toAccount) {
    this.fromAccount = fromAccount;
    this.toAccount = toAccount;
  }

  @Override
  public String toString() {
    return "CoinbaseCommitConvertTradeRequest [fromAccount=" + fromAccount
        + ", toAccount=" + toAccount + "]";
  }
}
