package org.knowm.xchange.coinsph.dto.account;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a unified funding record (deposit or withdrawal) from Coins.ph API Used for adapting
 * to XChange's FundingRecord
 */
@Getter
@ToString
public class CoinsphFundingRecord {

  public enum Type {
    DEPOSIT,
    WITHDRAWAL
  }

  private final String id;
  private final Type type;
  private final String currency;
  private final BigDecimal amount;
  private final String address;
  private final String addressTag;
  private final Date timestamp;
  private final String txId;
  private final String description;
  private final int status;
  private final BigDecimal fee;

  // Constructor for deposit records
  public CoinsphFundingRecord(CoinsphDepositRecord depositRecord) {
    this.id = depositRecord.getId();
    this.type = Type.DEPOSIT;
    this.currency = depositRecord.getCoin();
    this.amount = depositRecord.getAmount();
    this.address = depositRecord.getAddress();
    this.addressTag = depositRecord.getAddressTag();
    this.timestamp = new Date(depositRecord.getInsertTime());
    this.txId = depositRecord.getTxId();
    this.description = "Deposit via " + depositRecord.getNetwork();
    this.status = depositRecord.getStatus();
    this.fee = BigDecimal.ZERO; // Deposits typically don't have fees
  }

  // Constructor for withdrawal records
  public CoinsphFundingRecord(CoinsphWithdrawalRecord withdrawalRecord) {
    this.id = withdrawalRecord.getId();
    this.type = Type.WITHDRAWAL;
    this.currency = withdrawalRecord.getCoin();
    this.amount = withdrawalRecord.getAmount();
    this.address = withdrawalRecord.getAddress();
    this.addressTag = withdrawalRecord.getAddressTag();
    this.timestamp = new Date(withdrawalRecord.getApplyTime());
    this.txId = withdrawalRecord.getTxId();
    this.description =
        withdrawalRecord.getInfo() != null && !withdrawalRecord.getInfo().isEmpty()
            ? withdrawalRecord.getInfo()
            : "Withdrawal via " + withdrawalRecord.getNetwork();
    this.status = withdrawalRecord.getStatus();
    this.fee = withdrawalRecord.getTransactionFee();
  }
}
