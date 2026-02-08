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
  /** Raw Coinbase product ids (for example {@code BTC-USD} or {@code BTC-PERP}). */
  private Set<String> productIds;
  private String transactionId;
  private String orderId;
  private String nextPageCursor;
  private Date startTime;
  private Date endTime;
  private Integer limit;
  /** Optional retail portfolio id filter used by some endpoints (for example perpetuals/INTX). */
  private String retailPortfolioId;

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
    if (this.currencyPairs == null) {
      this.currencyPairs = new HashSet<>();
    }
    this.currencyPairs.add(currencyPair);
  }

  /**
   * @return raw Coinbase product ids to filter fills by (optional)
   */
  public Collection<String> getProductIds() {
    return productIds;
  }

  /**
   * Set raw Coinbase product ids to filter fills by.
   *
   * <p>This is a Coinbase-specific escape hatch for products that do not have a natural
   * {@link CurrencyPair} representation (for example perpetual futures products like {@code BTC-PERP}).</p>
   *
   * @param productIds product ids (nullable)
   */
  public void setProductIds(Collection<String> productIds) {
    if (productIds == null) {
      this.productIds = null;
      return;
    }
    this.productIds = new HashSet<>(productIds);
  }

  /**
   * Add a single raw Coinbase product id filter.
   *
   * @param productId product id (ignored when null/blank)
   */
  public void addProductId(String productId) {
    if (productId == null || productId.trim().isEmpty()) {
      return;
    }
    if (this.productIds == null) {
      this.productIds = new HashSet<>();
    }
    this.productIds.add(productId.trim());
  }

  /**
   * @return retail portfolio id used for filtering fills (optional)
   */
  public String getRetailPortfolioId() {
    return retailPortfolioId;
  }

  /**
   * Set the retail portfolio id filter.
   *
   * <p>This is primarily used for Coinbase perpetuals/INTX portfolios where fills are logically scoped
   * to a portfolio UUID.</p>
   *
   * @param retailPortfolioId portfolio id (nullable)
   */
  public void setRetailPortfolioId(String retailPortfolioId) {
    this.retailPortfolioId = retailPortfolioId;
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
