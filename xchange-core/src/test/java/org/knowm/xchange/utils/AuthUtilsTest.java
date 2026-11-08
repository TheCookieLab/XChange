package org.knowm.xchange.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.opentest4j.TestAbortedException;

public class AuthUtilsTest {

  private static final String PREFIX = "authutils-test";
  private static final String FILE_API_KEY = "file-api-key";
  private static final String FILE_SECRET_KEY = "file-secret-key";

  @Test
  public void testPathTraversalDotDotRejected() {
    assertThrows(IllegalArgumentException.class, () -> AuthUtils.getSecretProperties("../secret"));
  }

  @Test
  public void testPathTraversalSlashRejected() {
    assertThrows(IllegalArgumentException.class, () -> AuthUtils.getSecretProperties("sub/dir"));
  }

  @Test
  public void testPathTraversalBackslashRejected() {
    assertThrows(IllegalArgumentException.class, () -> AuthUtils.getSecretProperties("sub\\dir"));
  }

  @Test
  public void testPathTraversalSpaceRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> AuthUtils.getSecretProperties("invalid prefix"));
  }

  @Test
  public void testPathTraversalNullByteRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> AuthUtils.getSecretProperties("prefix\0suffix"));
  }

  @Test
  public void testSecretPropertiesStreamClosureAndNonExistentHandling() {
    // Non-existent prefix should cleanly return null without unhandled exceptions or leaks
    org.junit.jupiter.api.Assertions.assertNull(
        AuthUtils.getSecretProperties("non-existent-prefix-12345"));
  }

  @Test
  public void testSecretKeysFallbackWhenEnvMissing() {
    assumeSecretKeysAvailable(PREFIX);

    String host = "authutils-test-host";
    while (System.getenv(sanitizeHost(host) + "_API_KEY") != null
        || System.getenv(sanitizeHost(host) + "_API_SECRET_KEY") != null) {
      host += "-x";
    }
    ExchangeSpecification spec = new ExchangeSpecification(Exchange.class);
    spec.setHost(host);

    AuthUtils.setApiAndSecretKey(spec, PREFIX);

    assertEquals(FILE_API_KEY, spec.getApiKey());
    assertEquals(FILE_SECRET_KEY, spec.getSecretKey());
  }

  @Test
  public void testEnvOverridesSecretKeysUsingHost() {
    String host = "api-sandbox.coinbase.com";
    String hostToken = sanitizeHost(host);
    String envKeyName = hostToken + "_API_KEY";
    String envSecretName = hostToken + "_API_SECRET_KEY";

    String previousKey = System.getenv(envKeyName);
    String previousSecret = System.getenv(envSecretName);

    RuntimeException envMutationFailure = null;
    try {
      setEnvValue(envKeyName, "env-api-key");
      setEnvValue(envSecretName, "env-secret-key");
    } catch (RuntimeException e) {
      envMutationFailure = e;
    }

    String envApiKey = System.getenv(envKeyName);
    String envSecretKey = System.getenv(envSecretName);
    String effectiveApiKey = envApiKey != null ? envApiKey.trim() : null;
    String effectiveSecretKey = envSecretKey != null ? envSecretKey.trim() : null;
    boolean hasEnvValues =
        effectiveApiKey != null
            && !effectiveApiKey.isEmpty()
            && effectiveSecretKey != null
            && !effectiveSecretKey.isEmpty();
    boolean usingTestValues =
        "env-api-key".equals(effectiveApiKey) && "env-secret-key".equals(effectiveSecretKey);
    try {
      if (!hasEnvValues) {
        if (envMutationFailure != null) {
          throw new TestAbortedException("Unable to set environment variables", envMutationFailure);
        }
        assumeTrue(false, "Environment variables not available for test");
      }
      ExchangeSpecification spec = new ExchangeSpecification(Exchange.class);
      spec.setHost(host);

      AuthUtils.setApiAndSecretKey(spec, PREFIX);

      if (usingTestValues) {
        assertEquals("env-api-key", spec.getApiKey());
        assertEquals("env-secret-key", spec.getSecretKey());
      } else {
        assertTrue(effectiveApiKey.equals(spec.getApiKey()));
        assertTrue(effectiveSecretKey.equals(spec.getSecretKey()));
      }
    } finally {
      try {
        setEnvValue(envKeyName, previousKey);
        setEnvValue(envSecretName, previousSecret);
      } catch (RuntimeException e) {
        // Ignore cleanup failures.
      }
    }
  }

  @Test
  public void testEnvUsesSslUriWhenHostMissing() {
    String host = "api-sandbox.coinbase.com";
    String hostToken = sanitizeHost(host);
    String envKeyName = hostToken + "_API_KEY";
    String envSecretName = hostToken + "_API_SECRET_KEY";

    String previousKey = System.getenv(envKeyName);
    String previousSecret = System.getenv(envSecretName);

    RuntimeException envMutationFailure = null;
    try {
      setEnvValue(envKeyName, "env-api-key-ssl");
      setEnvValue(envSecretName, "env-secret-key-ssl");
    } catch (RuntimeException e) {
      envMutationFailure = e;
    }

    String envApiKey = System.getenv(envKeyName);
    String envSecretKey = System.getenv(envSecretName);
    String effectiveApiKey = envApiKey != null ? envApiKey.trim() : null;
    String effectiveSecretKey = envSecretKey != null ? envSecretKey.trim() : null;
    boolean hasEnvValues =
        effectiveApiKey != null
            && !effectiveApiKey.isEmpty()
            && effectiveSecretKey != null
            && !effectiveSecretKey.isEmpty();
    boolean usingTestValues =
        "env-api-key-ssl".equals(effectiveApiKey)
            && "env-secret-key-ssl".equals(effectiveSecretKey);
    try {
      if (!hasEnvValues) {
        if (envMutationFailure != null) {
          throw new TestAbortedException("Unable to set environment variables", envMutationFailure);
        }
        assumeTrue(false, "Environment variables not available for test");
      }
      ExchangeSpecification spec = new ExchangeSpecification(Exchange.class);
      spec.setSslUri("https://api-sandbox.coinbase.com");
      spec.setHost(null);

      AuthUtils.setApiAndSecretKey(spec, PREFIX);

      if (usingTestValues) {
        assertEquals("env-api-key-ssl", spec.getApiKey());
        assertEquals("env-secret-key-ssl", spec.getSecretKey());
      } else {
        assertTrue(effectiveApiKey.equals(spec.getApiKey()));
        assertTrue(effectiveSecretKey.equals(spec.getSecretKey()));
      }
    } finally {
      try {
        setEnvValue(envKeyName, previousKey);
        setEnvValue(envSecretName, previousSecret);
      } catch (RuntimeException e) {
        // Ignore cleanup failures.
      }
    }
  }

  private static String sanitizeHost(String host) {
    String normalized = host.trim().toUpperCase(Locale.ROOT);
    return normalized.replaceAll("[^A-Z0-9]", "_");
  }

  private static void assumeSecretKeysAvailable(String prefix) {
    String resource = prefix != null ? prefix + "-secret.keys" : "secret.keys";
    boolean onClasspath = AuthUtils.class.getResource("/" + resource) != null;
    File keyfile = new File(new File(System.getProperty("user.home"), ".ssh"), resource);
    boolean onDisk = keyfile.isFile();
    assumeTrue(onClasspath || onDisk, "Missing secret keys file: " + resource);
  }

  @SuppressWarnings("unchecked")
  private static void setEnvValue(String key, String value) {
    try {
      Map<String, String> env = System.getenv();
      Class<?> envClass = env.getClass();
      Field field = envClass.getDeclaredField("m");
      field.setAccessible(true);
      Map<String, String> mutableEnv = (Map<String, String>) field.get(env);
      if (value == null) {
        mutableEnv.remove(key);
      } else {
        mutableEnv.put(key, value);
      }
      return;
    } catch (Exception ignored) {
      // fall back to ProcessEnvironment path
    }

    try {
      Class<?> processEnv = Class.forName("java.lang.ProcessEnvironment");
      Field theEnvironment = processEnv.getDeclaredField("theEnvironment");
      theEnvironment.setAccessible(true);
      Map<String, String> env = (Map<String, String>) theEnvironment.get(null);
      if (value == null) {
        env.remove(key);
      } else {
        env.put(key, value);
      }

      Field ciEnv = processEnv.getDeclaredField("theCaseInsensitiveEnvironment");
      ciEnv.setAccessible(true);
      Map<String, String> envCi = (Map<String, String>) ciEnv.get(null);
      if (value == null) {
        envCi.remove(key);
      } else {
        envCi.put(key, value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Unable to set environment variable for test", e);
    }
  }
}
