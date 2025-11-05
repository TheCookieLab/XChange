package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response containing the current margin window for futures trading.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getcurrentmarginwindow">Get Current Margin Window</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseCurrentMarginWindowResponse {

  private final String marginWindow;
  private final String marginWindowType;
  private final Boolean isIntradayMarginEnrollmentKillswitchEnabled;
  private final Boolean isIntradayMarginKillswitchEnabled;

  @JsonCreator
  public CoinbaseCurrentMarginWindowResponse(
      @JsonProperty("margin_window") String marginWindow,
      @JsonProperty("margin_window_type") String marginWindowType,
      @JsonProperty("is_intraday_margin_enrollment_killswitch_enabled") Boolean isIntradayMarginEnrollmentKillswitchEnabled,
      @JsonProperty("is_intraday_margin_killswitch_enabled") Boolean isIntradayMarginKillswitchEnabled) {
    this.marginWindow = marginWindow;
    this.marginWindowType = marginWindowType;
    this.isIntradayMarginEnrollmentKillswitchEnabled = isIntradayMarginEnrollmentKillswitchEnabled;
    this.isIntradayMarginKillswitchEnabled = isIntradayMarginKillswitchEnabled;
  }

  @Override
  public String toString() {
    return "CoinbaseCurrentMarginWindowResponse [marginWindow=" + marginWindow + ", marginWindowType=" + marginWindowType + "]";
  }
}

