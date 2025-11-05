package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing a list of futures sweeps.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_listfcmsweeps">List Futures Sweeps</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesSweepsResponse {

  private final List<CoinbaseFuturesSweep> sweeps;

  @JsonCreator
  public CoinbaseFuturesSweepsResponse(@JsonProperty("sweeps") List<CoinbaseFuturesSweep> sweeps) {
    this.sweeps = sweeps == null ? Collections.emptyList() : Collections.unmodifiableList(sweeps);
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesSweepsResponse [sweeps count=" + (sweeps == null ? 0 : sweeps.size()) + "]";
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CoinbaseFuturesSweep {
    private final String id;
    private final String requestedAmount;
    private final Boolean shouldSweepAll;
    private final String scheduledTime;

    @JsonCreator
    public CoinbaseFuturesSweep(
        @JsonProperty("id") String id,
        @JsonProperty("requested_amount") String requestedAmount,
        @JsonProperty("should_sweep_all") Boolean shouldSweepAll,
        @JsonProperty("scheduled_time") String scheduledTime) {
      this.id = id;
      this.requestedAmount = requestedAmount;
      this.shouldSweepAll = shouldSweepAll;
      this.scheduledTime = scheduledTime;
    }
  }
}

