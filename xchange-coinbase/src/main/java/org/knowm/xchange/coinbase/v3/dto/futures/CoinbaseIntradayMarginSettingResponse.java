package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response for getting or setting intraday margin setting.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getintradaymarginsetting">Get Intraday Margin Setting</a>
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_setintradaymarginsetting">Set Intraday Margin Setting</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseIntradayMarginSettingResponse {

  private final String setting;

  @JsonCreator
  public CoinbaseIntradayMarginSettingResponse(@JsonProperty("setting") String setting) {
    this.setting = setting;
  }

  @Override
  public String toString() {
    return "CoinbaseIntradayMarginSettingResponse [setting=" + setting + "]";
  }
}

