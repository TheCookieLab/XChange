package org.knowm.xchange.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.interceptor.InterceptorProvider;
import si.mazi.rescu.ClientConfig;
import si.mazi.rescu.IRestProxyFactory;
import si.mazi.rescu.Interceptor;
import si.mazi.rescu.RateLimitedRestProxies;
import si.mazi.rescu.RestProxyFactoryImpl;
import si.mazi.rescu.clients.HttpConnectionType;

public final class ExchangeRestProxyBuilder<T> {

  private final Class<T> restInterface;
  private final ExchangeSpecification exchangeSpecification;
  private final List<Interceptor> customInterceptors = new ArrayList<>();
  private final List<ClientConfigCustomizer> clientConfigCustomizers = new ArrayList<>();
  private ClientConfig clientConfig;
  private ResilienceRegistries resilienceRegistries;
  private String baseUrl;
  private IRestProxyFactory restProxyFactory = new RestProxyFactoryImpl();
  private boolean customRestProxyFactory;

  private ExchangeRestProxyBuilder(
      Class<T> restInterface, ExchangeSpecification exchangeSpecification) {
    this.restInterface = restInterface;
    this.exchangeSpecification = exchangeSpecification;
    this.baseUrl =
        Optional.ofNullable(exchangeSpecification.getSslUri())
            .orElseGet(exchangeSpecification::getPlainTextUri);
  }

  public static <T> ExchangeRestProxyBuilder<T> forInterface(
      Class<T> restInterface, ExchangeSpecification exchangeSpecification) {
    return new ExchangeRestProxyBuilder<>(restInterface, exchangeSpecification)
        .customInterceptors(InterceptorProvider.provide());
  }

  public ExchangeRestProxyBuilder<T> clientConfig(ClientConfig value) {
    this.clientConfig = value;
    return this;
  }

  public ExchangeRestProxyBuilder<T> clientConfigCustomizer(
      ClientConfigCustomizer clientConfigCustomizer) {
    this.clientConfigCustomizers.add(clientConfigCustomizer);
    return this;
  }

  public ExchangeRestProxyBuilder<T> baseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  public ExchangeRestProxyBuilder<T> customInterceptor(Interceptor value) {
    this.customInterceptors.add(value);
    return this;
  }

  public ExchangeRestProxyBuilder<T> customInterceptors(Collection<Interceptor> interceptors) {
    customInterceptors.addAll(interceptors);
    return this;
  }

  public ExchangeRestProxyBuilder<T> restProxyFactory(IRestProxyFactory restProxyFactory) {
    this.restProxyFactory = restProxyFactory;
    this.customRestProxyFactory = true;
    return this;
  }

  /**
   * Builds the proxy. When the specification's resilience settings enable rate limiting and carry a
   * policy, every call of the proxy is admitted by the specification's {@link RateLimitContext}
   * (see {@link RateLimitedRestProxies}); custom interceptors wrap that admission and cannot
   * bypass it. Otherwise the proxy is a plain rescu proxy.
   *
   * @return the proxy
   * @throws IllegalStateException if rate limiting is enabled but cannot be enforced for this
   *     builder: a custom {@link #restProxyFactory} or the {@code apache} connection type (neither
   *     can be held to one wire attempt per admission), or no context is available
   */
  public T build() {
    if (clientConfig == null) {
      clientConfig = createClientConfig(exchangeSpecification);
    }
    if (resilienceRegistries == null) {
      resilienceRegistries = new ResilienceRegistries();
    }
    clientConfigCustomizers.forEach(
        clientConfigCustomizer -> clientConfigCustomizer.customize(clientConfig));
    Interceptor[] interceptors = customInterceptors.toArray(new Interceptor[0]);
    ExchangeSpecification.ResilienceSpecification resilience = exchangeSpecification.getResilience();
    if (resilience != null && resilience.isRateLimiterEnabled() && resilience.getRateLimitPolicy() != null) {
      return buildRateLimited(resilience, interceptors);
    }
    return restProxyFactory.createProxy(restInterface, baseUrl, clientConfig, interceptors);
  }

  private T buildRateLimited(
      ExchangeSpecification.ResilienceSpecification resilience, Interceptor[] interceptors) {
    RateLimitPolicy policy = resilience.getRateLimitPolicy();
    RateLimitContext context = resilience.getRateLimitContext();
    if (customRestProxyFactory) {
      throw new IllegalStateException(
          "Rate limiting is enabled for policy "
              + policy.getNamespace()
              + " but a custom IRestProxyFactory was set for "
              + restInterface.getName()
              + "; it would bypass admission. Remove the factory or disable the rate limiter.");
    }
    if (clientConfig.getConnectionType() != HttpConnectionType.java) {
      throw new IllegalStateException(
          "Rate limiting is enabled for policy "
              + policy.getNamespace()
              + " but connection type "
              + clientConfig.getConnectionType()
              + " cannot be held to one wire attempt per admission for "
              + restInterface.getName()
              + "; use the java connection type or disable the rate limiter.");
    }
    if (context == null) {
      throw new IllegalStateException(
          "Rate limiting is enabled for policy "
              + policy.getNamespace()
              + " but the specification has no rate-limit context; apply the specification to an"
              + " exchange first or set one.");
    }
    return RateLimitedRestProxies.createProxy(
        restInterface,
        baseUrl,
        clientConfig,
        policy,
        context,
        resilience.getRateLimitUserScope(),
        interceptors);
  }

  /**
   * Get a ClientConfig object which contains exchange-specific timeout values
   * (<i>httpConnTimeout</i> and <i>httpReadTimeout</i>) if they were present in the
   * ExchangeSpecification of this instance.
   *
   * @return a rescu client config object
   */
  public static ClientConfig createClientConfig(ExchangeSpecification exchangeSpecification) {

    ClientConfig rescuConfig = new ClientConfig(); // create default rescu config

    // set per exchange connection- and read-timeout (if they have been set in the
    // ExchangeSpecification)
    int customHttpConnTimeout = exchangeSpecification.getHttpConnTimeout();
    if (customHttpConnTimeout > 0) {
      rescuConfig.setHttpConnTimeout(customHttpConnTimeout);
    }
    int customHttpReadTimeout = exchangeSpecification.getHttpReadTimeout();
    if (customHttpReadTimeout > 0) {
      rescuConfig.setHttpReadTimeout(customHttpReadTimeout);
    }
    if (exchangeSpecification.getProxyHost() != null) {
      rescuConfig.setProxyHost(exchangeSpecification.getProxyHost());
    }
    if (exchangeSpecification.getProxyPort() != null) {
      rescuConfig.setProxyPort(exchangeSpecification.getProxyPort());
    }
    return rescuConfig;
  }
}
