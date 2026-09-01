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

/**
 * Coinbase fill-history filters, including product-native CFM filters and sequence windows.
 *
 * @since 1.0.2
 */
public class CoinbaseTradeHistoryParams implements TradeHistoryParamTransactionId,
    TradeHistoryParamOrderId, TradeHistoryParamMultiCurrencyPair, TradeHistoryParamLimit,
    TradeHistoryParamNextPageCursor, TradeHistoryParamsTimeSpan {

  private Set<CurrencyPair> currencyPairs;
  /** Raw Coinbase product ids (for example {@code BTC-USD} or {@code BTC-PERP}). */
  private Set<String> productIds;
  private String transactionId;
  private String orderId;
  private String nextPageCursor;
  /**
   * Whether {@link #nextPageCursor} and {@link #continuationFillIds} resume a partially consumed
   * mutable fill page.
   */
  private boolean fillContinuationPending;
  private Set<String> continuationFillIds = new HashSet<>();
  private Date startTime;
  private Date endTime;
  private Integer limit;
  /** Optional retail portfolio id filter used by some endpoints (for example perpetuals/INTX). */
  private String retailPortfolioId;
  /** Optional Coinbase fill sort field. */
  private String sortBy;
  /** Optional Coinbase asset filters. */
  private Set<String> assetFilters;
  /** Optional Coinbase order-type filters. */
  private Set<String> orderTypes;
  /** Optional Coinbase order-side filter. */
  private String orderSide;
  /** Optional Coinbase product-type filters. */
  private Set<String> productTypes;

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
    this.productIds = productIds == null ? new HashSet<>() : new HashSet<>(productIds);
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
    this.fillContinuationPending = false;
    this.continuationFillIds.clear();
  }

  /** @return whether the current cursor resumes a partially consumed mutable fills page */
  public boolean isFillContinuationPending() {
    return fillContinuationPending;
  }

  /**
   * Stores the cursor for a partial fills page without using a mutable result index.
   *
   * <p>When {@code pending} is true, the next request refetches the page and suppresses only
   * previously emitted fill identities. This admits new fills inserted ahead of the prior page
   * contents instead of skipping them by a stale numeric offset.</p>
   *
   * @param cursor raw Coinbase cursor used to request the page, or {@code null} for the first page
   * @param pending whether this is a partial-page continuation
   */
  public void setFillContinuation(String cursor, boolean pending) {
    this.nextPageCursor = cursor;
    this.fillContinuationPending = pending;
  }

  /** @return a defensive copy of fill identities emitted before the current continuation cursor */
  public Set<String> getContinuationFillIds() {
    return new HashSet<>(continuationFillIds);
  }

  /**
   * Records fill identities already emitted before resuming the current cursor.
   *
   * @param fillIds fill entry or trade identities, never {@code null}
   */
  public void setContinuationFillIds(Collection<String> fillIds) {
    this.continuationFillIds = new HashSet<>(fillIds);
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

  /** @return optional fill sort field */
  public String getSortBy() {
    return sortBy;
  }

  /** @param sortBy Coinbase fill sort field */
  public void setSortBy(String sortBy) {
    this.sortBy = sortBy;
  }

  /** @return optional asset filters */
  public Collection<String> getAssetFilters() {
    return assetFilters;
  }

  /** @param assetFilters Coinbase asset filters */
  public void setAssetFilters(Collection<String> assetFilters) {
    this.assetFilters = assetFilters == null ? null : new HashSet<>(assetFilters);
  }

  /** @return optional order-type filters */
  public Collection<String> getOrderTypes() {
    return orderTypes;
  }

  /** @param orderTypes Coinbase order-type filters */
  public void setOrderTypes(Collection<String> orderTypes) {
    this.orderTypes = orderTypes == null ? null : new HashSet<>(orderTypes);
  }

  /** @return optional order-side filter */
  public String getOrderSide() {
    return orderSide;
  }

  /** @param orderSide Coinbase order-side filter */
  public void setOrderSide(String orderSide) {
    this.orderSide = orderSide;
  }

  /** @return optional product-type filters */
  public Collection<String> getProductTypes() {
    return productTypes;
  }

  /** @param productTypes Coinbase product-type filters */
  public void setProductTypes(Collection<String> productTypes) {
    this.productTypes = productTypes == null ? null : new HashSet<>(productTypes);
  }
}
