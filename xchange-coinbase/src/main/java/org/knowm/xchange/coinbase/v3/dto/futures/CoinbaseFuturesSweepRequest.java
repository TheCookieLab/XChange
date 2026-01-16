package org.knowm.xchange.coinbase.v3.dto.futures;

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
 * Request payload for scheduling a futures sweep.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/futures/schedule-futures-sweep.md">Schedule Futures Sweep</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseFuturesSweepRequest {

  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal usdAmount;

  @JsonCreator
  public CoinbaseFuturesSweepRequest(@JsonProperty("usd_amount") BigDecimal usdAmount) {
    this.usdAmount = usdAmount;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesSweepRequest [usdAmount=" + usdAmount + "]";
  }
}
