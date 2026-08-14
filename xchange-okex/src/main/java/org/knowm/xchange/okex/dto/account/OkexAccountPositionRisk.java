package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk} instead.
 */
@Deprecated
public class OkexAccountPositionRisk {

  private final OkxAccountPositionRisk delegate;

  @JsonCreator
  public OkexAccountPositionRisk(OkxAccountPositionRisk delegate) {
    this.delegate = delegate;
  }

  /**
   * Retained legacy value constructor; builds the canonical DTO internally.
   *
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk} instead.
   */
  @Deprecated
  public OkexAccountPositionRisk(
      @JsonProperty("adjEq") BigDecimal adjustEquity,
      @JsonProperty("balData") List<BalanceData> balanceData,
      @JsonProperty("posData") List<PositionData> positionData,
      @JsonProperty("ts") Date timestamp) {
    this(
        new OkxAccountPositionRisk(
            adjustEquity,
            balanceData == null
                ? null
                : balanceData.stream()
                    .map(
                        b ->
                            new OkxAccountPositionRisk.BalanceData(
                                b.getCurrency(),
                                b.getEquityOfCurrency(),
                                b.getDiscountEquityOfCurrency()))
                    .collect(Collectors.toList()),
            positionData == null
                ? null
                : positionData.stream()
                    .map(
                        p ->
                            new OkxAccountPositionRisk.PositionData(
                                p.getInstrumentId(),
                                p.getPositionSize(),
                                p.getNotionalUsdValue(),
                                p.getMgnMode(),
                                p.getPosSide()))
                    .collect(Collectors.toList()),
            timestamp));
  }

  /** Returns the wrapped canonical DTO. */
  public OkxAccountPositionRisk to() {
    return delegate;
  }

  public BigDecimal getAdjustEquity() {
    return delegate.getAdjustEquity();
  }

  public List<BalanceData> getBalanceData() {
    List<OkxAccountPositionRisk.BalanceData> balanceData = delegate.getBalanceData();
    if (balanceData == null) {
      return null;
    }
    return balanceData.stream().map(BalanceData::new).collect(Collectors.toList());
  }

  public List<PositionData> getPositionData() {
    List<OkxAccountPositionRisk.PositionData> positionData = delegate.getPositionData();
    if (positionData == null) {
      return null;
    }
    return positionData.stream().map(PositionData::new).collect(Collectors.toList());
  }

  public Date getTimestamp() {
    return delegate.getTimestamp();
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.BalanceData}
   *     instead.
   */
  @Deprecated
  public static class BalanceData {

    private final OkxAccountPositionRisk.BalanceData delegate;

    public BalanceData(OkxAccountPositionRisk.BalanceData delegate) {
      this.delegate = delegate;
    }

    /**
     * Retained legacy value constructor; builds the canonical DTO internally.
     *
     * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.BalanceData}
     *     instead.
     */
    @Deprecated
    public BalanceData(
        @JsonProperty("ccy") org.knowm.xchange.currency.Currency currency,
        @JsonProperty("eq") BigDecimal equityOfCurrency,
        @JsonProperty("disEq") BigDecimal discountEquityOfCurrency) {
      this(
          new OkxAccountPositionRisk.BalanceData(
              currency, equityOfCurrency, discountEquityOfCurrency));
    }

    public org.knowm.xchange.currency.Currency getCurrency() {
      return delegate.getCurrency();
    }

    public BigDecimal getEquityOfCurrency() {
      return delegate.getEquityOfCurrency();
    }

    public BigDecimal getDiscountEquityOfCurrency() {
      return delegate.getDiscountEquityOfCurrency();
    }
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.PositionData}
   *     instead.
   */
  @Deprecated
  public static class PositionData {

    private final OkxAccountPositionRisk.PositionData delegate;

    public PositionData(OkxAccountPositionRisk.PositionData delegate) {
      this.delegate = delegate;
    }

    /**
     * Retained legacy value constructor; builds the canonical DTO internally.
     *
     * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.PositionData}
     *     instead.
     */
    @Deprecated
    public PositionData(
        @JsonProperty("instId") String instrumentId,
        @JsonProperty("pos") BigDecimal positionSize,
        @JsonProperty("notionalUsd") BigDecimal notionalUsdValue,
        @JsonProperty("mgnMode") String marginMode,
        @JsonProperty("posSide") String positionSide) {
      this(
          new OkxAccountPositionRisk.PositionData(
              instrumentId, positionSize, notionalUsdValue, marginMode, positionSide));
    }

    public String getInstrumentId() {
      return delegate.getInstrumentId();
    }

    public BigDecimal getPositionSize() {
      return delegate.getPositionSize();
    }

    public BigDecimal getNotionalUsdValue() {
      return delegate.getNotionalUsdValue();
    }

    public String getMgnMode() {
      return delegate.getMgnMode();
    }

    public String getPosSide() {
      return delegate.getPosSide();
    }
  }
}
