package org.knowm.xchange.client.ratelimit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable logical request handed to a policy classifier. It carries only what is needed to
 * classify cost, scope, priority and replay safety, never a signed or timestamped HTTP request.
 *
 * @since 1.0.3
 */
public final class RateLimitRequest {

  private final String operationKey;
  private final boolean authenticated;
  private final Map<String, String> parameters;

  /**
   * Creates a logical request without classification parameters.
   *
   * @param operationKey operation identity such as {@code "GET market/products"} or a JSON-RPC
   *     method name; must not contain credentials
   * @param authenticated whether the provider treats the request as authenticated for quota
   *     purposes (not merely whether a credential is installed)
   */
  public RateLimitRequest(String operationKey, boolean authenticated) {
    this(operationKey, authenticated, Collections.emptyMap());
  }

  /**
   * Creates a logical request whose cost depends on request parameters, for example a provider
   * weight that grows with an order-book {@code limit}.
   *
   * @param operationKey operation identity such as {@code "GET market/products"} or a JSON-RPC
   *     method name; must not contain credentials
   * @param authenticated whether the provider treats the request as authenticated for quota
   *     purposes (not merely whether a credential is installed)
   * @param parameters non-credential request parameters by wire name (query, form or path
   *     parameters); {@code null} values are dropped. The map is copied.
   * @since 1.0.3
   */
  public RateLimitRequest(
      String operationKey, boolean authenticated, Map<String, String> parameters) {
    this.operationKey = Objects.requireNonNull(operationKey, "operationKey");
    this.authenticated = authenticated;
    Objects.requireNonNull(parameters, "parameters");
    Map<String, String> copy = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    this.parameters = Collections.unmodifiableMap(copy);
  }

  /**
   * @return the operation identity
   */
  public String getOperationKey() {
    return operationKey;
  }

  /**
   * @return whether the provider's quota rules treat this request as authenticated
   */
  public boolean isAuthenticated() {
    return authenticated;
  }

  /**
   * @param name wire name of a query, form or path parameter
   * @return the parameter's string value, or {@code null} when absent
   * @since 1.0.3
   */
  public String getParameter(String name) {
    return parameters.get(name);
  }

  /**
   * @return the immutable classification parameters by wire name
   * @since 1.0.3
   */
  public Map<String, String> getParameters() {
    return parameters;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RateLimitRequest)) {
      return false;
    }
    RateLimitRequest other = (RateLimitRequest) o;
    return authenticated == other.authenticated
        && operationKey.equals(other.operationKey)
        && parameters.equals(other.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operationKey, authenticated, parameters);
  }

  /** Parameter values are omitted: they may carry order or account details. */
  @Override
  public String toString() {
    return "RateLimitRequest{operationKey="
        + operationKey
        + ", authenticated="
        + authenticated
        + ", parameterNames="
        + parameters.keySet()
        + '}';
  }
}
