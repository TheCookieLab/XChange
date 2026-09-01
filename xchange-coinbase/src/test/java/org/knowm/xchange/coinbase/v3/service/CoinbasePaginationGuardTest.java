package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import si.mazi.rescu.ParamsDigest;

/** Deterministic tests for bounded, loop-safe cursor pagination. */
public class CoinbasePaginationGuardTest {

  @Test
  public void repeatedCursorAbortsAccountsIteration() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    CoinbaseAccountsResponse page =
        new CoinbaseAccountsResponse(Collections.emptyList(), true, "stuck-cursor", null);
    when(authenticated.listAccounts(any(ParamsDigest.class), eq(250), any())).thenReturn(page);

    CoinbaseAccountServiceRaw service =
        new CoinbaseAccountServiceRaw(coinbaseExchange(), authenticated, mock(ParamsDigest.class));

    ExchangeException exception =
        assertThrows(ExchangeException.class, service::getCoinbaseAccounts);
    assertTrue(exception.getMessage().contains("repeated cursor"));
  }

  @Test
  public void repeatedCursorAbortsFillsIteration() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    CoinbaseOrdersResponse page = new CoinbaseOrdersResponse(Collections.emptyList(), "stuck");
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(page);

    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), authenticated, mock(ParamsDigest.class));

    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    ExchangeException exception =
        assertThrows(ExchangeException.class, () -> service.getTradeHistory(params));
    assertTrue(exception.getMessage().contains("repeated cursor"));
  }

  @Test
  public void fillsIterationForwardsContinuationCursor() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    List<String> requestedCursors = new ArrayList<>();
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenAnswer(
            invocation -> {
              requestedCursors.add(invocation.getArgument(8));
              if (requestedCursors.size() == 1) {
                return new CoinbaseOrdersResponse(Collections.singletonList(fill("1")), "next");
              }
              return new CoinbaseOrdersResponse(Arrays.asList(fill("1"), fill("2")), null);
            });

    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), authenticated, mock(ParamsDigest.class));

    UserTrades trades = service.getTradeHistory(new CoinbaseTradeHistoryParams());
    assertEquals(2, trades.getUserTrades().size());
    assertEquals(Arrays.asList(null, "next"), requestedCursors);
  }

  @Test
  public void finiteFillLimitPersistsSuccessfulContinuationCursor() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(new CoinbaseOrdersResponse(Collections.singletonList(fill("1")), "next"));
    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), authenticated, mock(ParamsDigest.class));
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.setLimit(1);

    UserTrades trades = service.getTradeHistory(params);

    assertEquals(1, trades.getUserTrades().size());
    assertEquals("next", params.getNextPageCursor());
  }

  @Test
  public void partiallyConsumedFillPageResumesAfterReturnedPrefix() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    List<String> requestedCursors = new ArrayList<>();
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenAnswer(
            invocation -> {
              String requestedCursor = invocation.getArgument(8);
              requestedCursors.add(requestedCursor);
              if (requestedCursor == null) {
                return new CoinbaseOrdersResponse(Collections.singletonList(fill("1")), "next");
              }
              if ("next".equals(requestedCursor)) {
                return new CoinbaseOrdersResponse(
                    Arrays.asList(fill("1"), fill("2"), fill("3")), "after");
              }
              return new CoinbaseOrdersResponse(Collections.emptyList(), null);
            });
    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), authenticated, mock(ParamsDigest.class));
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.setLimit(2);

    UserTrades limitedTrades = service.getTradeHistory(params);

    assertEquals(
        Arrays.asList("1-order", "2-order"),
        limitedTrades.getUserTrades().stream()
            .map(UserTrade::getOrderId)
            .collect(Collectors.toList()));
    assertEquals("next", params.getNextPageCursor());
    assertEquals(2, params.getNextPageCursorFillOffset());

    params.setLimit(null);
    UserTrades resumedTrades = service.getTradeHistory(params);

    assertEquals(
        Collections.singletonList("3-order"),
        resumedTrades.getUserTrades().stream()
            .map(UserTrade::getOrderId)
            .collect(Collectors.toList()));
    assertEquals(Arrays.asList(null, "next", "next", "after"), requestedCursors);
    assertEquals(null, params.getNextPageCursor());
    assertEquals(0, params.getNextPageCursorFillOffset());
  }

  @Test
  public void fillsFailureDoesNotMutateCallerCursor() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    List<String> requestedCursors = new ArrayList<>();
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenAnswer(
            invocation -> {
              requestedCursors.add(invocation.getArgument(8));
              if (requestedCursors.size() == 1) {
                return new CoinbaseOrdersResponse(Collections.singletonList(fill("1")), "next");
              }
              throw new IOException("later page unavailable");
            });

    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), authenticated, mock(ParamsDigest.class));
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.setNextPageCursor("initial");

    assertThrows(IOException.class, () -> service.getTradeHistory(params));
    assertEquals("initial", params.getNextPageCursor());
    assertEquals("initial", requestedCursors.get(0));

    int secondInvocation = requestedCursors.size();
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenAnswer(
            invocation -> {
              requestedCursors.add(invocation.getArgument(8));
              if (requestedCursors.size() == secondInvocation + 1) {
                return new CoinbaseOrdersResponse(Collections.singletonList(fill("1")), "next");
              }
              return new CoinbaseOrdersResponse(Collections.singletonList(fill("2")), null);
            });
    service.getTradeHistory(params);
    assertEquals("initial", requestedCursors.get(secondInvocation));
  }

  @Test
  public void fillsResponseWithoutRequiredCollectionFailsClosed() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(new CoinbaseOrdersResponse(null, null));

    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), authenticated, mock(ParamsDigest.class));
    ExchangeException exception =
        assertThrows(
            ExchangeException.class, () -> service.getTradeHistory(new CoinbaseTradeHistoryParams()));
    assertTrue(exception.getMessage().contains("required fills collection"));
  }

  @Test
  public void adjustedFillsSharingTradeIdRemainDistinctByEntryId() throws Exception {
    CoinbaseAuthenticated api = mock(CoinbaseAuthenticated.class);
    when(api.listFills(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(page(fill("entry-1", "shared-trade"), "cursor-1"))
        .thenReturn(page(fill("entry-2", "shared-trade"), ""));
    CoinbaseTradeService service =
        new CoinbaseTradeService(mock(Exchange.class), api, mock(ParamsDigest.class));

    UserTrades history = service.getTradeHistory(new CoinbaseTradeHistoryParams());

    assertEquals(2, history.getUserTrades().size());
    assertEquals(
        Arrays.asList("entry-1-order", "entry-2-order"),
        history.getUserTrades().stream().map(UserTrade::getOrderId).collect(Collectors.toList()));
  }

  @Test
  public void orderHistoryIteratesPagesUntilExhausted() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listOrders(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(
            new CoinbaseListOrdersResponse(Arrays.asList(order("1"), order("2")), "next", true),
            new CoinbaseListOrdersResponse(Arrays.asList(order("2"), order("3")), "stale", false));

    CoinbaseTradeServiceRaw service =
        new CoinbaseTradeServiceRaw(mock(Exchange.class), authenticated, mock(ParamsDigest.class));

    assertEquals(3, service.listOrdersBounded(null).size());
  }

  @Test
  public void orderHistoryRejectsHasNextWithoutContinuationCursor() throws Exception {
    for (String cursor : Arrays.asList((String) null, "", "   ")) {
      CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
      when(authenticated.listOrders(
              any(ParamsDigest.class),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any()))
          .thenReturn(
              new CoinbaseListOrdersResponse(
                  Collections.singletonList(order("partial")), cursor, true));

      CoinbaseTradeServiceRaw service =
          new CoinbaseTradeServiceRaw(
              mock(Exchange.class), authenticated, mock(ParamsDigest.class));

      ExchangeException exception =
          assertThrows(ExchangeException.class, () -> service.listOrdersBounded(null));
      assertTrue(exception.getMessage().contains("has_next=true"));
      assertTrue(exception.getMessage().contains("continuation cursor"));
    }
  }

  @Test
  public void orderHistoryStopsAtCallerLimit() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listOrders(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(
            new CoinbaseListOrdersResponse(Arrays.asList(order("1"), order("2")), "next", true),
            new CoinbaseListOrdersResponse(Collections.singletonList(order("3")), null, false));

    CoinbaseTradeServiceRaw service =
        new CoinbaseTradeServiceRaw(mock(Exchange.class), authenticated, mock(ParamsDigest.class));

    assertEquals(2, service.listOrdersBounded(2).size());
  }

  @Test
  public void orderHistoryResponseWithoutRequiredCollectionFailsClosed() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listOrders(
            any(ParamsDigest.class),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(new CoinbaseListOrdersResponse(null, null, false));

    CoinbaseTradeServiceRaw service =
        new CoinbaseTradeServiceRaw(mock(Exchange.class), authenticated, mock(ParamsDigest.class));
    ExchangeException exception =
        assertThrows(ExchangeException.class, () -> service.listOrdersBounded(null));
    assertTrue(exception.getMessage().contains("required orders collection"));
  }

  @Test
  public void futuresPositionsResponseWithoutRequiredCollectionFailsClosed() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listFuturesPositions(any(ParamsDigest.class)))
        .thenReturn(
            new org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionsResponse(null));

    CoinbaseTradeServiceRaw service =
        new CoinbaseTradeServiceRaw(mock(Exchange.class), authenticated, mock(ParamsDigest.class));
    ExchangeException exception =
        assertThrows(ExchangeException.class, service::listFuturesPositions);
    assertTrue(exception.getMessage().contains("required positions collection"));
  }

  private static Exchange coinbaseExchange() {
    CoinbaseExchange exchange = new CoinbaseExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    return exchange;
  }

  private static CoinbaseOrderDetail order(String id) {
    return new CoinbaseOrderDetail(
        id, "client-" + id, "BUY", "BTC-USD", "OPEN", null, null, null, null, null, null);
  }

  private static CoinbaseFill fill(String id) {
    return fill(id, id);
  }

  private static CoinbaseFill fill(String entryId, String tradeId) {
    return new CoinbaseFill(
        entryId,
        tradeId,
        entryId + "-order",
        "2026-02-08T00:00:00Z",
        "FILL",
        new BigDecimal("1"),
        new BigDecimal("0.15"),
        new BigDecimal("0.001"),
        "BTC-USD",
        "2026-02-08T00:00:00Z",
        "TAKER",
        false,
        "user",
        "BUY",
        "portfolio");
  }

  private static CoinbaseOrdersResponse page(CoinbaseFill fill, String cursor) {
    return new CoinbaseOrdersResponse(Collections.singletonList(fill), cursor);
  }
}
