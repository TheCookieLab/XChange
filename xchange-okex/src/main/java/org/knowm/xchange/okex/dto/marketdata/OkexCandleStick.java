package org.knowm.xchange.okex.dto.marketdata;

import org.knowm.xchange.okx.dto.marketdata.OkxCandleStick;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxCandleStick} instead.
 */
@Deprecated
public class OkexCandleStick {

  private final OkxCandleStick delegate;

  public OkexCandleStick(OkxCandleStick delegate) {
    this.delegate = delegate;
  }

  public Long getTimestamp() {
    return delegate.getTimestamp();
  }

  public String getOpenPrice() {
    return delegate.getOpenPrice();
  }

  public String getClosePrice() {
    return delegate.getClosePrice();
  }

  public String getHighPrice() {
    return delegate.getHighPrice();
  }

  public String getLowPrice() {
    return delegate.getLowPrice();
  }

  public String getVolume() {
    return delegate.getVolume();
  }

  public String getVolumeCcy() {
    return delegate.getVolumeCcy();
  }

  public String getVolCcyQuote() {
    return delegate.getVolCcyQuote();
  }

  public String getConfirm() {
    return delegate.getConfirm();
  }
}
