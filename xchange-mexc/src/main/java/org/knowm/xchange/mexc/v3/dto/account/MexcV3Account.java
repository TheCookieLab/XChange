package org.knowm.xchange.mexc.v3.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Account snapshot from {@code GET /api/v3/account}. */
public class MexcV3Account {

  private final boolean canTrade;
  private final boolean canWithdraw;
  private final boolean canDeposit;
  private final Long updateTime;
  private final String accountType;
  private final List<MexcV3Balance> balances;
  private final List<String> permissions;

  public MexcV3Account(
      @JsonProperty("canTrade") boolean canTrade,
      @JsonProperty("canWithdraw") boolean canWithdraw,
      @JsonProperty("canDeposit") boolean canDeposit,
      @JsonProperty("updateTime") Long updateTime,
      @JsonProperty("accountType") String accountType,
      @JsonProperty("balances") List<MexcV3Balance> balances,
      @JsonProperty("permissions") List<String> permissions) {
    this.canTrade = canTrade;
    this.canWithdraw = canWithdraw;
    this.canDeposit = canDeposit;
    this.updateTime = updateTime;
    this.accountType = accountType;
    this.balances = balances;
    this.permissions = permissions;
  }

  public boolean isCanTrade() {
    return canTrade;
  }

  public boolean isCanWithdraw() {
    return canWithdraw;
  }

  public boolean isCanDeposit() {
    return canDeposit;
  }

  /** Last account update time, or {@code null} per provider responses. */
  public Long getUpdateTime() {
    return updateTime;
  }

  public String getAccountType() {
    return accountType;
  }

  public List<MexcV3Balance> getBalances() {
    return balances;
  }

  public List<String> getPermissions() {
    return permissions;
  }
}
