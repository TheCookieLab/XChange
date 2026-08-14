package org.knowm.xchange.okex;

import org.knowm.xchange.client.ResilienceRegistries;

/**
 * @deprecated use {@link org.knowm.xchange.okx.OkxResilience} instead.
 */
@Deprecated
public class OkexResilience {

  private OkexResilience() {}

  /**
   * Delegates to the canonical {@link org.knowm.xchange.okx.OkxResilience#createRegistries()}.
   *
   * @deprecated use {@link org.knowm.xchange.okx.OkxResilience#createRegistries()} instead.
   */
  @Deprecated
  public static ResilienceRegistries createRegistries() {
    return org.knowm.xchange.okx.OkxResilience.createRegistries();
  }
}
