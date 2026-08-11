package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** UTA account balance snapshot from {@code GET /api/ua/v1/unified/account/balance}. */
@Data
public class UtaAccountBalance {

  @JsonProperty("accountType")
  private String accountType;

  @JsonProperty("ts")
  private Long ts;

  @JsonProperty("accounts")
  private List<UtaAccountCurrencyGroup> accounts;

  @Data
  public static class UtaAccountCurrencyGroup {
    @JsonProperty("currencies")
    private List<UtaCurrencyAsset> currencies;
  }
}
