package si.mazi.rescu;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import oauth.signpost.OAuthConsumer;
import si.mazi.rescu.clients.HttpConnection;
import si.mazi.rescu.clients.HttpConnectionType;

/**
 * rescu {@code HttpTemplate} whose every {@link HttpConnection} is exactly one wire attempt.
 *
 * <p>Rescu's {@code java} backend sits on {@code HttpURLConnection}, which can put more requests
 * on the wire than the caller issued: it follows redirects, retries a request found dead on a
 * pooled keep-alive connection, and after any failure to read a response re-sends the request
 * once (a GET always, a POST unless a JVM-wide property says otherwise); fixed-length streaming,
 * the only per-connection switch, is impossible for GET and turns 401/407 error bodies into
 * exceptions. None of that is visible to rate-limit admission. This template therefore hands
 * rescu a {@link SingleAttemptConnection}, which cannot retry, pool or follow redirects.
 *
 * <p>Settings that cannot be honoured by that transport are rejected when the template is created
 * rather than silently dropped: a custom SSL socket factory or hostname verifier, an OAuth
 * consumer, a non-HTTP proxy and the {@code apache} connection type.
 *
 * @since 1.0.3
 */
final class SingleAttemptHttpTemplate extends HttpTemplate {

  private final ProxySelector proxySelector;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;

  SingleAttemptHttpTemplate(
      int connTimeout,
      int readTimeout,
      String proxyHost,
      Integer proxyPort,
      Proxy.Type proxyType,
      SSLSocketFactory sslSocketFactory,
      HostnameVerifier hostnameVerifier,
      OAuthConsumer oAuthConsumer,
      HttpConnectionType connectionType) {
    super(
        connTimeout,
        readTimeout,
        proxyHost,
        proxyPort,
        proxyType,
        sslSocketFactory,
        hostnameVerifier,
        oAuthConsumer,
        connectionType);
    if (connectionType != HttpConnectionType.java) {
      throw unsupported("connectionType=" + connectionType + " (only java is supported)");
    }
    if (sslSocketFactory != null) {
      throw unsupported("a custom SSLSocketFactory");
    }
    if (hostnameVerifier != null) {
      throw unsupported("a custom HostnameVerifier");
    }
    if (oAuthConsumer != null) {
      throw unsupported("an OAuth consumer");
    }
    if (proxyHost == null || proxyPort == null) {
      this.proxySelector = null;
    } else if (proxyType == null || proxyType == Proxy.Type.HTTP) {
      this.proxySelector = ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));
    } else {
      throw unsupported("proxy type " + proxyType + " (only HTTP is supported)");
    }
    this.connectTimeoutMillis = connTimeout;
    this.readTimeoutMillis = readTimeout;
  }

  private static IllegalStateException unsupported(String setting) {
    return new IllegalStateException(
        "Rate-limited proxies cannot honour " + setting + ": it would defeat one wire attempt per admission");
  }

  @Override
  protected HttpConnection getRescuHttpURLConnection(String urlString) throws IOException {
    try {
      return new SingleAttemptConnection(
          new URI(urlString), proxySelector, connectTimeoutMillis, readTimeoutMillis);
    } catch (URISyntaxException e) {
      throw new IOException("Malformed URL " + urlString, e);
    }
  }
}
