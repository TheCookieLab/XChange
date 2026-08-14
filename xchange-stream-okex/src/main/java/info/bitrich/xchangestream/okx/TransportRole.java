package info.bitrich.xchangestream.okx;

/**
 * The three websocket transports of the OKX streaming API.
 *
 * <p>Used by {@link OkxStreamingExchange} to model which transports must be connected and healthy
 * for the exchange to be considered {@linkplain OkxStreamingExchange#isAlive() alive}: the public
 * market-data socket, the authenticated private socket, and the business socket.
 */
public enum TransportRole {

  /** The public market-data websocket ({@code /ws/v5/public}). Always required. */
  PUBLIC,

  /** The authenticated websocket ({@code /ws/v5/private}) carrying trading and account streams. */
  PRIVATE,

  /**
   * The business websocket ({@code /ws/v5/business}) carrying business channels such as candles.
   */
  BUSINESS
}
