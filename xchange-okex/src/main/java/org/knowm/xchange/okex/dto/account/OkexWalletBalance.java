package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxWalletBalance} instead.
 */
@Deprecated
public class OkexWalletBalance {

  private final OkxWalletBalance delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexWalletBalance() {
    this(new OkxWalletBalance());
  }

  @JsonCreator
  public OkexWalletBalance(OkxWalletBalance delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxWalletBalance to() {
    return delegate;
  }

  public String getAsOfTime() {
    return delegate.getAsOfTime();
  }

  public String getTotalEquity() {
    return delegate.getTotalEquity();
  }

  public String getIsolatedMarginEquity() {
    return delegate.getIsolatedMarginEquity();
  }

  public String getAdjustedEquity() {
    return delegate.getAdjustedEquity();
  }

  public String getMarginFrozen() {
    return delegate.getMarginFrozen();
  }

  public String getInitialMarginRequirement() {
    return delegate.getInitialMarginRequirement();
  }

  public String getMaintenanceMarginRequirement() {
    return delegate.getMaintenanceMarginRequirement();
  }

  public String getMarginRatio() {
    return delegate.getMarginRatio();
  }

  public String getNotionalUsd() {
    return delegate.getNotionalUsd();
  }

  public Detail[] getDetails() {
    OkxWalletBalance.Detail[] details = delegate.getDetails();
    if (details == null) {
      return null;
    }
    Detail[] wrapped = new Detail[details.length];
    for (int i = 0; i < details.length; i++) {
      wrapped[i] = new Detail(details[i]);
    }
    return wrapped;
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxWalletBalance.Detail} instead.
   */
  @Deprecated
  public static class Detail {

    private final OkxWalletBalance.Detail delegate;

    public Detail(OkxWalletBalance.Detail delegate) {
      this.delegate = delegate;
    }

    public String getCurrency() {
      return delegate.getCurrency();
    }

    public String getEquity() {
      return delegate.getEquity();
    }

    public String getCashBalance() {
      return delegate.getCashBalance();
    }

    public String getAsOfTime() {
      return delegate.getAsOfTime();
    }

    public String getIsolatedMarginEquity() {
      return delegate.getIsolatedMarginEquity();
    }

    public String getAvilableEquity() {
      return delegate.getAvilableEquity();
    }

    public String getDiscountEquity() {
      return delegate.getDiscountEquity();
    }

    public String getAvailableBalance() {
      return delegate.getAvailableBalance();
    }

    public String getFrozenBalance() {
      return delegate.getFrozenBalance();
    }

    public String getMarginFrozen() {
      return delegate.getMarginFrozen();
    }

    public String getUsdEqual() {
      return delegate.getUsdEqual();
    }
  }
}
