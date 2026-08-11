package org.knowm.xchange.bitget.uta.v3.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Transfer confirmation returned by {@code POST /api/v3/account/transfer}.
 *
 * <p>The provider assigns the {@code transferId}; {@code clientOid} echoes the request value when
 * supplied. Transfer status must be confirmed via the relevant balance endpoint — a transfer is
 * asynchronous and no direct status endpoint is documented.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3TransferResult {

  @JsonProperty("transferId")
  private String transferId;

  @JsonProperty("clientOid")
  private String clientOid;
}
