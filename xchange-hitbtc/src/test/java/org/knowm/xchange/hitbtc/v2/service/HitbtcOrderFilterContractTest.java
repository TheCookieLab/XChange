package org.knowm.xchange.hitbtc.v2.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.hitbtc.v2.HitbtcExchange;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

class HitbtcOrderFilterContractTest {
  private Exchange exchange(String uri) {
    Exchange exchange = mock(Exchange.class);
    ExchangeSpecification specification = new HitbtcExchange().getDefaultExchangeSpecification();
    specification.setSslUri(uri);
    specification.setApiKey("test-key");
    specification.setSecretKey("test-secret");
    when(exchange.getExchangeSpecification()).thenReturn(specification);
    return exchange;
  }

  @Test
  void highLevelFactoryDefaultsToAllAndPassesSelectedPair() throws Exception {
    HitbtcTradeService service = spy(new HitbtcTradeService(exchange("http://127.0.0.1")));
    doReturn(Collections.emptyList()).when(service).getOpenOrdersRaw();
    doReturn(Collections.emptyList()).when(service).getOpenOrdersRaw(CurrencyPair.BTC_USDT);
    OpenOrdersParams params = service.createOpenOrdersParams();
    assertInstanceOf(OpenOrdersParamCurrencyPair.class, params);
    service.getOpenOrders();
    service.getOpenOrders(params);
    verify(service, times(2)).getOpenOrdersRaw();
    ((OpenOrdersParamCurrencyPair) params).setCurrencyPair(CurrencyPair.BTC_USDT);
    service.getOpenOrders(params);
    verify(service).getOpenOrdersRaw(CurrencyPair.BTC_USDT);
  }

  @Test
  void activeAndHistoricalFiltersAreEncodedOnTheirGetEndpoints() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    List<String> methods = new CopyOnWriteArrayList<>();
    List<String> auth = new CopyOnWriteArrayList<>();
    List<URI> requests = new CopyOnWriteArrayList<>();
    server.createContext("/api/2/", http -> {
      methods.add(http.getRequestMethod());
      auth.add(http.getRequestHeaders().getFirst("Authorization"));
      requests.add(http.getRequestURI());
      byte[] response = "[]".getBytes(StandardCharsets.UTF_8);
      http.getResponseHeaders().add("Content-Type", "application/json");
      http.sendResponseHeaders(200, response.length);
      http.getResponseBody().write(response);
      http.close();
    });
    server.start();
    try {
      HitbtcTradeServiceRaw service = new HitbtcTradeServiceRaw(
          exchange("http://127.0.0.1:" + server.getAddress().getPort()));
      service.getOpenOrdersRaw();
      service.getOpenOrdersRaw(CurrencyPair.BTC_USDT);
      service.getHitbtcRecentOrders();
      service.getHitbtcRecentOrders("BTCUSDT", "client-1", "2024-01-01T00:00:00Z", "2024-02-01T00:00:00Z", 20, 10L);
      assertEquals(4, requests.size());
      assertEquals(Collections.nCopies(4, "GET"), methods);
      String credentials =
          Base64.getEncoder()
              .encodeToString("test-key:test-secret".getBytes(StandardCharsets.UTF_8));
      assertEquals(Collections.nCopies(4, "Basic " + credentials), auth);
      assertEquals("/api/2/order", requests.get(0).getPath());
      assertTrue(query(requests.get(0)).isEmpty());
      assertEquals("/api/2/order", requests.get(1).getPath());
      assertEquals(Collections.singletonMap("symbol", "BTCUSDT"), query(requests.get(1)));
      assertEquals("/api/2/history/order", requests.get(2).getPath());
      assertTrue(query(requests.get(2)).isEmpty());
      assertEquals("/api/2/history/order", requests.get(3).getPath());
      Map<String, String> expected = new HashMap<>();
      expected.put("symbol", "BTCUSDT");
      expected.put("clientOrderId", "client-1");
      expected.put("from", "2024-01-01T00:00:00Z");
      expected.put("till", "2024-02-01T00:00:00Z");
      expected.put("limit", "20");
      expected.put("offset", "10");
      assertEquals(expected, query(requests.get(3)));
    } finally {
      server.stop(0);
    }
  }

  private Map<String, String> query(URI uri) {
    Map<String, String> result = new HashMap<>();
    if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
      for (String parameter : uri.getRawQuery().split("&")) {
        String[] keyValue = parameter.split("=", 2);
        result.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
            URLDecoder.decode(keyValue.length == 2 ? keyValue[1] : "", StandardCharsets.UTF_8));
      }
    }
    return result;
  }
}
