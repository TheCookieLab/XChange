package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Request payload for setting intraday margin preferences.
 *
 * @see <a href="https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/futures/set-intraday-margin-settings.md">Set Intraday Margin Setting</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseIntradayMarginSettingRequest {

  private final CoinbaseIntradayMarginSetting setting;

  @JsonCreator
  public CoinbaseIntradayMarginSettingRequest(
      @JsonProperty("setting") CoinbaseIntradayMarginSetting setting) {
    this.setting = setting;
  }

  @Override
  public String toString() {
    return "CoinbaseIntradayMarginSettingRequest [setting=" + setting + "]";
  }
}
