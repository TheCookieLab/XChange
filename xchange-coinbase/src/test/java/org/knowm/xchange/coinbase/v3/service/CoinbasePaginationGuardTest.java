package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    return new CoinbaseFill(
        "entry-" + id,
        "trade-" + id,
        "order-" + id,
        "2026-02-08T00:00:00Z",
        "FILL",
        new BigDecimal("2500"),
        new BigDecimal("1"),
        new BigDecimal("0.15"),
        "BTC-USD",
        "2026-02-08T00:00:00Z",
        "TAKER",
        false,
        "user",
        "BUY",
        "portfolio");
  }
}
