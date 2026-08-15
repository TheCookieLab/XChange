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
 * (inclusive) are optional. {@code limit} caps the total number of returned trades. Requests
 * are partitioned to the provider's per-request constraints: at most 100 trades per request
 * and at most one month of history per request window; pages are fetched and stitched by
 * advancing {@code startTime} past the newest trade seen, stopping on a short page or a
 * window that does not advance.
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
