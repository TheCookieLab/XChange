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

    private String volume;

    private String high;

    private String last;

    private String low;

    private String vwap;

    private String ask;

    private String bid;

    private Instant createdAt;

    @JsonProperty("change_24")
    private String change24;

    private Object rollingAverageChange;

    // Legacy getters for backwards compatibility
    public BigDecimal getLastAsDecimal() {
      return last != null ? new BigDecimal(last) : null;
    }

    public BigDecimal getHighAsDecimal() {
      return high != null ? new BigDecimal(high) : null;
    }

    public BigDecimal getLowAsDecimal() {
      return low != null ? new BigDecimal(low) : null;
    }

    public BigDecimal getVwapAsDecimal() {
      return vwap != null ? new BigDecimal(vwap) : null;
    }

    public BigDecimal getVolumeAsDecimal() {
      return volume != null ? new BigDecimal(volume) : null;
    }

    public BigDecimal getBidAsDecimal() {
      return bid != null ? new BigDecimal(bid) : null;
    }

    public BigDecimal getAskAsDecimal() {
      return ask != null ? new BigDecimal(ask) : null;
    }

    public BigDecimal getChange24AsDecimal() {
      return change24 != null ? new BigDecimal(change24) : null;
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
