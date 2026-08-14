package org.knowm.xchange.okex.dto.marketdata;

import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxTicker} instead.
 */
@Deprecated
public class OkexTicker {

  private final OkxTicker delegate;

  public OkexTicker(OkxTicker delegate) {
    this.delegate = delegate;
  }

  public String getInstrumentType() {
    return delegate.getInstrumentType();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public BigDecimal getLast() {
    return delegate.getLast();
  }

  public BigDecimal getLastSize() {
    return delegate.getLastSize();
  }

  public BigDecimal getAskPrice() {
    return delegate.getAskPrice();
  }

  public BigDecimal getAskSize() {
    return delegate.getAskSize();
  }

  public BigDecimal getBidPrice() {
    return delegate.getBidPrice();
  }

  public BigDecimal getBidSize() {
    return delegate.getBidSize();
  }

  public BigDecimal getOpen24h() {
    return delegate.getOpen24h();
  }

  public BigDecimal getHigh24h() {
    return delegate.getHigh24h();
  }

  public BigDecimal getLow24h() {
    return delegate.getLow24h();
  }

  public BigDecimal getVolumeCurrency24h() {
    return delegate.getVolumeCurrency24h();
  }

  public BigDecimal getVolume24h() {
    return delegate.getVolume24h();
  }

  public String getSodUtc0() {
    return delegate.getSodUtc0();
  }

  public String getSodUtc8() {
    return delegate.getSodUtc8();
  }

  public Date getTimestamp() {
    return delegate.getTimestamp();
  }
}
