package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.kalshi.KalshiAdapters;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse;
import org.knowm.xchange.kalshi.dto.account.KalshiPositionsResponse.KalshiMarketPosition;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse.KalshiFill;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrder;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrdersResponse;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.InstrumentParam;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/** Generic trade service for Kalshi; placement is limit-only on the YES leg. */
public class KalshiTradeService extends KalshiTradeServiceRaw implements TradeService {

  /** Safety bound on cursor-following loops in the generic collection reads. */
  private static final int MAX_PAGES = 100;

  public KalshiTradeService(KalshiExchange exchange) {
    super(exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    final String ticker =
        params instanceof OpenOrdersParamInstrument instrumentParams
                && instrumentParams.getInstrument() != null
            ? KalshiAdapters.marketTicker(instrumentParams.getInstrument())
            : null;
    List<LimitOrder> openOrders = new ArrayList<>();
    for (KalshiOrder order :
        fetchAllPages(
            null,
            cursor -> getKalshiOrders(ticker, "resting", null, cursor),
            KalshiOrdersResponse::cursor,
            KalshiOrdersResponse::orders,
            KalshiOrder::orderId)) {
      openOrders.add(KalshiAdapters.adaptOrder(order));
    }
    return new OpenOrders(openOrders);
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamInstrument();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    return placeKalshiOrder(KalshiAdapters.toCreateOrderRequest(limitOrder)).orderId();
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    throw new NotAvailableFromExchangeException(
        "Kalshi V2 event orders require a limit price; market orders are not supported.");
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (!(orderParams instanceof CancelOrderByIdParams idParams)) {
      throw new IllegalArgumentException(
          "Kalshi cancel requires a CancelOrderByIdParams (provider order id).");
    }
    KalshiOrder order = cancelKalshiOrder(idParams.getOrderId()).order();
    return order != null && "canceled".equals(order.status());
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>();
    for (OrderQueryParams params : orderQueryParams) {
      orders.add(KalshiAdapters.adaptOrder(getKalshiOrder(params.getOrderId()).order()));
    }
    return orders;
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    final String ticker =
        params instanceof TradeHistoryParamInstrument instrumentParams
                && instrumentParams.getInstrument() != null
            ? KalshiAdapters.marketTicker(instrumentParams.getInstrument())
            : null;
    List<UserTrade> trades = new ArrayList<>();
    for (KalshiFill fill :
        fetchAllPages(
            null,
            cursor -> getKalshiFills(ticker, null, null, cursor),
            KalshiFillsResponse::cursor,
            KalshiFillsResponse::fills,
            KalshiFill::fillId)) {
      trades.add(KalshiAdapters.adaptFill(fill));
    }
    return new UserTrades(
        trades, org.knowm.xchange.dto.marketdata.Trades.TradeSortType.SortByTimestamp);
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    List<KalshiMarketPosition> positions =
        fetchAllPages(
            null,
            cursor ->
                kalshiAuthenticated.getPositions(apiKey, timestampFactory(), digest, null, cursor),
            KalshiPositionsResponse::cursor,
            KalshiPositionsResponse::marketPositions,
            KalshiMarketPosition::ticker);
    return new OpenPositions(KalshiAdapters.adaptPositions(positions));
  }

  /**
   * Follows a Kalshi cursor-paginated read to exhaustion so that the generic collection methods
   * never silently truncate an account.
   *
   * <p>Each provider response carries a {@code cursor} for the next page; an empty or absent
   * cursor terminates the loop. Iterations are bounded by {@link #MAX_PAGES} and items are
   * de-duplicated by id so a repeated record across page boundaries is accumulated once. When the
   * bound is hit without a terminal cursor the method fails loudly instead of returning a
   * truncated collection.
   *
   * @param initialCursor cursor for the first page, or {@code null}
   * @param fetchPage loads one page for a given cursor
   * @param nextCursor extracts the next-page cursor from a response
   * @param items extracts the item list from a response
   * @param itemId extracts the stable id used for de-duplication
   * @return all items across pages, de-duplicated and ordered as returned
   * @throws IOException on transport errors
   * @throws IllegalStateException when pagination does not terminate within {@link #MAX_PAGES}
   *     pages
   */
  private <R, T> List<T> fetchAllPages(
      String initialCursor,
      PageFetcher<R> fetchPage,
      Function<R, String> nextCursor,
      Function<R, List<T>> items,
      Function<T, String> itemId)
      throws IOException {
    List<T> all = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String cursor = initialCursor;
    for (int page = 0; page < MAX_PAGES; page++) {
      R response = fetchPage.fetch(cursor);
      List<T> pageItems = items.apply(response);
      if (pageItems != null) {
        for (T item : pageItems) {
          String id = itemId.apply(item);
          if (id == null || seen.add(id)) {
            all.add(item);
          }
        }
      }
      cursor = nextCursor.apply(response);
      if (cursor == null || cursor.isBlank()) {
        return all;
      }
    }
    throw new IllegalStateException(
        "Kalshi cursor pagination did not terminate within "
            + MAX_PAGES
            + " pages; refusing to return a truncated collection");
  }

  /** Page loader that may throw {@link IOException}. */
  @FunctionalInterface
  private interface PageFetcher<R> {
    R fetch(String cursor) throws IOException;
  }
}
