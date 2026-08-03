package org.knowm.xchange.polymarket.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** L2 API credentials as returned by {@code /auth/derive-api-key}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketApiCredentials(
    @JsonProperty("apiKey") String apiKey,
    @JsonProperty("secret") String secret,
    @JsonProperty("passphrase") String passphrase) {}
