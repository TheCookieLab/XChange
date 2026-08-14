package org.knowm.xchange.okex.service.params;

import org.knowm.xchange.okx.service.params.OkxTickerParams;
import org.knowm.xchange.service.marketdata.params.Params;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.params.OkxTickerParams} instead.
 */
@Deprecated
public class OkexTickerParams implements Params {

  private final OkxTickerParams delegate;

  public OkexTickerParams() {
    this.delegate = new OkxTickerParams();
  }

  public String getInstType() {
    return delegate.getInstType();
  }

  public void setInstType(String instType) {
    delegate.setInstType(instType);
  }

  public String getUly() {
    return delegate.getUly();
  }

  public void setUly(String uly) {
    delegate.setUly(uly);
  }

  public String getInstFamily() {
    return delegate.getInstFamily();
  }

  public void setInstFamily(String instFamily) {
    delegate.setInstFamily(instFamily);
  }
}
