package org.knowm.xchange.coinbasederivatives.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Access token returned by {@code public/auth}. */
public record AccessToken(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    String scope) {}
