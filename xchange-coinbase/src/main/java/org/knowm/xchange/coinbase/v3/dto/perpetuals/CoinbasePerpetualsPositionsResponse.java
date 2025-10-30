package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing a list of perpetuals positions.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getintxpositions">List Perpetuals Positions</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePerpetualsPositionsResponse {

  private final List<CoinbasePerpetualsPosition> positions;

  @JsonCreator
  public CoinbasePerpetualsPositionsResponse(@JsonProperty("positions") List<CoinbasePerpetualsPosition> positions) {
    this.positions = positions == null ? Collections.emptyList() : Collections.unmodifiableList(positions);
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualsPositionsResponse [positions count=" + (positions == null ? 0 : positions.size()) + "]";
  }
}

