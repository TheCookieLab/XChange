package org.knowm.xchange.yobit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.utils.nonce.AtomicLongIncrementalTime2014NonceFactory;
import org.knowm.xchange.yobit.YoBitExchange;

class YoBitDepositAddressContractTest {
  @Test
  void defaultsToExistingAddressAndSerializesExplicitChoiceAsInteger() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    List<String> requests = new CopyOnWriteArrayList<>();
    server.createContext("/tapi", http -> {
      requests.add(new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"success\":1,\"return\":{\"address\":\"test-address\"}}".getBytes(StandardCharsets.UTF_8);
      http.getResponseHeaders().add("Content-Type", "application/json");
      http.sendResponseHeaders(200, response.length);
      http.getResponseBody().write(response);
      http.close();
    });
    server.start();
    try {
      YoBitExchange exchange = mock(YoBitExchange.class);
      ExchangeSpecification specification = new YoBitExchange().getDefaultExchangeSpecification();
      specification.setSslUri("http://127.0.0.1:" + server.getAddress().getPort());
      specification.setHost("127.0.0.1");
      specification.setPort(server.getAddress().getPort());
      specification.setApiKey("test-key");
      specification.setSecretKey("test-secret");
      when(exchange.getExchangeSpecification()).thenReturn(specification);
      when(exchange.getNonceFactory()).thenReturn(new AtomicLongIncrementalTime2014NonceFactory());
      YoBitAccountService service = new YoBitAccountService(exchange);
      assertEquals("test-address", service.requestDepositAddress(Currency.BTC));
      assertEquals("test-address", service.requestDepositAddress(Currency.BTC, "false"));
      assertEquals("test-address", service.requestDepositAddress(Currency.BTC, "true"));
      service.service.getDepositAddress(
          specification.getApiKey(), service.signatureCreator, "GetDepositAddress",
          exchange.getNonceFactory(), "BTC", null);
      service.getDepositAddress(Currency.BTC);
      assertThrows(
          IllegalArgumentException.class,
          () -> service.requestDepositAddress(Currency.BTC, "invalid"));
      assertEquals(5, requests.size());
      assertTrue(requests.get(0).contains("need_new=0"));
      assertTrue(requests.get(1).contains("need_new=0"));
      assertTrue(requests.get(2).contains("need_new=1"));
      assertFalse(requests.get(3).contains("need_new="));
      assertTrue(requests.get(4).contains("need_new=0"));
      for (String request : requests) {
        assertTrue(request.contains("method=GetDepositAddress"));
        assertTrue(request.contains("coinName=BTC"));
      }
    } finally {
      server.stop(0);
    }
  }
}
