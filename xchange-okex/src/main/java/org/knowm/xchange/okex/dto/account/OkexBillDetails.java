package org.knowm.xchange.okex.dto.account;

import org.knowm.xchange.okx.dto.account.OkxBillDetails;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxBillDetails} instead.
 */
@Deprecated
public class OkexBillDetails {

  private final OkxBillDetails delegate;

  public OkexBillDetails(OkxBillDetails delegate) {
    this.delegate = delegate;
  }

  public String getInstType() {
    return delegate.getInstType();
  }

  public String getBillId() {
    return delegate.getBillId();
  }

  public String getBillType() {
    return delegate.getBillType();
  }

  public String getBillSubType() {
    return delegate.getBillSubType();
  }

  public String getTimestamp() {
    return delegate.getTimestamp();
  }

  public String getAccountBalanceChange() {
    return delegate.getAccountBalanceChange();
  }

  public String getPositionBalanceChange() {
    return delegate.getPositionBalanceChange();
  }

  public String getAccountBalance() {
    return delegate.getAccountBalance();
  }

  public String getPositionBalance() {
    return delegate.getPositionBalance();
  }

  public String getQuantity() {
    return delegate.getQuantity();
  }

  public String getCurrency() {
    return delegate.getCurrency();
  }

  public String getPnl() {
    return delegate.getPnl();
  }

  public String getFee() {
    return delegate.getFee();
  }

  public String getMarginMode() {
    return delegate.getMarginMode();
  }

  public String getInstId() {
    return delegate.getInstId();
  }

  public String getOrderId() {
    return delegate.getOrderId();
  }

  public String getExecType() {
    return delegate.getExecType();
  }

  public String getFromAccount() {
    return delegate.getFromAccount();
  }

  public String getToAccount() {
    return delegate.getToAccount();
  }

  public String getNotes() {
    return delegate.getNotes();
  }
}
