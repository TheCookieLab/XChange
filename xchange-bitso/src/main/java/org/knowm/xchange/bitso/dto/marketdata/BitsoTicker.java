package org.knowm.xchange.bitso.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @author Piotr Ładyżyński Updated for Bitso API v3
 */
@Value
@Jacksonized
@Builder
public class BitsoTicker {

  private Boolean success;

  private BitsoTickerData payload;

  @Value
  @Jacksonized
  @Builder
  public static class BitsoTickerData {

    private String book;

    private BigDecimal volume;

    private BigDecimal high;

    private BigDecimal last;

    private BigDecimal low;

    private BigDecimal vwap;

    private BigDecimal ask;

    private BigDecimal bid;

    private Instant createdAt;

    @JsonProperty("change_24")
    private BigDecimal change24;

    private Object rollingAverageChange;

    // Legacy getters for backwards compatibility
    public BigDecimal getLastAsDecimal() {
      return last;
    }

    public BigDecimal getHighAsDecimal() {
      return high;
    }

    public BigDecimal getLowAsDecimal() {
      return low;
    }

    public BigDecimal getVwapAsDecimal() {
      return vwap;
    }

    public BigDecimal getVolumeAsDecimal() {
      return volume;
    }

    public BigDecimal getBidAsDecimal() {
      return bid;
    }

    public BigDecimal getAskAsDecimal() {
      return ask;
    }

    public BigDecimal getChange24AsDecimal() {
      return change24;
    }
  }

  // Legacy methods for backwards compatibility
  public BigDecimal getLast() {
    return payload != null ? payload.getLastAsDecimal() : null;
  }

  public BigDecimal getHigh() {
    return payload != null ? payload.getHighAsDecimal() : null;
  }

  public BigDecimal getLow() {
    return payload != null ? payload.getLowAsDecimal() : null;
  }

  public BigDecimal getVwap() {
    return payload != null ? payload.getVwapAsDecimal() : null;
  }

  public BigDecimal getVolume() {
    return payload != null ? payload.getVolumeAsDecimal() : null;
  }

  public BigDecimal getBid() {
    return payload != null ? payload.getBidAsDecimal() : null;
  }

  public BigDecimal getAsk() {
    return payload != null ? payload.getAskAsDecimal() : null;
  }
}
