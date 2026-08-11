package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * UTA account ledger entry; the ledger response is a bare array of these records.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/get-account-ledger">Get Account Ledger</a>
 */
@Data
public class UtaLedgerEntry {

  @JsonProperty("accountType")
  private String accountType;

  @JsonProperty("id")
  private String id;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("direction")
  private String direction;

  @JsonProperty("businessType")
  private String businessType;

  @JsonProperty("amount")
  private BigDecimal amount;

  @JsonProperty("balance")
  private BigDecimal balance;

  @JsonProperty("fee")
  private BigDecimal fee;

  @JsonProperty("tax")
  private BigDecimal tax;

  /** Business-related info (e.g. order id) as a JSON string. */
  @JsonProperty("remark")
  private String remark;

  /** Nanoseconds. */
  @JsonProperty("ts")
  private Long ts;
}
