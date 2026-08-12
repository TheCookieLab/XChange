package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Place-order request body for {@code POST /api/v3/trade/place-order}.
 *
 * <p>Wire enums (lowercase): side {@code buy|sell}; orderType {@code limit|market}; timeInForce
 * {@code ioc|fok|gtc|post_only|rpi}; marginMode {@code crossed|isolated} (futures only); holdMode
 * {@code one_way_mode|hedge_mode}; reduceOnly {@code yes|no}; posSide {@code long|short} (futures
 * only); tpTriggerBy/slTriggerBy {@code market|mark}; tpOrderType/slOrderType {@code limit|market}.
 *
 * <p>{@code price} is required for limit orders; {@code qty} is the required size parameter for all
 * order types (base-coin quantity for limit and market-sell, quote-coin spend for market-buy on
 * spot/margin). There is no {@code amount} parameter on the v3 endpoint. {@code clientOid} must
 * match {@code ^[\.A-Z\:/a-z0-9_-]{1,32}$} and is idempotent for 6 hours.
 */
@Data
@Builder(toBuilder = true)
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitgetUtaV3PlaceOrderRequest {

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("side")
  private String side;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("qty")
  private BigDecimal qty;

  @JsonProperty("timeInForce")
  private String timeInForce;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("marginCoin")
  private String marginCoin;

  @JsonProperty("reduceOnly")
  private String reduceOnly;

  @JsonProperty("holdMode")
  private String holdMode;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("stpMode")
  private String stpMode;

  @JsonProperty("tpTriggerBy")
  private String tpTriggerBy;

  @JsonProperty("slTriggerBy")
  private String slTriggerBy;

  @JsonProperty("takeProfit")
  private BigDecimal takeProfit;

  @JsonProperty("stopLoss")
  private BigDecimal stopLoss;

  @JsonProperty("tpOrderType")
  private String tpOrderType;

  @JsonProperty("slOrderType")
  private String slOrderType;

  @JsonProperty("tpLimitPrice")
  private BigDecimal tpLimitPrice;

  @JsonProperty("slLimitPrice")
  private BigDecimal slLimitPrice;
}
