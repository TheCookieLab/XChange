package org.knowm.xchange.uniswap.signing;

/**
 * Supplies the keystore password at signing time through a non-persisted boundary.
 *
 * <p>Implementations must never log, persist, or embed the password. Callers zero the returned
 * array after use.
 */
public interface SecretProvider {

  /** Returns a fresh copy of the keystore password; the caller clears it after use. */
  char[] password();
}
