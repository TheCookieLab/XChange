package org.knowm.xchange.coinbase.v3.dto.converts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Trade incentive metadata for convert quotes.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseTradeIncentiveMetadata {

  private final String userIncentiveId;
  private final String codeVal;

  @JsonCreator
  public CoinbaseTradeIncentiveMetadata(
      @JsonProperty("user_incentive_id") String userIncentiveId,
      @JsonProperty("code_val") String codeVal) {
    this.userIncentiveId = userIncentiveId;
    this.codeVal = codeVal;
  }

  @Override
  public String toString() {
    return "CoinbaseTradeIncentiveMetadata [userIncentiveId=" + userIncentiveId
        + ", codeVal=" + codeVal + "]";
  }
}
