package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One filter from a symbol's {@code filters} array in {@code GET /api/v3/exchangeInfo}.
 *
 * <p>Only {@code PERCENT_PRICE_BY_SIDE} is documented by the provider. Unknown filter types are
 * preserved raw (including their parameters) through {@link #getExtra()} — the adapter never
 * interprets a filter it does not recognize, so downstream validation cannot silently guess.
 */
public class MexcV3SymbolFilter {

  private final String filterType;
  private final String bidMultiplierUp;
  private final String askMultiplierDown;
  private final Map<String, Object> extra = new LinkedHashMap<>();

  public MexcV3SymbolFilter(
      @JsonProperty("filterType") String filterType,
      @JsonProperty("bidMultiplierUp") String bidMultiplierUp,
      @JsonProperty("askMultiplierDown") String askMultiplierDown) {
    this.filterType = filterType;
    this.bidMultiplierUp = bidMultiplierUp;
    this.askMultiplierDown = askMultiplierDown;
  }

  public String getFilterType() {
    return filterType;
  }

  /** Buy-side price band (multiplier applied to the last price), or {@code null}. */
  public String getBidMultiplierUp() {
    return bidMultiplierUp;
  }

  /** Sell-side price band (multiplier applied to the last price), or {@code null}. */
  public String getAskMultiplierDown() {
    return askMultiplierDown;
  }

  /** Parameters of this filter not explicitly modeled, preserved verbatim. */
  public Map<String, Object> getExtra() {
    return extra;
  }

  @JsonAnySetter
  public void setExtra(String name, Object value) {
    extra.put(name, value);
  }
}
