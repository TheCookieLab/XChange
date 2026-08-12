package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Order identity returned by place/cancel/modify/strategy endpoints.
 *
 * <p>Only the fields present in the response are populated; {@code clientOid} echoes the request
 * value when supplied.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3OrderId {

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;
}
