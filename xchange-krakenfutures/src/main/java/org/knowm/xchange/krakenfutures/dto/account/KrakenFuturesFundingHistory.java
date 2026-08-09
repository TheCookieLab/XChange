package org.knowm.xchange.krakenfutures.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.krakenfutures.dto.KrakenFuturesResult;

/**
 * Per-instrument funding payments applied to the account.
 *
 * <p>Querying with a recent {@code lastFundingTime} limits the response to funding payments that
 * occurred after that time, which makes incremental pulls cheap.
 */
@Getter
@ToString
public class KrakenFuturesFundingHistory extends KrakenFuturesResult {

  private final Date serverTime;
  private final List<KrakenFuturesFundingPayment> history;

  public KrakenFuturesFundingHistory(
      @JsonProperty("result") String result,
      @JsonProperty("serverTime") Date serverTime,
      @JsonProperty("error") String error,
      @JsonProperty("history") List<KrakenFuturesFundingPayment> history) {

    super(result, error);
    this.serverTime = serverTime;
    this.history = history;
  }

  /** One funding payment. */
  @Getter
  @ToString
  public static class KrakenFuturesFundingPayment {

    private final String instrument;
    private final Date time;
    private final BigDecimal fundingRate;
    private final BigDecimal markPrice;
    private final BigDecimal fundingAmount;
    private final BigDecimal fundingPrice;

    public KrakenFuturesFundingPayment(
        @JsonProperty("instrument") String instrument,
        @JsonProperty("time") Date time,
        @JsonProperty("fundingRate") BigDecimal fundingRate,
        @JsonProperty("markPrice") BigDecimal markPrice,
        @JsonProperty("fundingAmount") BigDecimal fundingAmount,
        @JsonProperty("fundingPrice") BigDecimal fundingPrice) {

      this.instrument = instrument;
      this.time = time;
      this.fundingRate = fundingRate;
      this.markPrice = markPrice;
      this.fundingAmount = fundingAmount;
      this.fundingPrice = fundingPrice;
    }
  }
}
