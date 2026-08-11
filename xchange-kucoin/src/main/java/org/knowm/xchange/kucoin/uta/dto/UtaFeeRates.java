package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** Actual fee rates for one or more trading pairs. */
@Data
public class UtaFeeRates {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("list")
  private List<UtaFeeRate> list;

  @Data
  public static class UtaFeeRate {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("takerFeeRate")
    private BigDecimal takerFeeRate;

    @JsonProperty("makerFeeRate")
    private BigDecimal makerFeeRate;
  }
}
