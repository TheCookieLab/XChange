package org.knowm.xchange.coinbase.v3.dto.trade;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamMultiCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamNextPageCursor;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOrderId;
import org.knowm.xchange.service.trade.params.TradeHistoryParamTransactionId;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;

public class CoinbaseTradeHistoryParams implements TradeHistoryParamTransactionId,
    TradeHistoryParamOrderId, TradeHistoryParamMultiCurrencyPair, TradeHistoryParamLimit,
    TradeHistoryParamNextPageCursor, TradeHistoryParamsTimeSpan {

  private Set<CurrencyPair> currencyPairs;
  private String transactionId;
  private String orderId;
  private String nextPageCursor;
  private Date startTime;
  private Date endTime;
  private Integer limit;

  public CoinbaseTradeHistoryParams() {
  }

  public CoinbaseTradeHistoryParams(Set<CurrencyPair> currencyPairs, Date startTime, Date endTime,
      Integer limit, String nextPageCursor, String orderId, String transactionId) {
    this.currencyPairs = currencyPairs;
    this.startTime = startTime;
    this.endTime = endTime;
    this.limit = limit;
    this.nextPageCursor = nextPageCursor;
    this.orderId = orderId;
    this.transactionId = transactionId;
  }

  @Override
  public Collection<CurrencyPair> getCurrencyPairs() {
    return currencyPairs;
  }

  @Override
  public void setCurrencyPairs(Collection<CurrencyPair> currencyPairs) {
    this.currencyPairs = new HashSet<>(currencyPairs);
  }

  public void addCurrencyPair(CurrencyPair currencyPair) {
    this.currencyPairs.add(currencyPair);
  }

  @Override
  public String getNextPageCursor() {
    return nextPageCursor;
  }

  @Override
  public void setNextPageCursor(String cursor) {
    this.nextPageCursor = cursor;
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
  public String getTransactionId() {
    return transactionId;
  }

  @Override
  public void setTransactionId(String txId) {
    this.transactionId = txId;
  }

  @Override
  public Integer getLimit() {
    return this.limit;
  }

  @Override
  public void setLimit(Integer limit) {
    this.limit = limit;
  }


}
