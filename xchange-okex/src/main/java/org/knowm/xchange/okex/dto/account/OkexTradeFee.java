package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxTradeFee} instead.
 */
@Deprecated
public class OkexTradeFee {

  private final OkxTradeFee delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexTradeFee() {
    this(new OkxTradeFee());
  }

  @JsonCreator
  public OkexTradeFee(OkxTradeFee delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxTradeFee to() {
    return delegate;
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

  public List<FiatList> getFiatList() {
    List<OkxTradeFee.FiatList> fiatList = delegate.getFiatList();
    if (fiatList == null) {
      return null;
    }
    return fiatList.stream().map(FiatList::new).collect(Collectors.toList());
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxTradeFee.FiatList} instead.
   */
  @Deprecated
  public static class FiatList {

    private final OkxTradeFee.FiatList delegate;

    /**
     * Public no-argument constructor retained for source and binary compatibility with pre-rename
     * clients (previously Lombok {@code @NoArgsConstructor}).
     */
    public FiatList() {
      this(new OkxTradeFee.FiatList());
    }

    public FiatList(OkxTradeFee.FiatList delegate) {
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
