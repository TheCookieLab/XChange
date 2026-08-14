package org.knowm.xchange.okex.dto.account;

import java.math.BigDecimal;
import org.knowm.xchange.okx.dto.account.OkxPosition;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxPosition} instead.
 */
@Deprecated
public class OkexPosition {

  private final OkxPosition delegate;

  public OkexPosition(OkxPosition delegate) {
    this.delegate = delegate;
  }

  public String getInstrumentType() {
    return delegate.getInstrumentType();
  }

  public String getMarginMode() {
    return delegate.getMarginMode();
  }

  public String getPositionId() {
    return delegate.getPositionId();
  }

  public String getPositionSide() {
    return delegate.getPositionSide();
  }

  public BigDecimal getPosition() {
    return delegate.getPosition();
  }

  public String getBaseCurrencyBalance() {
    return delegate.getBaseCurrencyBalance();
  }

  public String getQuoteCurrencyBalance() {
    return delegate.getQuoteCurrencyBalance();
  }

  public String getPositionCurrency() {
    return delegate.getPositionCurrency();
  }

  public String getAvailablePosition() {
    return delegate.getAvailablePosition();
  }

  public BigDecimal getAverageOpenPrice() {
    return delegate.getAverageOpenPrice();
  }

  public BigDecimal getMarkPrice() {
    return delegate.getMarkPrice();
  }

  public BigDecimal getUnrealizedPnL() {
    return delegate.getUnrealizedPnL();
  }

  public String getUnrealizedPnLRatio() {
    return delegate.getUnrealizedPnLRatio();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public String getLeverage() {
    return delegate.getLeverage();
  }

  public BigDecimal getLiquidationPrice() {
    return delegate.getLiquidationPrice();
  }

  public String getInitialMarginRequirement() {
    return delegate.getInitialMarginRequirement();
  }

  public String getMargin() {
    return delegate.getMargin();
  }

  public String getMarginRatio() {
    return delegate.getMarginRatio();
  }

  public String getMaintenanceMarginRatio() {
    return delegate.getMaintenanceMarginRatio();
  }

  public String getLiabilities() {
    return delegate.getLiabilities();
  }

  public String getLiabilitiesCurrency() {
    return delegate.getLiabilitiesCurrency();
  }

  public String getInterest() {
    return delegate.getInterest();
  }

  public String getTradeId() {
    return delegate.getTradeId();
  }

  public String getOptionValue() {
    return delegate.getOptionValue();
  }

  public String getNotionalUsd() {
    return delegate.getNotionalUsd();
  }

  public String getAdl() {
    return delegate.getAdl();
  }

  public String getCurrency() {
    return delegate.getCurrency();
  }

  public BigDecimal getLastPrice() {
    return delegate.getLastPrice();
  }

  public String getCreationTime() {
    return delegate.getCreationTime();
  }

  public String getUpdateTime() {
    return delegate.getUpdateTime();
  }
}
