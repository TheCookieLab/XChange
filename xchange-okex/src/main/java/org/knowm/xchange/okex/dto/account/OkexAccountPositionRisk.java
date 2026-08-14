package org.knowm.xchange.okex.dto.account;

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

  public OkexAccountPositionRisk(OkxAccountPositionRisk delegate) {
    this.delegate = delegate;
  }

  public BigDecimal getAdjustEquity() {
    return delegate.getAdjustEquity();
  }

  public List<OkexBalanceData> getBalanceData() {
    return delegate.getBalanceData().stream()
        .map(OkexBalanceData::new)
        .collect(Collectors.toList());
  }

  public List<OkexPositionData> getPositionData() {
    return delegate.getPositionData().stream()
        .map(OkexPositionData::new)
        .collect(Collectors.toList());
  }

  public Date getTimestamp() {
    return delegate.getTimestamp();
  }

  /**
   * @deprecated use {@link
   *     org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.BalanceData} instead.
   */
  @Deprecated
  public static class OkexBalanceData {

    private final OkxAccountPositionRisk.BalanceData delegate;

    public OkexBalanceData(OkxAccountPositionRisk.BalanceData delegate) {
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
   * @deprecated use {@link
   *     org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.PositionData} instead.
   */
  @Deprecated
  public static class OkexPositionData {

    private final OkxAccountPositionRisk.PositionData delegate;

    public OkexPositionData(OkxAccountPositionRisk.PositionData delegate) {
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
