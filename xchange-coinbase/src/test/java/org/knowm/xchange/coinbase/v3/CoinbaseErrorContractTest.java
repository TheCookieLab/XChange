package org.knowm.xchange.coinbase.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.RetryClassification;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.service.CoinbaseTradeServiceRaw;
import si.mazi.rescu.ParamsDigest;

/** Deterministic tests for structured error classification and ambiguous placement handling. */
public class CoinbaseErrorContractTest {

  @Test
  public void httpStatusCodesClassifyForRetryPolicy() {
    assertEquals(RetryClassification.AUTHENTICATION, CoinbaseException_classify(401));
    assertEquals(RetryClassification.AUTHENTICATION, CoinbaseException_classify(403));
    assertEquals(RetryClassification.RATE_CREDIT, CoinbaseException_classify(429));
    assertEquals(RetryClassification.TRANSIENT, CoinbaseException_classify(500));
    assertEquals(RetryClassification.TRANSIENT, CoinbaseException_classify(503));
    assertEquals(RetryClassification.PERMANENT, CoinbaseException_classify(400));
    assertEquals(RetryClassification.PERMANENT, CoinbaseException_classify(422));
  }

  @Test
  public void errorShapeDeserializesWithProviderCodeAndClassifiesByStatus() throws Exception {
    org.knowm.xchange.coinbase.v3.dto.CoinbaseException failure =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(
                "{\"errors\":[{\"id\":\"RATE_LIMIT_REACHED\",\"message\":\"slow down\"}]}",
                org.knowm.xchange.coinbase.v3.dto.CoinbaseException.class);
    failure.setHttpStatusCode(429);
    assertEquals("RATE_LIMIT_REACHED", failure.getErrors().get(0).id);
    assertEquals("slow down", failure.getErrors().get(0).message);
    assertEquals(RetryClassification.RATE_CREDIT, failure.getRetryClassification());
  }

  @Test
  public void createOrderTransportFailureIsAmbiguousAndNeverReplayed() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.createOrder(any(ParamsDigest.class), any()))
        .thenThrow(new IOException("connection reset"));

    CoinbaseTradeServiceRaw service =
        new CoinbaseTradeServiceRaw(mock(Exchange.class), authenticated, mock(ParamsDigest.class));

    CoinbaseOrderRequest request = new CoinbaseOrderRequest(
        "client-order-1", "BTC-USD", null, null, null, null, null, null, null, null, null);
    try {
      service.createOrder(request);
      throw new AssertionError("expected CoinbaseUnknownOutcomeException");
    } catch (CoinbaseUnknownOutcomeException unknown) {
      assertEquals(RetryClassification.AMBIGUOUS, unknown.getRetryClassification());
      assertEquals("createOrder", unknown.getOperation());
      assertEquals("client-order-1", unknown.getClientOrderId());
      assertTrue(unknown.getMessage().contains("do not replay"));
      assertFalse(unknown.getMessage().contains("secret"));
    }
  }

  @Test
  public void editOrderTransportFailureIsAmbiguous() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.editOrderCurrent(any(ParamsDigest.class), any()))
        .thenThrow(new IOException("read timed out"));

    CoinbaseTradeServiceRaw service =
        new CoinbaseTradeServiceRaw(mock(Exchange.class), authenticated, mock(ParamsDigest.class));

    CoinbaseEditOrderRequest request =
        new CoinbaseEditOrderRequest("order-42", null, null, null, null, null);
    try {
      service.editOrderCurrent(request);
      throw new AssertionError("expected CoinbaseUnknownOutcomeException");
    } catch (CoinbaseUnknownOutcomeException unknown) {
      assertEquals(RetryClassification.AMBIGUOUS, unknown.getRetryClassification());
      assertEquals(Collections.singletonList("order-42"), unknown.getOrderIds());
      assertTrue(unknown.getClientOrderIds().isEmpty());
      assertEquals(null, unknown.getClientOrderId());
    }
  }

  @Test
  public void unknownOutcomeExceptionIsAnIOExceptionForCallerCompatibility() {
    CoinbaseUnknownOutcomeException exception =
        new CoinbaseUnknownOutcomeException(
            "createOrder", "c-1", new IOException("boom"));
    assertTrue(exception instanceof IOException);
  }

  @Test
  public void nonOrderCorrelationDoesNotClaimClientOrderIdentity() {
    CoinbaseUnknownOutcomeException exception =
        new CoinbaseUnknownOutcomeException(
            "commitConvertTrade", "trade_id", "trade-42", new IOException("boom"));

    assertEquals("trade_id", exception.getCorrelationName());
    assertEquals("trade-42", exception.getCorrelationId());
    assertTrue(exception.getOrderIds().isEmpty());
    assertTrue(exception.getClientOrderIds().isEmpty());
  }

  private static RetryClassification CoinbaseException_classify(int status) {
    return org.knowm.xchange.coinbase.v3.dto.CoinbaseException.classify(status);
  }
}
