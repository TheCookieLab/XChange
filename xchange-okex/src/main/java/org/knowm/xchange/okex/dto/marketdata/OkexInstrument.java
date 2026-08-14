package org.knowm.xchange.okex.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxInstrument} instead.
 */
@Deprecated
public class OkexInstrument {

  private final OkxInstrument delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexInstrument() {
    this(new OkxInstrument());
  }

  @JsonCreator
  public OkexInstrument(OkxInstrument delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxInstrument to() {
    return delegate;
  }

  public String getInstrumentType() {
    return delegate.getInstrumentType();
  }

  public String getInstrumentId() {
    return delegate.getInstrumentId();
  }

  public String getUnderlying() {
    return delegate.getUnderlying();
  }

  public String getCategory() {
    return delegate.getCategory();
  }

  public String getBaseCurrency() {
    return delegate.getBaseCurrency();
  }

  public String getQuoteCurrency() {
    return delegate.getQuoteCurrency();
  }

  public String getSettleCurrency() {
    return delegate.getSettleCurrency();
  }

  public String getContractValue() {
    return delegate.getContractValue();
  }

  public String getContractMultiplier() {
    return delegate.getContractMultiplier();
  }

  public String getOptionType() {
    return delegate.getOptionType();
  }

  public String getStrikePrice() {
    return delegate.getStrikePrice();
  }

  public String getListTime() {
    return delegate.getListTime();
  }

  public String getExpiryTime() {
    return delegate.getExpiryTime();
  }

  public String getLeverage() {
    return delegate.getLeverage();
  }

  public String getTickSize() {
    return delegate.getTickSize();
  }

  public String getLotSize() {
    return delegate.getLotSize();
  }

  public String getMinSize() {
    return delegate.getMinSize();
  }

  public String getContractType() {
    return delegate.getContractType();
  }

  public String getAlias() {
    return delegate.getAlias();
  }

  public String getState() {
    return delegate.getState();
  }

  public String getInstIdCode() {
    return delegate.getInstIdCode();
  }
}
