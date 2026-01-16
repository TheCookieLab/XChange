package org.knowm.xchange.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Properties;
import org.knowm.xchange.ExchangeSpecification;

public class AuthUtils {

  /**
   * Generates a BASE64 Basic Authentication String
   *
   * @return BASE64 Basic Authentication String
   */
  public static String getBasicAuth(String user, final String pass) {

    return "Basic " + java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
  }

  /**
   * Read the API & Secret key from a resource called {@code secret.keys}. NOTE: This file MUST
   * NEVER be commited to source control. It is therefore added to .gitignore.
   */
  public static void setApiAndSecretKey(ExchangeSpecification exchangeSpec) {

    setApiAndSecretKey(exchangeSpec, null);
  }

  /**
   * Read the API & Secret key from a resource called {@code prefix}-{@code secret.keys}. NOTE: This
   * file MUST NEVER be commited to source control. It is therefore added to .gitignore.
   *
   * <p>Environment variables are checked first using the exchange host as a prefix:
   * {@code <HOST>_API_KEY} and {@code <HOST>_API_SECRET_KEY}, where host is uppercased and
   * non-alphanumeric characters are replaced with underscores (e.g., api-sandbox.coinbase.com
   * becomes API_SANDBOX_COINBASE_COM_API_KEY).
   */
  public static void setApiAndSecretKey(ExchangeSpecification exchangeSpec, String prefix) {

    String host = resolveHost(exchangeSpec);
    String envApiKey = null;
    String envSecretKey = null;
    if (host != null) {
      String hostToken = sanitizeHost(host);
      envApiKey = readEnvValue(hostToken + "_API_KEY");
      envSecretKey = readEnvValue(hostToken + "_API_SECRET_KEY");
    }

    Properties props = getSecretProperties(prefix);

    if (envApiKey != null) {
      exchangeSpec.setApiKey(envApiKey);
    } else if (props != null) {
      String apiKey = props.getProperty("apiKey");
      if (apiKey != null) {
        exchangeSpec.setApiKey(apiKey);
      }
    }

    if (envSecretKey != null) {
      exchangeSpec.setSecretKey(envSecretKey);
    } else if (props != null) {
      String secretKey = props.getProperty("secretKey");
      if (secretKey != null) {
        exchangeSpec.setSecretKey(secretKey);
      }
    }
  }

  /**
   * Read the secret properties from a resource called {@code prefix}-{@code secret.keys}. NOTE:
   * This file MUST NEVER be commited to source control. It is therefore added to .gitignore.
   *
   * @return The properties or null
   */
  public static Properties  getSecretProperties(String prefix) {

    String resource = prefix != null ? prefix + "-secret.keys" : "secret.keys";

    // First try to find the keys in the classpath
    InputStream inStream = AuthUtils.class.getResourceAsStream("/" + resource);

    // Next try to find the keys in the user's home/.ssh dir
    File keyfile = new File(System.getProperty("user.home") + "/" + ".ssh", resource);
    if (inStream == null && keyfile.isFile()) {
      try {
        inStream = new FileInputStream(keyfile);
      } catch (IOException e) {
        // do nothing
      }
    }

    Properties props = null;
    if (inStream != null) {
      try {
        props = new Properties();
        props.load(inStream);
        return props;
      } catch (IOException e) {
        // do nothing
      }
    }
    return props;
  }

  private static String resolveHost(ExchangeSpecification exchangeSpec) {
    if (exchangeSpec == null) {
      return null;
    }
    String host = exchangeSpec.getHost();
    if (host != null && !host.trim().isEmpty()) {
      return host.trim();
    }
    String sslUri = exchangeSpec.getSslUri();
    if (sslUri == null || sslUri.trim().isEmpty()) {
      return null;
    }
    try {
      URI uri = new URI(sslUri);
      return uri.getHost();
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private static String sanitizeHost(String host) {
    String normalized = host.trim().toUpperCase(Locale.ROOT);
    return normalized.replaceAll("[^A-Z0-9]", "_");
  }

  private static String readEnvValue(String key) {
    String value = System.getenv(key);
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }
}
