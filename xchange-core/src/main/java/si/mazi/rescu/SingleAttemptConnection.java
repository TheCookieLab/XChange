package si.mazi.rescu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import si.mazi.rescu.clients.HttpConnection;
import si.mazi.rescu.clients.HttpConnectionType;

/**
 * One HTTP/1.1 request on a private {@link HttpClient} that lives and dies with this connection.
 *
 * <p>A connection pool is what makes JDK clients re-send a request on their own (a pooled
 * keep-alive connection found dead is retried transparently), and {@code HttpURLConnection} even
 * re-sends once after any failure to read a response. Here no connection is ever shared or
 * reused, redirects are never followed and nothing is retried, so the request is on the wire at
 * most once: the attempt a rate-limit admission paid for. The request is sent lazily by the first
 * call that needs the response, like the JDK connection it replaces; the body written through
 * {@link #getOutputStream()} is buffered until then.
 *
 * <p>Known differences from rescu's {@code JavaConnection}: the read timeout bounds the wait for
 * the response headers (not each later body read), a {@code Content-Length} header set by the
 * caller is ignored because the length of the buffered body is authoritative, and both the error
 * and the input stream expose the response body of whatever status was returned.
 *
 * @since 1.0.3
 */
final class SingleAttemptConnection implements HttpConnection {

  private final URI uri;
  private final ProxySelector proxy;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final Map<String, String> requestHeaders = new LinkedHashMap<>();
  private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
  private String method = "GET";
  private HttpClient client;
  private HttpResponse<InputStream> response;

  SingleAttemptConnection(
      URI uri, ProxySelector proxy, int connectTimeoutMillis, int readTimeoutMillis) {
    this.uri = uri;
    this.proxy = proxy;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public HttpConnectionType getHttpConnectionType() {
    return HttpConnectionType.java;
  }

  @Override
  public String getHeaderField(String name) {
    List<String> values = response().headers().map().get(name.toLowerCase(Locale.ROOT));
    return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
  }

  @Override
  public String getRequestMethod() {
    return method;
  }

  @Override
  public OutputStream getOutputStream() {
    return requestBody;
  }

  @Override
  public int getResponseCode() throws IOException {
    return send().statusCode();
  }

  @Override
  public Map<String, List<String>> getHeaderFields() {
    return response().headers().map();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return send().body();
  }

  @Override
  public InputStream getErrorStream() throws IOException {
    return send().body();
  }

  @Override
  public void setRequestMethod(HttpMethod httpMethod) throws ProtocolException {
    this.method = httpMethod.name();
  }

  @Override
  public void setHeader(String key, String value) {
    requestHeaders.put(key, value);
  }

  @Override
  public void setReadTimeout(int readTimeout) {
    // fixed at construction from the same ClientConfig value
  }

  @Override
  public void setConnectTimeout(int connTimeout) {
    // fixed at construction from the same ClientConfig value
  }

  @Override
  public boolean isSsl() {
    return "https".equalsIgnoreCase(uri.getScheme());
  }

  @Override
  public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
    throw new IllegalStateException("A custom SSLSocketFactory is not supported by SingleAttemptConnection");
  }

  @Override
  public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
    throw new IllegalStateException("A custom HostnameVerifier is not supported by SingleAttemptConnection");
  }

  @Override
  public void close() {
    if (client != null) {
      client.shutdownNow();
    }
  }

  /** Response of an already sent request; header accessors cannot throw {@link IOException}. */
  private HttpResponse<InputStream> response() {
    if (response == null) {
      throw new IllegalStateException("The request has not been sent yet: " + method + " " + uri);
    }
    return response;
  }

  private HttpResponse<InputStream> send() throws IOException {
    if (response != null) {
      return response;
    }
    HttpClient.Builder clientBuilder =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER);
    if (connectTimeoutMillis > 0) {
      clientBuilder.connectTimeout(Duration.ofMillis(connectTimeoutMillis));
    }
    if (proxy != null) {
      clientBuilder.proxy(proxy);
    }
    client = clientBuilder.build();

    HttpRequest.Builder request = HttpRequest.newBuilder(uri);
    if (readTimeoutMillis > 0) {
      request.timeout(Duration.ofMillis(readTimeoutMillis));
    }
    for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
      if (!"content-length".equalsIgnoreCase(header.getKey())) {
        request.header(header.getKey(), header.getValue());
      }
    }
    byte[] payload = requestBody.toByteArray();
    request.method(
        method, payload.length == 0 ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(payload));
    try {
      response = client.send(request.build(), BodyHandlers.ofInputStream());
    } catch (HttpTimeoutException e) {
      SocketTimeoutException timeout = new SocketTimeoutException(e.getMessage());
      timeout.initCause(e);
      throw timeout;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      InterruptedIOException interrupted = new InterruptedIOException(e.getMessage());
      interrupted.initCause(e);
      throw interrupted;
    }
    return response;
  }
}
