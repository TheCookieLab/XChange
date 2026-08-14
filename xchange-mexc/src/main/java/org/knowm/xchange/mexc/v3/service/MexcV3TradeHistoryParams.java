package org.knowm.xchange.mexc.v3.service;

import java.util.Date;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.DefaultTradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOrderId;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;

/**
 * Trade-history parameters for {@code GET /api/v3/myTrades}.
 *
 * <p>The MEXC endpoint requires a symbol; {@code orderId}, {@code startTime}/{@code endTime}
 * (inclusive) and {@code limit} (default 100, max 100) are optional.
 */
public class MexcV3TradeHistoryParams extends DefaultTradeHistoryParamCurrencyPair
    implements TradeHistoryParamOrderId, TradeHistoryParamsTimeSpan, TradeHistoryParamLimit {

  private String orderId;
  private Date startTime;
  private Date endTime;
  private Integer limit;

  public MexcV3TradeHistoryParams() {}

  public MexcV3TradeHistoryParams(CurrencyPair currencyPair) {
    setCurrencyPair(currencyPair);
  }

  @Override
  public String getOrderId() {
    return orderId;
  }

  @Override
  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  @Override
  public Date getStartTime() {
    return startTime;
  }

  @Override
  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  @Override
  public Date getEndTime() {
    return endTime;
  }

  @Override
  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }

  @Override
  public Integer getLimit() {
    return limit;
  }

  @Override
  public void setLimit(Integer limit) {
    this.limit = limit;
  }
}
