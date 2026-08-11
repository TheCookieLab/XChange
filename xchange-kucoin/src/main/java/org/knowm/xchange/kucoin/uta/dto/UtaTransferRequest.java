package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * UTA flex-transfer request body.
 *
 * <p>{@code type} is one of {@code 0} (internal), {@code 1} (parent to sub), {@code 2} (sub to
 * parent), {@code 3} (sub to sub). Account types: FUNDING, SPOT, FUTURES, CROSS, ISOLATED, UNIFIED.
 */
@Data
@JsonInclude(Include.NON_NULL)
public class UtaTransferRequest {

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("amount")
  private BigDecimal amount;

  @JsonProperty("type")
  private String type;

  @JsonProperty("fromAccountType")
  private String fromAccountType;

  @JsonProperty("toAccountType")
  private String toAccountType;

  @JsonProperty("fromAccountSymbol")
  private String fromAccountSymbol;

  @JsonProperty("toAccountSymbol")
  private String toAccountSymbol;

  @JsonProperty("fromUid")
  private String fromUid;

  @JsonProperty("toUid")
  private String toUid;
}
