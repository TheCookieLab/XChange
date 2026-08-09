package org.knowm.xchange.krakenfutures.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.krakenfutures.dto.KrakenFuturesResult;

/**
 * Account activity log (deposits, withdrawals, transfers, fee payments, funding).
 *
 * <p>Entries are returned newest first and are ordered by the provider-generated id; the id is
 * the stable cursor for incremental pulls.
 */
@Getter
@ToString
public class KrakenFuturesAccountLog extends KrakenFuturesResult {

  private final Date serverTime;
  private final List<KrakenFuturesAccountLogEntry> accountLog;

  public KrakenFuturesAccountLog(
      @JsonProperty("result") String result,
      @JsonProperty("serverTime") Date serverTime,
      @JsonProperty("error") String error,
      @JsonProperty("accountLog") List<KrakenFuturesAccountLogEntry> accountLog) {

    super(result, error);
    this.serverTime = serverTime;
    this.accountLog = accountLog;
  }

  /** One account activity entry. */
  @Getter
  @ToString
  public static class KrakenFuturesAccountLogEntry {

    private final String id;
    private final Date time;
    private final String type;
    private final BigDecimal amount;
    private final String account;
    private final String instrument;
    private final String wallet;
    private final BigDecimal balance;
    private final BigDecimal change;

    public KrakenFuturesAccountLogEntry(
        @JsonProperty("id") String id,
        @JsonProperty("time") Date time,
        @JsonProperty("type") String type,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("account") String account,
        @JsonProperty("instrument") String instrument,
        @JsonProperty("wallet") String wallet,
        @JsonProperty("balance") BigDecimal balance,
        @JsonProperty("change") BigDecimal change) {

      this.id = id;
      this.time = time;
      this.type = type;
      this.amount = amount;
      this.account = account;
      this.instrument = instrument;
      this.wallet = wallet;
      this.balance = balance;
      this.change = change;
    }
  }
}
