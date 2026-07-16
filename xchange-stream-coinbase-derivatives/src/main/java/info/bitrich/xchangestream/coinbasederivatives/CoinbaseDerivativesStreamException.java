package info.bitrich.xchangestream.coinbasederivatives;

/** Base exception for sanitized Coinbase derivatives WebSocket protocol failures. */
public class CoinbaseDerivativesStreamException extends RuntimeException {

  public CoinbaseDerivativesStreamException(String message) {
    super(message);
  }

  public CoinbaseDerivativesStreamException(String message, Throwable cause) {
    super(message, cause);
  }
}
