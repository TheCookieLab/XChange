package org.knowm.xchange.okx.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@Getter
public class OkxOrderbook {

  private final List<OkxPublicOrder> asks;

  private final List<OkxPublicOrder> bids;
  private final String ts;

  @JsonCreator
  public OkxOrderbook(
      @JsonProperty("asks") List<OkxPublicOrder> asks,
      @JsonProperty("bids") List<OkxPublicOrder> bids,
      @JsonProperty("ts") String ts) {

    this.asks = asks;
    this.bids = bids;
    this.ts = ts;
  }

  @Override
  public String toString() {
    return "OkxOrderbookResponse{" + "asks=" + asks + ", bids=" + bids + '}';
  }
}
