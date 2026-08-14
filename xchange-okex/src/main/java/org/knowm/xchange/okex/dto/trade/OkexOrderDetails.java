package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxOrderDetails} instead.
 */
@Deprecated
public class OkexOrderDetails {

  private final OkxOrderDetails delegate;

  public OkexOrderDetails(OkxOrderDetails delegate) {
    this.delegate = delegate;
  }

  public String getInstrumentType() {
    return delegate.getInstrumentType();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public String getTradeMode() {
    return delegate.getTradeMode();
  }

  public String getMarginCurrency() {
    return delegate.getMarginCurrency();
  }

  public String getOrderId() {
    return delegate.getOrderId();
  }

  public String getClientOrderId() {
    return delegate.getClientOrderId();
  }

  public String getTag() {
    return delegate.getTag();
  }

  public String getSide() {
    return delegate.getSide();
  }

  public String getPnl() {
    return delegate.getPnl();
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

  public String getAccumulatedFill() {
    return delegate.getAccumulatedFill();
  }

  public String getLastFilledPrice() {
    return delegate.getLastFilledPrice();
  }

  public String getLastTradeId() {
    return delegate.getLastTradeId();
  }

  public String getLastFilledQuantity() {
    return delegate.getLastFilledQuantity();
  }

  public String getLastFilledTime() {
    return delegate.getLastFilledTime();
  }

  public String getAverageFilledPrice() {
    return delegate.getAverageFilledPrice();
  }

  public String getLastPrice() {
    return delegate.getLastPrice();
  }

  public String getState() {
    return delegate.getState();
  }

  public String getLeverage() {
    return delegate.getLeverage();
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

  public String getFeeCurrency() {
    return delegate.getFeeCurrency();
  }

  public String getFee() {
    return delegate.getFee();
  }

  public String getRebateCcy() {
    return delegate.getRebateCcy();
  }

  public String getRebateAmount() {
    return delegate.getRebateAmount();
  }

  public String getCategory() {
    return delegate.getCategory();
  }

  public String getUpdateTime() {
    return delegate.getUpdateTime();
  }

  public String getCreationTime() {
    return delegate.getCreationTime();
  }
}
