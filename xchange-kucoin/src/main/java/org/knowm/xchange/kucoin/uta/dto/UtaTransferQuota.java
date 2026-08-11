package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/** Transferable-balance result from {@code GET /api/ua/v1/account/transfer-quota}. */
@Data
public class UtaTransferQuota {

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("transferable")
  private BigDecimal transferable;

  @JsonProperty("accountType")
  private String accountType;
}
