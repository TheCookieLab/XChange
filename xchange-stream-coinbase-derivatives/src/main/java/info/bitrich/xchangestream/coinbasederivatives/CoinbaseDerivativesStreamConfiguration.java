package info.bitrich.xchangestream.coinbasederivatives;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Immutable lifecycle configuration for the Coinbase derivatives WebSocket session. */
public final class CoinbaseDerivativesStreamConfiguration {

  /** Provider-supported Cancel on Disconnect scope. */
  public enum CancelOnDisconnectScope {
    CONNECTION("connection"),
    ACCOUNT("account");

    private final String wireValue;

    CancelOnDisconnectScope(String wireValue) {
      this.wireValue = wireValue;
    }

    String wireValue() {
      return wireValue;
    }
  }

  private final Supplier<String> jwtSupplier;
  private final Duration authenticationLifetime;
  private final Duration reauthenticationLeadTime;
  private final boolean cancelOnDisconnect;
  private final CancelOnDisconnectScope cancelOnDisconnectScope;

  public CoinbaseDerivativesStreamConfiguration(Supplier<String> jwtSupplier) {
    this(
        jwtSupplier,
        Duration.ofMinutes(50),
        Duration.ofMinutes(5),
        false,
        CancelOnDisconnectScope.CONNECTION);
  }

  public CoinbaseDerivativesStreamConfiguration(
      Supplier<String> jwtSupplier,
      Duration authenticationLifetime,
      Duration reauthenticationLeadTime,
      boolean cancelOnDisconnect,
      CancelOnDisconnectScope cancelOnDisconnectScope) {
    this.jwtSupplier = jwtSupplier;
    this.authenticationLifetime = Objects.requireNonNull(authenticationLifetime);
    this.reauthenticationLeadTime = Objects.requireNonNull(reauthenticationLeadTime);
    if (authenticationLifetime.isNegative()
        || authenticationLifetime.isZero()
        || reauthenticationLeadTime.isNegative()
        || reauthenticationLeadTime.compareTo(authenticationLifetime) >= 0) {
      throw new IllegalArgumentException(
          "Reauthentication lead time must be within the authentication lifetime");
    }
    this.cancelOnDisconnect = cancelOnDisconnect;
    this.cancelOnDisconnectScope = Objects.requireNonNull(cancelOnDisconnectScope);
  }

  Supplier<String> jwtSupplier() {
    return jwtSupplier;
  }

  Duration reauthenticationDelay() {
    return authenticationLifetime.minus(reauthenticationLeadTime);
  }

  public boolean isCancelOnDisconnect() {
    return cancelOnDisconnect;
  }

  public CancelOnDisconnectScope getCancelOnDisconnectScope() {
    return cancelOnDisconnectScope;
  }
}
