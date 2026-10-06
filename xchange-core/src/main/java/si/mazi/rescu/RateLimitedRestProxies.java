package si.mazi.rescu;

import java.lang.reflect.InvocationHandler;
import java.util.Objects;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import si.mazi.rescu.clients.HttpConnectionType;

/**
 * Creates rescu proxies whose every call is an admitted, single-attempt operation of a {@link
 * RateLimitContext}. It lives in the rescu package because rescu offers no public way to install an
 * invocation handler; it is used only by {@code ExchangeRestProxyBuilder}.
 *
 * <p>Interceptors wrap the rate-limited handler, so they can observe but never bypass admission.
 *
 * @since 1.0.3
 */
public final class RateLimitedRestProxies {

  private RateLimitedRestProxies() {}

  /**
   * Creates a rate-limited proxy.
   *
   * @param restInterface the JAX-RS annotated interface
   * @param baseUrl the service base URL
   * @param config client configuration; must use the {@code java} connection type and no custom
   *     SSL socket factory, hostname verifier, OAuth consumer or non-HTTP proxy
   * @param policy the rate-limit policy that classifies operations
   * @param context the context enforcing the policy
   * @param userScope opaque user binding, or {@code null} for the context's conservative default
   * @param interceptors interceptors wrapped around the rate-limited handler
   * @param <I> the interface type
   * @return the proxy
   * @throws IllegalStateException if {@code config} selects a connection type or setting that
   *     cannot be held to one wire attempt per admission
   */
  public static <I> I createProxy(
      Class<I> restInterface,
      String baseUrl,
      ClientConfig config,
      RateLimitPolicy policy,
      RateLimitContext context,
      String userScope,
      Interceptor... interceptors) {
    Objects.requireNonNull(restInterface, "restInterface");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(context, "context");
    if (config.getConnectionType() != HttpConnectionType.java) {
      throw new IllegalStateException(
          "Rate-limited proxies require connectionType=java (the "
              + config.getConnectionType()
              + " client retries and follows redirects internally) for "
              + restInterface.getName());
    }
    InvocationHandler handler =
        new RateLimitedInvocationHandler(restInterface, baseUrl, config, policy, context, userScope);
    return RestProxyFactory.createProxy(restInterface, handler, interceptors);
  }
}
