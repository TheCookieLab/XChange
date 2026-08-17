package org.knowm.xchange.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComRequestException;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.CryptoComRetryClass;

/** Exercises the single choke point every REST call passes through. */
public class CryptoComErrorInterceptorTest {

  private final CryptoComErrorInterceptor interceptor = new CryptoComErrorInterceptor();

  @Test
  public void successResponse_passesThrough() throws Throwable {
    CryptoComResponse response = new CryptoComResponse();
    response.setCode(0);

    Object result = invoke(response, requestFor("private/user-balance"));

    assertThat(result).isSameAs(response);
  }

  @Test
  public void envelopeWithErrorCode_becomesStructuredExceptionWithRequestContext() throws Throwable {
    CryptoComResponse error = new CryptoComResponse();
    error.setId(77); // response echoes the request id; the interceptor uses the request's id
    error.setCode(10209);
    error.setMessage("insufficient balance");
    CryptoComRequest request = requestFor("private/create-order");

    assertThatThrownBy(() -> invoke((Object) error, request))
        .isInstanceOf(CryptoComRequestException.class)
        .satisfies(
            e -> {
              CryptoComRequestException ex = (CryptoComRequestException) e;
              assertThat(ex.getRequestId()).isEqualTo(41);
              assertThat(ex.getMethod()).isEqualTo("private/create-order");
              assertThat(ex.getProviderCode()).isEqualTo(10209);
              assertThat(ex.getProviderMessage()).isEqualTo("insufficient balance");
              assertThat(ex.getTransport()).isNotNull();
            });
  }

  @Test
  public void envelopeErrorRateLimit_becomesStructuredWithRetryClassRateLimit() throws Throwable {
    CryptoComResponse error = new CryptoComResponse();
    error.setCode(CryptoComErrorAdapter.TOO_MANY_REQUESTS);
    error.setMessage("rate limit");

    assertThatThrownBy(() -> invoke((Object) error, requestFor("private/get-order-history")))
        .isInstanceOf(CryptoComRequestException.class)
        .satisfies(
            e -> {
              CryptoComRequestException ex = (CryptoComRequestException) e;
              assertThat(ex.getProviderCode()).isEqualTo(CryptoComErrorAdapter.TOO_MANY_REQUESTS);
              assertThat(ex.getRetryClass()).isEqualTo(CryptoComRetryClass.RATE_LIMIT);
            });
  }

  @Test
  public void httpStatusException_unmapped_becomesStructuredWithHttpStatus() throws Throwable {
    CryptoComException exception = new CryptoComException(10002, "bad request");
    InvocationHandler handler =
        (proxy, method, args) -> {
          throw exception;
        };

    assertThatThrownBy(() -> invokeWithHandler(handler, requestFor("private/cancel-order")))
        .isInstanceOf(CryptoComRequestException.class)
        .satisfies(
            e -> {
              CryptoComRequestException ex = (CryptoComRequestException) e;
              assertThat(ex.getMethod()).isEqualTo("private/cancel-order");
              assertThat(ex.getProviderCode()).isEqualTo(10002);
            });
  }

  @Test
  public void httpStatusException_knownCode_becomesStructuredWithRejectedRetryClass()
      throws Throwable {
    CryptoComException exception =
        new CryptoComException(CryptoComErrorAdapter.INSUFFICIENT_AVAILABLE_BALANCE, "nope");
    InvocationHandler handler =
        (proxy, method, args) -> {
          throw exception;
        };

    assertThatThrownBy(() -> invokeWithHandler(handler, requestFor("private/create-order")))
        .isInstanceOf(CryptoComRequestException.class)
        .satisfies(
            e -> {
              CryptoComRequestException ex = (CryptoComRequestException) e;
              assertThat(ex.getProviderCode())
                  .isEqualTo(CryptoComErrorAdapter.INSUFFICIENT_AVAILABLE_BALANCE);
              assertThat(ex.getRetryClass()).isEqualTo(CryptoComRetryClass.REJECTED);
            });
  }

  /**
   * Drives the interceptor with a handler that throws the given error (HTTP-status path, where
   * rescu deserializes the non-2xx body into a {@link CryptoComException}).
   */
  private Object invokeWithHandler(InvocationHandler handler, CryptoComRequest request)
      throws Throwable {
    return interceptor.aroundInvoke(
        handler, mock(Object.class), methodOf("any"), new Object[] {request});
  }

  private Object invoke(Object result, CryptoComRequest request) throws Throwable {
    InvocationHandler handler = (proxy, method, args) -> result;
    return interceptor.aroundInvoke(handler, mock(Object.class), methodOf("any"), new Object[] {request});
  }

  private static Method methodOf(String name) throws Throwable {
    for (Method m : CryptoCom.class.getDeclaredMethods()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    return CryptoCom.class.getDeclaredMethods()[0];
  }

  private CryptoComRequest requestFor(String method) {
    CryptoComRequest request = new CryptoComRequest();
    request.setId(41);
    request.setMethod(method);
    return request;
  }
}