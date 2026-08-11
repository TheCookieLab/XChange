package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * Order amendment request (currently futures only). Either {@code orderId} or {@code clientOid}
 * must be provided; {@code cxlOnFail} cancels the original order when the amendment fails.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/amend-order">Amend Order</a>
 */
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class UtaAmendOrderRequest {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("newPrice")
  private BigDecimal newPrice;

  @JsonProperty("newSize")
  private BigDecimal newSize;

  @JsonProperty("sizeUnit")
  private String sizeUnit;

  @JsonProperty("cxlOnFail")
  private Boolean cxlOnFail;

  @JsonProperty("slTriggerPrice")
  private BigDecimal slTriggerPrice;

  @JsonProperty("tpTriggerPrice")
  private BigDecimal tpTriggerPrice;

  @JsonProperty("slTriggerPriceType")
  private String slTriggerPriceType;

  @JsonProperty("tpTriggerPriceType")
  private String tpTriggerPriceType;
}
