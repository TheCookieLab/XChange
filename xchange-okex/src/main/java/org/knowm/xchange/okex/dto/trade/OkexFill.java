package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxFill;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxFill} instead.
 */
@Deprecated
public class OkexFill {

  private final OkxFill delegate;

  public OkexFill(OkxFill delegate) {
    this.delegate = delegate;
  }

  public String getInstrumentType() {
    return delegate.getInstrumentType();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public String getTradeId() {
    return delegate.getTradeId();
  }

  public String getOrderId() {
    return delegate.getOrderId();
  }

  public String getClientOrderId() {
    return delegate.getClientOrderId();
  }

  public String getBillId() {
    return delegate.getBillId();
  }

  public String getTag() {
    return delegate.getTag();
  }

  public String getPrice() {
    return delegate.getPrice();
  }

  public String getAmount() {
    return delegate.getAmount();
  }

  public String getSide() {
    return delegate.getSide();
  }

  public String getPosSide() {
    return delegate.getPosSide();
  }

  public String getFee() {
    return delegate.getFee();
  }

  public String getFeeCurrency() {
    return delegate.getFeeCurrency();
  }

  public String getRebate() {
    return delegate.getRebate();
  }

  public String getRebateCurrency() {
    return delegate.getRebateCurrency();
  }

  public String getFillPrice() {
    return delegate.getFillPrice();
  }

  public String getFillSize() {
    return delegate.getFillSize();
  }

  public String getFillTime() {
    return delegate.getFillTime();
  }

  public String getExecutionType() {
    return delegate.getExecutionType();
  }

  public String getTimestamp() {
    return delegate.getTimestamp();
  }
}
