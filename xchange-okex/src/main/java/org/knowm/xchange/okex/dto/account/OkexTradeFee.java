package org.knowm.xchange.okex.dto.account;

import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxTradeFee} instead.
 */
@Deprecated
public class OkexTradeFee {

  private final OkxTradeFee delegate;

  public OkexTradeFee(OkxTradeFee delegate) {
    this.delegate = delegate;
  }

  public String getDelivery() {
    return delegate.getDelivery();
  }

  public String getExercise() {
    return delegate.getExercise();
  }

  public String getInstType() {
    return delegate.getInstType();
  }

  public String getLevel() {
    return delegate.getLevel();
  }

  public String getMaker() {
    return delegate.getMaker();
  }

  public String getTaker() {
    return delegate.getTaker();
  }

  public String getMakerU() {
    return delegate.getMakerU();
  }

  public String getTakerU() {
    return delegate.getTakerU();
  }

  public String getMakerUSDC() {
    return delegate.getMakerUSDC();
  }

  public String getTakerUSDC() {
    return delegate.getTakerUSDC();
  }

  public String getTimestamp() {
    return delegate.getTimestamp();
  }

  public String getRuleType() {
    return delegate.getRuleType();
  }

  public List<OkexFiatList> getFiatList() {
    return delegate.getFiatList().stream().map(OkexFiatList::new).collect(Collectors.toList());
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxTradeFee.FiatList} instead.
   */
  @Deprecated
  public static class OkexFiatList {

    private final OkxTradeFee.FiatList delegate;

    public OkexFiatList(OkxTradeFee.FiatList delegate) {
      this.delegate = delegate;
    }

    public String getCcy() {
      return delegate.getCcy();
    }

    public String getTaker() {
      return delegate.getTaker();
    }

    public String getMaker() {
      return delegate.getMaker();
    }
  }
}
