package org.knowm.xchange.kucoin.uta.dto;

/**
 * UTA trade type discriminator used across REST and WebSocket APIs.
 *
 * <p>UTA Spot and UTA Futures are the stable Phase 1 product lines; UTA Margin is exposed by the
 * unified order endpoints but is not yet covered by streaming.
 */
public enum UtaTradeType {
  SPOT,
  FUTURES,
  MARGIN;

  /** UTA private WebSocket subscriptions use {@code UNIFIED} to cover all trade types. */
  public static final String UNIFIED = "UNIFIED";
}
