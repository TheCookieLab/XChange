package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 trade fill.
 *
 * <p>Wire enums: tradeScope {@code taker|maker}; tradeSide {@code open|close}; isRPI {@code
 * yes|no}. Timestamps are Unix milliseconds as decimal strings.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Fill {

  @JsonProperty("execId")
  private String execId;

  @JsonProperty("execLinkId")
  private String execLinkId;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("side")
  private String side;

  @JsonProperty("execPrice")
  private BigDecimal execPrice;

  @JsonProperty("execQty")
  private BigDecimal execQty;

  @JsonProperty("execValue")
  private BigDecimal execValue;

  @JsonProperty("tradeScope")
  private String tradeScope;

  @JsonProperty("tradeSide")
  private String tradeSide;

  @JsonProperty("feeDetail")
  private java.util.List<BitgetUtaV3Order.BitgetUtaV3Fee> feeDetail;

  @JsonProperty("createdTime")
  private String createdTime;

  @JsonProperty("updatedTime")
  private String updatedTime;

  /** Realized PnL of the fill (futures). */
  @JsonProperty("execPnl")
  private BigDecimal execPnl;

  @JsonProperty("isRPI")
  private String isRPI;
}
