package org.knowm.xchange.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.io.File;
import org.junit.Assume;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;

public class AuthUtilsTest {

  private static final String PREFIX = "authutils-test";
  private static final String FILE_API_KEY = "file-api-key";
  private static final String FILE_SECRET_KEY = "file-secret-key";

  @Test
  public void testSecretKeysFallbackWhenEnvMissing() {
    assumeSecretKeysAvailable(PREFIX);

    ExchangeSpecification spec = new ExchangeSpecification(Exchange.class);
    spec.setHost("authutils-test-host");

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
    if (!hasEnvValues) {
      if (envMutationFailure != null) {
        Assume.assumeNoException("Unable to set environment variables", envMutationFailure);
      }
      Assume.assumeTrue("Environment variables not available for test", false);
    }

    try {
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
    if (!hasEnvValues) {
      if (envMutationFailure != null) {
        Assume.assumeNoException("Unable to set environment variables", envMutationFailure);
      }
      Assume.assumeTrue("Environment variables not available for test", false);
    }

    try {
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
    Assume.assumeTrue("Missing secret keys file: " + resource, onClasspath || onDisk);
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
