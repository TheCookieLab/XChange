package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * UTA account-mode probe result.
 *
 * <p>{@code selfAccountMode} is {@code CLASSIC} or {@code UNIFIED}; a UTA credential set must
 * report {@code UNIFIED} before UTA trading operations are attempted.
 */
@Data
public class UtaAccountModeResponse {

  @JsonProperty("selfAccountMode")
  private String selfAccountMode;

  @JsonProperty("unifiedSubAccount")
  private java.util.List<Long> unifiedSubAccount;

  @JsonProperty("classicSubAccount")
  private java.util.List<Long> classicSubAccount;
}
