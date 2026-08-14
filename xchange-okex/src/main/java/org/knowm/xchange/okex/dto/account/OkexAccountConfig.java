package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxAccountConfig;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountConfig} instead.
 */
@Deprecated
public class OkexAccountConfig {

  private final OkxAccountConfig delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexAccountConfig() {
    this(new OkxAccountConfig());
  }

  @JsonCreator
  public OkexAccountConfig(OkxAccountConfig delegate) {
    this.delegate = delegate;
  }

  public String getUid() {
    return delegate.getUid();
  }

  public String getAccountLevel() {
    return delegate.getAccountLevel();
  }

  public String getPositionMode() {
    return delegate.getPositionMode();
  }

  public Boolean getAutoLoan() {
    return delegate.getAutoLoan();
  }

  public String getGreeksType() {
    return delegate.getGreeksType();
  }

  public String getLevel() {
    return delegate.getLevel();
  }

  public String getLevelTmp() {
    return delegate.getLevelTmp();
  }
}
