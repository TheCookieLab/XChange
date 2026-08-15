package org.knowm.xchange.mexc.v3.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One user trade from {@code GET /api/v3/myTrades}. */
public class MexcV3MyTrade {

  private final String symbol;
  private final long id;
  private final String orderId;
  private final long orderListId;
  private final String price;
  private final String qty;
  private final String quoteQty;
  private final String commission;
  private final String commissionAsset;
  private final long time;
  private final boolean isBuyer;
  private final boolean isMaker;
  private final boolean isBestMatch;
  private final boolean isSelfTrade;
  private final String clientOrderId;

  public MexcV3MyTrade(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("id") long id,
      @JsonProperty("orderId") String orderId,
      @JsonProperty("orderListId") long orderListId,
      @JsonProperty("price") String price,
      @JsonProperty("qty") String qty,
      @JsonProperty("quoteQty") String quoteQty,
      @JsonProperty("commission") String commission,
      @JsonProperty("commissionAsset") String commissionAsset,
      @JsonProperty("time") long time,
      @JsonProperty("isBuyer") boolean isBuyer,
      @JsonProperty("isMaker") boolean isMaker,
      @JsonProperty("isBestMatch") boolean isBestMatch,
      @JsonProperty("isSelfTrade") boolean isSelfTrade,
      @JsonProperty("clientOrderId") String clientOrderId) {
    this.symbol = symbol;
    this.id = id;
    this.orderId = orderId;
    this.orderListId = orderListId;
    this.price = price;
    this.qty = qty;
    this.quoteQty = quoteQty;
    this.commission = commission;
    this.commissionAsset = commissionAsset;
    this.time = time;
    this.isBuyer = isBuyer;
    this.isMaker = isMaker;
    this.isBestMatch = isBestMatch;
    this.isSelfTrade = isSelfTrade;
    this.clientOrderId = clientOrderId;
  }

  public String getSymbol() {
    return symbol;
  }

  public long getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public long getOrderListId() {
    return orderListId;
  }

  public String getPrice() {
    return price;
  }

  public String getQty() {
    return qty;
  }

  public String getQuoteQty() {
    return quoteQty;
  }

  public String getCommission() {
    return commission;
  }

  public String getCommissionAsset() {
    return commissionAsset;
  }

  public long getTime() {
    return time;
  }

  public boolean isBuyer() {
    return isBuyer;
  }

  public boolean isMaker() {
    return isMaker;
  }

  public boolean isBestMatch() {
    return isBestMatch;
  }

  public boolean isSelfTrade() {
    return isSelfTrade;
  }

  public String getClientOrderId() {
    return clientOrderId;
  }
}
