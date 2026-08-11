package org.knowm.xchange.bitget.config;

/**
 * API/account mode of a Bitget exchange instance.
 *
 * <p>Bitget operates two incompatible account/API generations:
 *
 * <ul>
 *   <li>{@link #CLASSIC_V2}: the legacy Spot/Futures account and the v2 REST/WebSocket APIs, served
 *       by {@code xchange-bitget} and {@code xchange-bitget-futures}.
 *   <li>{@link #UTA_V3}: the Unified Trading Account (UTA) and the v3 REST/WebSocket APIs.
 * </ul>
 *
 * <p>Credentials, REST endpoints, product catalogs, account semantics, and streaming transports
 * differ between the modes. The selected mode owns REST clients, authentication validation,
 * metadata initialization, account/trade service implementations, and streaming transport
 * selection. Classic remains the compatibility-period default so existing consumers upgrade without
 * an involuntary account-mode migration.
 */
public enum BitgetApiMode {

  /** Legacy Spot/Futures account and v2 API generation (default). */
  CLASSIC_V2("classic", "v2"),

  /** Unified Trading Account (UTA) and v3 API generation. */
  UTA_V3("uta", "v3");

  private final String accountLabel;
  private final String apiGeneration;

  BitgetApiMode(String accountLabel, String apiGeneration) {
    this.accountLabel = accountLabel;
    this.apiGeneration = apiGeneration;
  }

  /** Short account label used in diagnostics, e.g. {@code classic} or {@code uta}. */
  public String getAccountLabel() {
    return accountLabel;
  }

  /** API generation label used in diagnostics, e.g. {@code v2} or {@code v3}. */
  public String getApiGeneration() {
    return apiGeneration;
  }
}
