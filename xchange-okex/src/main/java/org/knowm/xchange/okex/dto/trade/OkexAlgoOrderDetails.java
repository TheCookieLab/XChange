package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderDetails;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxAlgoOrderDetails} instead.
 */
@Deprecated
public class OkexAlgoOrderDetails {

  private final OkxAlgoOrderDetails delegate;

  public OkexAlgoOrderDetails(OkxAlgoOrderDetails delegate) {
    this.delegate = delegate;
  }

  public String getInstrumentType() {
    return delegate.getInstrumentType();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public String getOrderId() {
    return delegate.getOrderId();
  }

  public String getClientOrderId() {
    return delegate.getClientOrderId();
  }

  public String getAlgoClientOrderId() {
    return delegate.getAlgoClientOrderId();
  }

  public String getTag() {
    return delegate.getTag();
  }

  public String getSide() {
    return delegate.getSide();
  }

  public String getPosSide() {
    return delegate.getPosSide();
  }

  public String getOrderType() {
    return delegate.getOrderType();
  }

  public String getAmount() {
    return delegate.getAmount();
  }

  public String getPrice() {
    return delegate.getPrice();
  }

  public String getReducePosition() {
    return delegate.getReducePosition();
  }

  public String getTakeProfitTriggerPrice() {
    return delegate.getTakeProfitTriggerPrice();
  }

  public String getTakeProfitOrderPrice() {
    return delegate.getTakeProfitOrderPrice();
  }

  public String getStopLossTriggerPrice() {
    return delegate.getStopLossTriggerPrice();
  }

  public String getStopLossOrderPrice() {
    return delegate.getStopLossOrderPrice();
  }

  public String getTriggerPrice() {
    return delegate.getTriggerPrice();
  }

  public String getOrderPrice() {
    return delegate.getOrderPrice();
  }

  public String getActualPrice() {
    return delegate.getActualPrice();
  }

  public String getActualSize() {
    return delegate.getActualSize();
  }

  public String getState() {
    return delegate.getState();
  }

  public String getCreationTime() {
    return delegate.getCreationTime();
  }

  public String getUpdateTime() {
    return delegate.getUpdateTime();
  }
}
