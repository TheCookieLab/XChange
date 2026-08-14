package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxWithdrawalRequest} instead.
 */
@Deprecated
@Builder
@Getter
@ToString
public class OkexWithdrawalRequest {

  @JsonProperty("ccy")
  private String currency;

  @JsonProperty("amt")
  private String amount;

  // 3: internal, 4: on chain
  @JsonProperty("dest")
  private String method;

  @JsonProperty("toAddr")
  private String address;

  @JsonProperty("fee")
  private String fee;

  @JsonProperty("chain")
  private String chain;

  @JsonProperty("clientId")
  private String clientId;

  public OkxWithdrawalRequest to() {
    return OkxWithdrawalRequest.builder()
        .currency(currency)
        .amount(amount)
        .method(method)
        .address(address)
        .fee(fee)
        .chain(chain)
        .clientId(clientId)
        .build();
  }
}
