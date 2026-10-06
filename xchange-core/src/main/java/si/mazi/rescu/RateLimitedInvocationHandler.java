package si.mazi.rescu;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import jakarta.ws.rs.Path;
import org.knowm.xchange.client.ratelimit.RateLimitAttemptObserver;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import si.mazi.rescu.clients.HttpConnection;
import si.mazi.rescu.serialization.jackson.DefaultJacksonObjectMapperFactory;
import si.mazi.rescu.serialization.jackson.JacksonObjectMapperFactory;

/**
 * rescu invocation handler that runs every proxy call as one admitted, single-attempt operation of
 * a {@link RateLimitContext}.
 *
 * <p>{@link #invoke} hands the whole base invocation to {@link RateLimitContext#execute}, so
 * admission happens before {@code RestInvocation.create}: parameter digests, JWTs, nonces and
 * timestamps are produced inside the admitted attempt, and a replay after a confirmed rate
 * rejection builds the invocation, and so signs, anew. {@link #receiveAndMap} reports the exact
 * response status and headers of the attempt to the attempt's observer before rescu translates the
 * response into a result or exception. The wire attempt is made by {@link
 * SingleAttemptHttpTemplate}, so no redirect or hidden retry can bypass admission.
 *
 * <p><b>Operation key.</b> {@code "<HTTP METHOD> <interface @Path>/<method @Path>"}, with empty
 * path segments removed, no leading slash, path-parameter placeholders left unexpanded and
 * neither query string nor host, for example {@code GET
 * api/v3/brokerage/market/products/{product_id}/candles}.
 *
 * <p><b>Authenticated.</b> A request is authenticated when at least one parameter of the invoked
 * Java method is declared with a type assignable to {@link ParamsDigest} and the argument passed
 * is not {@code null}. This is derived from the interface declaration and the call only, never
 * from whether a credential happens to be installed elsewhere.
 *
 * @since 1.0.3
 */
final class RateLimitedInvocationHandler extends RestInvocationHandler {

  private final RateLimitPolicy policy;
  private final RateLimitContext context;
  private final String userScope;
  private final String intfacePath;
  private final SingleAttemptHttpTemplate template;
  private final RequestWriterResolver writerResolver;
  private final Map<Method, OperationShape> shapes = new ConcurrentHashMap<>();
  private final ThreadLocal<RateLimitAttemptObserver> currentAttempt = new ThreadLocal<>();

  RateLimitedInvocationHandler(
      Class<?> restInterface,
      String baseUrl,
      ClientConfig config,
      RateLimitPolicy policy,
      RateLimitContext context,
      String userScope) {
    super(restInterface, baseUrl, config);
    this.policy = policy;
    this.context = context;
    this.userScope = userScope;
    this.intfacePath = restInterface.getAnnotation(Path.class).value();
    this.template =
        new SingleAttemptHttpTemplate(
            config.getHttpConnTimeout(),
            config.getHttpReadTimeout(),
            config.getProxyHost(),
            config.getProxyPort(),
            config.getProxyType(),
            config.getSslSocketFactory(),
            config.getHostnameVerifier(),
            config.getOAuthConsumer(),
            config.getConnectionType());
    JacksonObjectMapperFactory mapperFactory = config.getJacksonObjectMapperFactory();
    if (mapperFactory == null) {
      mapperFactory = new DefaultJacksonObjectMapperFactory();
    }
    this.writerResolver = RequestWriterResolver.createDefault(mapperFactory.createObjectMapper());
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getDeclaringClass().equals(Object.class)) {
      return super.invoke(proxy, method, args);
    }
    OperationShape shape = shapes.computeIfAbsent(method, this::shapeOf);
    RateLimitRequest request = new RateLimitRequest(shape.operationKey, shape.isAuthenticated(args));
    return context.execute(
        policy,
        request,
        userScope,
        observer -> {
          RateLimitAttemptObserver outer = currentAttempt.get();
          currentAttempt.set(observer);
          try {
            return super.invoke(proxy, method, args);
          } catch (Throwable t) {
            if (t instanceof Exception) {
              throw (Exception) t;
            }
            if (t instanceof Error) {
              throw (Error) t;
            }
            throw new UndeclaredThrowableException(t);
          } finally {
            if (outer == null) {
              currentAttempt.remove();
            } else {
              currentAttempt.set(outer);
            }
          }
        });
  }

  @Override
  protected HttpConnection invokeHttp(RestInvocation invocation) throws IOException {
    RestMethodMetadata metadata = invocation.getMethodMetadata();
    String body = writerResolver.resolveWriter(metadata).writeBody(invocation);
    return template.send(
        invocation.getInvocationUrl(),
        body,
        invocation.getAllHttpHeaders(),
        metadata.getHttpMethod());
  }

  @Override
  protected Object receiveAndMap(RestMethodMetadata methodMetadata, HttpConnection connection)
      throws IOException {
    RateLimitAttemptObserver observer = currentAttempt.get();
    if (observer != null) {
      int status = connection.getResponseCode();
      observer.observeHttpResponse(status, name -> headerValue(connection, name));
    }
    return super.receiveAndMap(methodMetadata, connection);
  }

  /** Case-insensitive lookup of a response header; the last value wins like {@code getHeaderField}. */
  private static String headerValue(HttpConnection connection, String name) {
    if (name == null) {
      return null;
    }
    String found = null;
    Map<String, List<String>> headers = connection.getHeaderFields();
    if (headers == null) {
      return null;
    }
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (entry.getKey() != null
          && entry.getKey().equalsIgnoreCase(name)
          && entry.getValue() != null
          && !entry.getValue().isEmpty()) {
        found = entry.getValue().get(entry.getValue().size() - 1);
      }
    }
    return found;
  }

  private OperationShape shapeOf(Method method) {
    RestMethodMetadata metadata = RestMethodMetadata.create(method, "", intfacePath);
    StringBuilder path = new StringBuilder();
    for (String segment : (intfacePath + "/" + metadata.getMethodPathTemplate()).split("/")) {
      if (!segment.isEmpty()) {
        if (path.length() > 0) {
          path.append('/');
        }
        path.append(segment);
      }
    }
    String key = metadata.getHttpMethod().name().toUpperCase(Locale.ROOT) + " " + path;
    List<Integer> digestParameters = new ArrayList<>();
    Class<?>[] types = method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      if (ParamsDigest.class.isAssignableFrom(types[i])) {
        digestParameters.add(i);
      }
    }
    return new OperationShape(key, digestParameters.stream().mapToInt(Integer::intValue).toArray());
  }

  private static final class OperationShape {
    private final String operationKey;
    private final int[] digestParameters;

    private OperationShape(String operationKey, int[] digestParameters) {
      this.operationKey = operationKey;
      this.digestParameters = digestParameters;
    }

    private boolean isAuthenticated(Object[] args) {
      if (args == null) {
        return false;
      }
      for (int index : digestParameters) {
        if (args[index] != null) {
          return true;
        }
      }
      return false;
    }
  }
}
