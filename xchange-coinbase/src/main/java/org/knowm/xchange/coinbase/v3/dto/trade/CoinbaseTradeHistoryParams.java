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
   * Number of raw fills already consumed from the page requested with {@link #nextPageCursor}.
   *
   * <p>This is continuation state owned by the high-level fill-history adapter. It is zero for a
   * complete page or a caller-supplied cursor, and permits a later limited request to resume a
   * partially consumed Coinbase page without changing the raw remote cursor.
   */
  private int nextPageCursorFillOffset;
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
    this.nextPageCursorFillOffset = 0;
  }

  /**
   * Returns the number of raw fills already consumed from the page identified by {@link
   * #getNextPageCursor()}.
   *
   * <p>The offset is reset when callers set a cursor through {@link #setNextPageCursor(String)}.
   * It is meaningful only when {@link org.knowm.xchange.coinbase.v3.service.CoinbaseTradeService}
   * has stopped at a configured limit in the middle of that page.
   *
   * @return nonnegative raw-result offset for the current cursor
   */
  public int getNextPageCursorFillOffset() {
    return nextPageCursorFillOffset;
  }

  /**
   * Stores a raw Coinbase cursor and the number of raw fills already consumed from its page.
   *
   * <p>This preserves the remote cursor exactly while recording the high-level continuation state
   * necessary to resume a partially consumed page. The offset is zero after a complete page.
   *
   * @param cursor raw Coinbase cursor used to request the page, or {@code null} for the first page
   * @param fillOffset nonnegative number of raw fills already consumed from that page
   * @throws IllegalArgumentException if {@code fillOffset} is negative
   */
  public void setNextPageCursorContinuation(String cursor, int fillOffset) {
    if (fillOffset < 0) {
      throw new IllegalArgumentException("Fill continuation offset must not be negative");
    }
    this.nextPageCursor = cursor;
    this.nextPageCursorFillOffset = fillOffset;
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
