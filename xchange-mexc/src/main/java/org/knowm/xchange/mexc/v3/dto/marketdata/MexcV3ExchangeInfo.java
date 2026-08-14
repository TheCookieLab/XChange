package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Exchange-wide configuration from {@code GET /api/v3/exchangeInfo}.
 *
 * <p>Parsing is strict: a malformed payload fails deserialization instead of producing guessed
 * filters or precisions. Unknown {@code filterType} values are preserved verbatim (see {@link
 * MexcV3SymbolFilter}) and never interpreted.
 */
public class MexcV3ExchangeInfo {

  private final String timezone;
  private final long serverTime;
  private final List<MexcV3RateLimit> rateLimits;
  private final List<Object> exchangeFilters;
  private final List<MexcV3Symbol> symbols;

  public MexcV3ExchangeInfo(
      @JsonProperty("timezone") String timezone,
      @JsonProperty("serverTime") long serverTime,
      @JsonProperty("rateLimits") List<MexcV3RateLimit> rateLimits,
      @JsonProperty("exchangeFilters") List<Object> exchangeFilters,
      @JsonProperty("symbols") List<MexcV3Symbol> symbols) {
    this.timezone = timezone;
    this.serverTime = serverTime;
    this.rateLimits = rateLimits;
    this.exchangeFilters = exchangeFilters;
    this.symbols = symbols;
  }

  public String getTimezone() {
    return timezone;
  }

  public long getServerTime() {
    return serverTime;
  }

  public List<MexcV3RateLimit> getRateLimits() {
    return rateLimits;
  }

  public List<Object> getExchangeFilters() {
    return exchangeFilters;
  }

  public List<MexcV3Symbol> getSymbols() {
    return symbols;
  }
}
