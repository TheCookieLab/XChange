package org.knowm.xchange.client.ratelimit;

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

  /**
   * Creates a logical request.
   *
   * @param operationKey operation identity such as {@code "GET market/products"} or a JSON-RPC
   *     method name; must not contain credentials
   * @param authenticated whether the provider treats the request as authenticated for quota
   *     purposes (not merely whether a credential is installed)
   */
  public RateLimitRequest(String operationKey, boolean authenticated) {
    this.operationKey = Objects.requireNonNull(operationKey, "operationKey");
    this.authenticated = authenticated;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RateLimitRequest)) {
      return false;
    }
    RateLimitRequest other = (RateLimitRequest) o;
    return authenticated == other.authenticated && operationKey.equals(other.operationKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operationKey, authenticated);
  }

  @Override
  public String toString() {
    return "RateLimitRequest{operationKey=" + operationKey + ", authenticated=" + authenticated + '}';
  }
}
