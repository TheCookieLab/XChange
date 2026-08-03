package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Cancel response: ids successfully canceled, and ids that could not be with reasons. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketCancelResponse(
    @JsonProperty("canceled") List<String> canceled,
    @JsonProperty("not_canceled") Map<String, String> notCanceled) {}
