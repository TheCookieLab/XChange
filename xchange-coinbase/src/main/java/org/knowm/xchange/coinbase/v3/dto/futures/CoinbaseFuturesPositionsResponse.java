package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing a list of futures positions.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getfcmpositions">List Futures Positions</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesPositionsResponse {

  private final List<CoinbaseFuturesPosition> positions;

  @JsonCreator
  public CoinbaseFuturesPositionsResponse(@JsonProperty("positions") List<CoinbaseFuturesPosition> positions) {
    this.positions = positions == null ? Collections.emptyList() : Collections.unmodifiableList(positions);
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesPositionsResponse [positions count=" + (positions == null ? 0 : positions.size()) + "]";
  }
}

