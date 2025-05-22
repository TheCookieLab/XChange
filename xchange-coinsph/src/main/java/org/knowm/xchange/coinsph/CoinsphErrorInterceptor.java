package org.knowm.xchange.coinsph;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.knowm.xchange.coinsph.dto.CoinsphResponse;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import si.mazi.rescu.Interceptor;

public class CoinsphErrorInterceptor implements Interceptor {
  @Override
  public Object aroundInvoke(
      InvocationHandler invocationHandler, Object proxy, Method method, Object[] args)
      throws Throwable {
    Object result = invocationHandler.invoke(proxy, method, args);

    if (result instanceof CoinsphResponse) {
      CoinsphResponse response = (CoinsphResponse) result;
      if (response.getCode() < 0) {
        handleErrorResponse(response);
      }
    }

    return result;
  }

  private void handleErrorResponse(CoinsphResponse response) {
    switch (response.getCode()) {
      case -10112:
      case -1131:
        throw new FundsExceededException(response.getMessage());
      case -1003:
        throw new RateLimitExceededException(response.getMessage());
    }
    throw new ExchangeException(
        String.format("Coinsph code: %d error: %s", response.getCode(), response.getMessage()));
  }
}
