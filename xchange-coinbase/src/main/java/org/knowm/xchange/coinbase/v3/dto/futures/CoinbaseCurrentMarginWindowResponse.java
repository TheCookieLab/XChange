package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response containing the current CFM margin window.
 *
 * <p>The wire-level {@code margin_window} object is flattened into the legacy string accessors so
 * callers retain {@code getMarginWindow()} and {@code getMarginWindowType()}. {@code getEndTime()}
 * exposes the object's RFC 3339 end time.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/futures/get-current-margin-window">Get Current Margin Window</a>
 * @since 1.0.2
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseCurrentMarginWindowResponse {

  private final String marginWindow;
  private final String marginWindowType;
  private final String endTime;
  private final Boolean isIntradayMarginEnrollmentKillswitchEnabled;
  private final Boolean isIntradayMarginKillswitchEnabled;

  /**
   * Creates a current CFM margin-window response from its wire-level nested object.
   *
   * @param marginWindow current margin-window type and end time
   * @param isIntradayMarginEnrollmentKillswitchEnabled enrollment killswitch state
   * @param isIntradayMarginKillswitchEnabled intraday-margin killswitch state
   */
  @JsonCreator
  public CoinbaseCurrentMarginWindowResponse(
      @JsonProperty("margin_window") MarginWindow marginWindow,
      @JsonProperty("is_intraday_margin_enrollment_killswitch_enabled")
          Boolean isIntradayMarginEnrollmentKillswitchEnabled,
      @JsonProperty("is_intraday_margin_killswitch_enabled")
          Boolean isIntradayMarginKillswitchEnabled) {
    this.marginWindow = marginWindow == null ? null : marginWindow.marginWindowType;
    this.marginWindowType = this.marginWindow;
    this.endTime = marginWindow == null ? null : marginWindow.endTime;
    this.isIntradayMarginEnrollmentKillswitchEnabled =
        isIntradayMarginEnrollmentKillswitchEnabled;
    this.isIntradayMarginKillswitchEnabled = isIntradayMarginKillswitchEnabled;
  }

  /**
   * Preserves the pre-1.0.2 construction contract for callers that supplied flattened values.
   *
   * @deprecated Coinbase now returns a nested {@code margin_window} object
   */
  @Deprecated
  public CoinbaseCurrentMarginWindowResponse(
      String marginWindow,
      String marginWindowType,
      Boolean isIntradayMarginEnrollmentKillswitchEnabled,
      Boolean isIntradayMarginKillswitchEnabled) {
    this.marginWindow = marginWindow;
    this.marginWindowType = marginWindowType;
    this.endTime = null;
    this.isIntradayMarginEnrollmentKillswitchEnabled =
        isIntradayMarginEnrollmentKillswitchEnabled;
    this.isIntradayMarginKillswitchEnabled = isIntradayMarginKillswitchEnabled;
  }

  @Override
  public String toString() {
    return "CoinbaseCurrentMarginWindowResponse [marginWindow="
        + marginWindow
        + ", marginWindowType="
        + marginWindowType
        + "]";
  }

  /**
   * Wire representation of Coinbase's nested current-margin window.
   *
   * @since 1.0.2
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MarginWindow {
    private final String marginWindowType;
    private final String endTime;

    /**
     * Creates a current margin window.
     *
     * @param marginWindowType exchange window classification
     * @param endTime RFC 3339 end time
     */
    @JsonCreator
    public MarginWindow(
        @JsonProperty("margin_window_type") String marginWindowType,
        @JsonProperty("end_time") String endTime) {
      this.marginWindowType = marginWindowType;
      this.endTime = endTime;
    }
  }
}

