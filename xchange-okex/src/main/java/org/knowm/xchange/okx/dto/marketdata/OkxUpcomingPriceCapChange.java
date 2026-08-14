package org.knowm.xchange.okx.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single upcoming price-cap parameter change for an instrument, as reported by the {@code upcChg}
 * field of <a href="https://www.okx.com/docs-v5/en/#rest-api-public-data-get-instruments">GET
 * /api/v5/public/instruments</a>. The change takes effect at {@code effTime}; until then the
 * current instrument value remains valid.
 */
@Getter
@NoArgsConstructor
public class OkxUpcomingPriceCapChange {

  /** Effective time of the change, in Unix milliseconds. */
  @JsonProperty("effTime")
  private String effectiveTime;

  /** The new value the capped parameter will take once the change is effective. */
  @JsonProperty("newValue")
  private String newValue;

  /** The capped parameter being changed, e.g. {@code minSz} or {@code tickSz}. */
  @JsonProperty("param")
  private String parameter;
}
