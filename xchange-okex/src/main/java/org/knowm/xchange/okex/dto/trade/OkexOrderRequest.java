package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxOrderRequest} instead.
 */
@Deprecated
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkexOrderRequest {

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("instIdCode")
  private String instIdCode;

  @JsonProperty("tdMode")
  private String tradeMode;

  @JsonProperty("ccy")
  private String marginCurrency;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  @JsonProperty("tag")
  private String tag;

  @JsonProperty("side")
  private String side;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("ordType")
  private String orderType;

  @JsonProperty("sz")
  private String amount;

  @JsonProperty("px")
  private String price;

  @JsonProperty("reduceOnly")
  private boolean reducePosition;

  @JsonProperty("tradeQuoteCcy")
  private String tradeQuoteCcy;

  @JsonProperty("tpTriggerPx")
  private String takeProfitTriggerPrice;

  @JsonProperty("tpOrdPx")
  private String takeProfitOrderPrice;

  @JsonProperty("slTriggerPx")
  private String stopLossTriggerPrice;

  @JsonProperty("slOrdPx")
  private String stopLossOrderPrice;

  @JsonProperty("tpTriggerPxType")
  private String takeProfitTriggerPriceType;

  @JsonProperty("slTriggerPxType")
  private String stopLossTriggerPriceType;

  @JsonProperty("attachAlgoOrds")
  private List<OkexAttachAlgoOrder> attachAlgoOrds;

  /** Compatibility constructor wrapping the canonical DTO. */
  public OkexOrderRequest(OkxOrderRequest delegate) {
    this.instrumentId = delegate.getInstrumentId();
    this.instIdCode = delegate.getInstIdCode();
    this.tradeMode = delegate.getTradeMode();
    this.marginCurrency = delegate.getMarginCurrency();
    this.clientOrderId = delegate.getClientOrderId();
    this.tag = delegate.getTag();
    this.side = delegate.getSide();
    this.posSide = delegate.getPosSide();
    this.orderType = delegate.getOrderType();
    this.amount = delegate.getAmount();
    this.price = delegate.getPrice();
    this.reducePosition = delegate.isReducePosition();
    this.tradeQuoteCcy = delegate.getTradeQuoteCcy();
    this.takeProfitTriggerPrice = delegate.getTakeProfitTriggerPrice();
    this.takeProfitOrderPrice = delegate.getTakeProfitOrderPrice();
    this.stopLossTriggerPrice = delegate.getStopLossTriggerPrice();
    this.stopLossOrderPrice = delegate.getStopLossOrderPrice();
    this.takeProfitTriggerPriceType = delegate.getTakeProfitTriggerPriceType();
    this.stopLossTriggerPriceType = delegate.getStopLossTriggerPriceType();
    List<org.knowm.xchange.okx.dto.trade.OkxAttachAlgoOrder> delegateAttachAlgoOrds =
        delegate.getAttachAlgoOrds();
    List<OkexAttachAlgoOrder> attachAlgoOrds = null;
    if (delegateAttachAlgoOrds != null) {
      attachAlgoOrds =
          delegateAttachAlgoOrds.stream()
              .map(
                  attach ->
                      new OkexAttachAlgoOrder(
                          attach.getTakeProfitTriggerPrice(),
                          attach.getTakeProfitOrderPrice(),
                          attach.getStopLossTriggerPrice(),
                          attach.getStopLossOrderPrice(),
                          attach.getTakeProfitTriggerPriceType(),
                          attach.getStopLossTriggerPriceType(),
                          attach.getAmount()))
              .collect(Collectors.toList());
    }
    this.attachAlgoOrds = attachAlgoOrds;
  }

  public OkxOrderRequest to() {
    return OkxOrderRequest.builder()
        .instrumentId(instrumentId)
        .instIdCode(instIdCode)
        .tradeMode(tradeMode)
        .marginCurrency(marginCurrency)
        .clientOrderId(clientOrderId)
        .tag(tag)
        .side(side)
        .posSide(posSide)
        .orderType(orderType)
        .amount(amount)
        .price(price)
        .reducePosition(reducePosition)
        .tradeQuoteCcy(tradeQuoteCcy)
        .takeProfitTriggerPrice(takeProfitTriggerPrice)
        .takeProfitOrderPrice(takeProfitOrderPrice)
        .stopLossTriggerPrice(stopLossTriggerPrice)
        .stopLossOrderPrice(stopLossOrderPrice)
        .takeProfitTriggerPriceType(takeProfitTriggerPriceType)
        .stopLossTriggerPriceType(stopLossTriggerPriceType)
        .attachAlgoOrds(toAttachAlgoOrders(attachAlgoOrds))
        .build();
  }

  private static List<org.knowm.xchange.okx.dto.trade.OkxAttachAlgoOrder> toAttachAlgoOrders(
      List<OkexAttachAlgoOrder> orders) {
    if (orders == null) {
      return null;
    }
    return orders.stream().map(OkexAttachAlgoOrder::to).collect(Collectors.toList());
  }
}
