package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Wrapper for the order configuration payload.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseOrderConfiguration {

  private final CoinbaseMarketMarketIoc marketMarketIoc;
  private final CoinbaseMarketMarketFok marketMarketFok;
  private final CoinbaseSorLimitIoc sorLimitIoc;
  private final CoinbaseLimitLimitGtc limitLimitGtc;
  private final CoinbaseLimitLimitGtd limitLimitGtd;
  private final CoinbaseLimitLimitFok limitLimitFok;
  private final CoinbaseTwapLimitGtd twapLimitGtd;
  private final CoinbaseStopLimitStopLimitGtc stopLimitStopLimitGtc;
  private final CoinbaseStopLimitStopLimitGtd stopLimitStopLimitGtd;
  private final CoinbaseTriggerBracketGtc triggerBracketGtc;
  private final CoinbaseTriggerBracketGtd triggerBracketGtd;
  private final CoinbaseScaledLimitGtc scaledLimitGtc;

  @JsonCreator
  public CoinbaseOrderConfiguration(
      @JsonProperty("market_market_ioc") CoinbaseMarketMarketIoc marketMarketIoc,
      @JsonProperty("market_market_fok") CoinbaseMarketMarketFok marketMarketFok,
      @JsonProperty("sor_limit_ioc") CoinbaseSorLimitIoc sorLimitIoc,
      @JsonProperty("limit_limit_gtc") CoinbaseLimitLimitGtc limitLimitGtc,
      @JsonProperty("limit_limit_gtd") CoinbaseLimitLimitGtd limitLimitGtd,
      @JsonProperty("limit_limit_fok") CoinbaseLimitLimitFok limitLimitFok,
      @JsonProperty("twap_limit_gtd") CoinbaseTwapLimitGtd twapLimitGtd,
      @JsonProperty("stop_limit_stop_limit_gtc") CoinbaseStopLimitStopLimitGtc stopLimitStopLimitGtc,
      @JsonProperty("stop_limit_stop_limit_gtd") CoinbaseStopLimitStopLimitGtd stopLimitStopLimitGtd,
      @JsonProperty("trigger_bracket_gtc") CoinbaseTriggerBracketGtc triggerBracketGtc,
      @JsonProperty("trigger_bracket_gtd") CoinbaseTriggerBracketGtd triggerBracketGtd,
      @JsonProperty("scaled_limit_gtc") CoinbaseScaledLimitGtc scaledLimitGtc) {
    this.marketMarketIoc = marketMarketIoc;
    this.marketMarketFok = marketMarketFok;
    this.sorLimitIoc = sorLimitIoc;
    this.limitLimitGtc = limitLimitGtc;
    this.limitLimitGtd = limitLimitGtd;
    this.limitLimitFok = limitLimitFok;
    this.twapLimitGtd = twapLimitGtd;
    this.stopLimitStopLimitGtc = stopLimitStopLimitGtc;
    this.stopLimitStopLimitGtd = stopLimitStopLimitGtd;
    this.triggerBracketGtc = triggerBracketGtc;
    this.triggerBracketGtd = triggerBracketGtd;
    this.scaledLimitGtc = scaledLimitGtc;
  }

  public static CoinbaseOrderConfiguration marketMarketIoc(CoinbaseMarketMarketIoc config) {
    return new CoinbaseOrderConfiguration(config, null, null, null, null, null, null, null, null, null,
        null, null);
  }

  public static CoinbaseOrderConfiguration limitLimitGtc(CoinbaseLimitLimitGtc config) {
    return new CoinbaseOrderConfiguration(null, null, null, config, null, null, null, null, null, null,
        null, null);
  }

  public static CoinbaseOrderConfiguration stopLimitStopLimitGtc(
      CoinbaseStopLimitStopLimitGtc config) {
    return new CoinbaseOrderConfiguration(null, null, null, null, null, null, null, config, null, null,
        null, null);
  }

  @Override
  public String toString() {
    return "CoinbaseOrderConfiguration [marketMarketIoc=" + marketMarketIoc
        + ", marketMarketFok=" + marketMarketFok
        + ", sorLimitIoc=" + sorLimitIoc
        + ", limitLimitGtc=" + limitLimitGtc
        + ", limitLimitGtd=" + limitLimitGtd
        + ", limitLimitFok=" + limitLimitFok
        + ", twapLimitGtd=" + twapLimitGtd
        + ", stopLimitStopLimitGtc=" + stopLimitStopLimitGtc
        + ", stopLimitStopLimitGtd=" + stopLimitStopLimitGtd
        + ", triggerBracketGtc=" + triggerBracketGtc
        + ", triggerBracketGtd=" + triggerBracketGtd
        + ", scaledLimitGtc=" + scaledLimitGtc + "]";
  }
}
