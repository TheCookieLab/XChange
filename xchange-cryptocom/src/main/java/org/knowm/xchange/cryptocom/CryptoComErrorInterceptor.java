package org.knowm.xchange.cryptocom;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import si.mazi.rescu.Interceptor;

/**
 * Converts every Crypto.com-specific error - a {@code 200 OK} envelope with a non-zero {@code
 * code}, or a non-2xx HTTP status deserialized into a {@link CryptoComException} - into the
 * matching XChange {@link org.knowm.xchange.exceptions.ExchangeException} here, at the single choke
 * point every REST call passes through, so no caller (raw or otherwise) ever sees the
 * exchange-specific exception type.
 *
 * <p>Every provider error surfaces as a structured {@link
 * org.knowm.xchange.cryptocom.dto.CryptoComRequestException} carrying the envelope request id
 * (first-class on every private call), the API method, provider code/message, HTTP status and
 * retry classification; the dedicated XChange exception types (rate limit, nonce, funds) remain
 * available through the context-free {@code CryptoComErrorAdapter.adaptError} overloads.
 */
public class CryptoComErrorInterceptor implements Interceptor {

  @Override
  public Object aroundInvoke(
      InvocationHandler invocationHandler, Object proxy, Method method, Object[] args)
      throws Throwable {
    long requestId = extractRequestId(args);
    String apiMethod = extractApiMethod(args, method.getName());

    Object result;
    try {
      result = invocationHandler.invoke(proxy, method, args);
    } catch (CryptoComException e) {
      throw CryptoComErrorAdapter.adaptError(e, requestId, apiMethod);
    }

    if (result instanceof CryptoComResponse) {
      CryptoComResponse response = (CryptoComResponse) result;
      if (response.getCode() != 0) {
        throw CryptoComErrorAdapter.adaptError(response, requestId, apiMethod);
      }
    }

    return result;
  }

  private static long extractRequestId(Object[] args) {
    if (args != null) {
      for (Object arg : args) {
        if (arg instanceof CryptoComRequest) {
          return ((CryptoComRequest) arg).getId();
        }
      }
    }
    return 0;
  }

  /** Prefers the wire method carried by the request envelope; falls back to the Java method name. */
  private static String extractApiMethod(Object[] args, String javaMethodName) {
    if (args != null) {
      for (Object arg : args) {
        if (arg instanceof CryptoComRequest) {
          String apiMethod = ((CryptoComRequest) arg).getMethod();
          if (apiMethod != null && !apiMethod.isEmpty()) {
            return apiMethod;
          }
        }
      }
    }
    return javaMethodName;
  }
}