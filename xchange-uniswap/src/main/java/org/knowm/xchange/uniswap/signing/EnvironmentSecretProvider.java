package org.knowm.xchange.uniswap.signing;

/**
 * Reads the keystore password from the {@code UNISWAP_KEYSTORE_PASSWORD} environment variable or
 * the {@code uniswap.keystore.password} system property. The environment variable wins when both
 * are set.
 */
public final class EnvironmentSecretProvider implements SecretProvider {

  public static final String ENV_VAR = "UNISWAP_KEYSTORE_PASSWORD";
  public static final String SYSTEM_PROPERTY = "uniswap.keystore.password";

  @Override
  public char[] password() {
    String value = System.getenv(ENV_VAR);
    if (value == null) {
      value = System.getProperty(SYSTEM_PROPERTY);
    }
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException(
          "keystore password is not configured: set " + ENV_VAR + " or -D" + SYSTEM_PROPERTY);
    }
    return value.toCharArray();
  }
}
