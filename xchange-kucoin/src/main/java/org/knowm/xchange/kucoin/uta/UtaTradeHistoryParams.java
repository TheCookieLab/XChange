package org.knowm.xchange.kucoin.uta;

import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamNextPageCursor;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;

/**
 * UTA trade-history parameters.
 *
 * <p>Pagination is cursor based: {@code lastId} is the cursor of the last record of the previous
 * page (UTA has no page-number/total-count semantics on history endpoints).
 */
public class UtaTradeHistoryParams
    implements TradeHistoryParamCurrencyPair,
        TradeHistoryParamsTimeSpan,
        TradeHistoryParamNextPageCursor {

  private org.knowm.xchange.currency.CurrencyPair currencyPair;
  private java.util.Date startTime;
  private java.util.Date endTime;
  private String nextPageCursor;
  private String side;
  private String orderFilter;

  @Override
  public org.knowm.xchange.currency.CurrencyPair getCurrencyPair() {
    return currencyPair;
  }

  @Override
  public void setCurrencyPair(org.knowm.xchange.currency.CurrencyPair currencyPair) {
    this.currencyPair = currencyPair;
  }

  @Override
  public java.util.Date getStartTime() {
    return startTime;
  }

  @Override
  public void setStartTime(java.util.Date startTime) {
    this.startTime = startTime;
  }

  @Override
  public java.util.Date getEndTime() {
    return endTime;
  }

  @Override
  public void setEndTime(java.util.Date endTime) {
    this.endTime = endTime;
  }

  @Override
  public String getNextPageCursor() {
    return nextPageCursor;
  }

  @Override
  public void setNextPageCursor(String nextPageCursor) {
    this.nextPageCursor = nextPageCursor;
  }

  public String getSide() {
    return side;
  }

  public void setSide(String side) {
    this.side = side;
  }

  public String getOrderFilter() {
    return orderFilter;
  }

  public void setOrderFilter(String orderFilter) {
    this.orderFilter = orderFilter;
  }
}
