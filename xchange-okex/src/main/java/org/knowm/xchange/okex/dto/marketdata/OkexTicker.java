package org.knowm.xchange.okex.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxTicker} instead.
 */
@Deprecated
public class OkexTicker {

  @JsonProperty("instType")
  private String instrumentType;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("last")
  private BigDecimal last;

  @JsonProperty("lastSz")
  private BigDecimal lastSize;

  @JsonProperty("askPx")
  private BigDecimal askPrice;

  @JsonProperty("askSz")
  private BigDecimal askSize;

  @JsonProperty("bidPx")
  private BigDecimal bidPrice;

  @JsonProperty("bidSz")
  private BigDecimal bidSize;

  @JsonProperty("open24h")
  private BigDecimal open24h;

  @JsonProperty("high24h")
  private BigDecimal high24h;

  @JsonProperty("low24h")
  private BigDecimal low24h;

  /** 24h trading volume, with a unit of currency. */
  @JsonProperty("volCcy24h")
  private BigDecimal volumeCurrency24h;

  /** 24h trading volume, with a unit of contract. */
  @JsonProperty("vol24h")
  private BigDecimal volume24h;

  @JsonProperty("sodUtc0")
  private String sodUtc0;

  @JsonProperty("sodUtc8")
  private String sodUtc8;

  @JsonProperty("ts")
  private Date timestamp;

  /** Legacy no-argument construction path for compiled clients and Jackson deserialization. */
  public OkexTicker() {}

  /** Compatibility constructor wrapping the canonical DTO. */
  public OkexTicker(OkxTicker delegate) {
    this.instrumentType = delegate.getInstrumentType();
    this.instrumentId = delegate.getInstrumentId();
    this.last = delegate.getLast();
    this.lastSize = delegate.getLastSize();
    this.askPrice = delegate.getAskPrice();
    this.askSize = delegate.getAskSize();
    this.bidPrice = delegate.getBidPrice();
    this.bidSize = delegate.getBidSize();
    this.open24h = delegate.getOpen24h();
    this.high24h = delegate.getHigh24h();
    this.low24h = delegate.getLow24h();
    this.volumeCurrency24h = delegate.getVolumeCurrency24h();
    this.volume24h = delegate.getVolume24h();
    this.sodUtc0 = delegate.getSodUtc0();
    this.sodUtc8 = delegate.getSodUtc8();
    this.timestamp = delegate.getTimestamp();
  }

  public String getInstrumentType() {
    return instrumentType;
  }

  public String getInstrumentId() {
    return instrumentId;
  }

  public BigDecimal getLast() {
    return last;
  }

  public BigDecimal getLastSize() {
    return lastSize;
  }

  public BigDecimal getAskPrice() {
    return askPrice;
  }

  public BigDecimal getAskSize() {
    return askSize;
  }

  public BigDecimal getBidPrice() {
    return bidPrice;
  }

  public BigDecimal getBidSize() {
    return bidSize;
  }

  public BigDecimal getOpen24h() {
    return open24h;
  }

  public BigDecimal getHigh24h() {
    return high24h;
  }

  public BigDecimal getLow24h() {
    return low24h;
  }

  public BigDecimal getVolumeCurrency24h() {
    return volumeCurrency24h;
  }

  public BigDecimal getVolume24h() {
    return volume24h;
  }

  public String getSodUtc0() {
    return sodUtc0;
  }

  public String getSodUtc8() {
    return sodUtc8;
  }

  public Date getTimestamp() {
    return timestamp;
  }
}
