package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk} instead.
 */
@Deprecated
public class OkexAccountPositionRisk {

  private final OkxAccountPositionRisk delegate;

  @JsonCreator
  public OkexAccountPositionRisk(OkxAccountPositionRisk delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxAccountPositionRisk to() {
    return delegate;
  }

  public BigDecimal getAdjustEquity() {
    return delegate.getAdjustEquity();
  }

  public List<BalanceData> getBalanceData() {
    return delegate.getBalanceData().stream().map(BalanceData::new).collect(Collectors.toList());
  }

  public List<PositionData> getPositionData() {
    return delegate.getPositionData().stream().map(PositionData::new).collect(Collectors.toList());
  }

  public Date getTimestamp() {
    return delegate.getTimestamp();
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.BalanceData}
   *     instead.
   */
  @Deprecated
  public static class BalanceData {

    private final OkxAccountPositionRisk.BalanceData delegate;

    public BalanceData(OkxAccountPositionRisk.BalanceData delegate) {
      this.delegate = delegate;
    }

    public org.knowm.xchange.currency.Currency getCurrency() {
      return delegate.getCurrency();
    }

    public BigDecimal getEquityOfCurrency() {
      return delegate.getEquityOfCurrency();
    }

    public BigDecimal getDiscountEquityOfCurrency() {
      return delegate.getDiscountEquityOfCurrency();
    }
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.PositionData}
   *     instead.
   */
  @Deprecated
  public static class PositionData {

    private final OkxAccountPositionRisk.PositionData delegate;

    public PositionData(OkxAccountPositionRisk.PositionData delegate) {
      this.delegate = delegate;
    }

    public String getInstrumentId() {
      return delegate.getInstrumentId();
    }

    public BigDecimal getPositionSize() {
      return delegate.getPositionSize();
    }

    public BigDecimal getNotionalUsdValue() {
      return delegate.getNotionalUsdValue();
    }

    public String getMgnMode() {
      return delegate.getMgnMode();
    }

    public String getPosSide() {
      return delegate.getPosSide();
    }
  }
}
