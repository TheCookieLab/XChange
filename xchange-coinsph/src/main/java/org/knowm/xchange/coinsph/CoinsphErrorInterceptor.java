package org.knowm.xchange.coinsph;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.knowm.xchange.coinsph.dto.CoinsphResponse;
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
        throw CoinsphErrorAdapter.adaptError(response);
      }
    }

    return result;
  }
}
