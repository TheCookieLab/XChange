package org.knowm.xchange.cryptocom.dto;

/** Transport a Crypto.com request/response travelled over; used for structured error context. */
public enum CryptoComTransport {
  /** Synchronous REST (HTTP) call. */
  REST,

  /** WebSocket (streaming) connection. */
  WEBSOCKET
}