package org.knowm.xchange.uniswap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loopback Ethereum JSON-RPC node on a dynamic port. It records every request that reaches the
 * wire, in arrival order, and answers through a replaceable {@link Responder}; nothing leaves the
 * machine.
 */
public final class RpcStub implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Plausible {@code result} JSON of every method of the node client, by JSON-RPC method. */
  private static final Map<String, String> RESULTS = results();

  /** Decides the answer to one wire request. */
  @FunctionalInterface
  public interface Responder {
    /**
     * @param arrival 1-based arrival index of the request on this stub
     * @param method the JSON-RPC method of the request
     * @return the reply
     */
    Reply reply(int arrival, String method);
  }

  /** An HTTP answer. */
  public static final class Reply {
    private final int status;
    private final String body;
    private final Map<String, String> headers;

    private Reply(int status, String body, Map<String, String> headers) {
      this.status = status;
      this.body = body;
      this.headers = headers;
    }

    /** HTTP 200 carrying the default JSON-RPC result of the method. */
    public static Reply success() {
      return new Reply(200, null, Map.of());
    }

    /** HTTP 200 carrying the given JSON {@code result}. */
    public static Reply result(String resultJson) {
      return new Reply(200, "{\"result\":" + resultJson + "}", Map.of());
    }

    /** HTTP 200 carrying a JSON-RPC error object. */
    public static Reply jsonRpcError(int code, String message) {
      return new Reply(
          200, "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}", Map.of());
    }

    /** HTTP 429 without any header. */
    public static Reply tooManyRequests() {
      return new Reply(429, "{\"message\":\"rate limited\"}", Map.of());
    }

    /** HTTP 429 with a {@code Retry-After} header. */
    public static Reply tooManyRequests(String retryAfter) {
      return new Reply(429, "{\"message\":\"rate limited\"}", Map.of("Retry-After", retryAfter));
    }

    /** A bare HTTP status with an empty JSON body. */
    public static Reply status(int status, Map<String, String> headers) {
      return new Reply(status, "{\"message\":\"status\"}", headers);
    }
  }

  private final HttpServer server;
  private final ExecutorService handlers = Executors.newCachedThreadPool();
  private final List<String> methods = new ArrayList<>();
  private volatile Responder responder = (arrival, method) -> Reply.success();

  /** Starts the stub on a free loopback port. */
  public RpcStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(handlers);
    server.createContext("/", this::handle);
    server.start();
  }

  /** The endpoint URL. */
  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Replaces the responder. */
  public void respondWith(Responder newResponder) {
    this.responder = newResponder;
  }

  /** The JSON-RPC methods that reached the wire so far, in arrival order. */
  public List<String> methods() {
    synchronized (methods) {
      return new ArrayList<>(methods);
    }
  }

  /** The number of requests that reached the wire so far. */
  public int arrivals() {
    synchronized (methods) {
      return methods.size();
    }
  }

  @Override
  public void close() {
    server.stop(0);
    handlers.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    JsonNode request = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
    String method = request.path("method").asText();
    JsonNode id = request.path("id");
    int arrival;
    synchronized (methods) {
      methods.add(method);
      arrival = methods.size();
    }
    Reply reply = responder.reply(arrival, method);
    String body = reply.body;
    if (body == null) {
      String result = RESULTS.get(method);
      body = result == null ? "{\"error\":{\"code\":-32601,\"message\":\"unknown method\"}}" : "{\"result\":" + result + "}";
    }
    // splice the echoed id into the JSON object
    String payload = "{\"jsonrpc\":\"2.0\",\"id\":" + id + "," + body.substring(1);
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    reply.headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
    exchange.sendResponseHeaders(reply.status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static Map<String, String> results() {
    Map<String, String> results = new LinkedHashMap<>();
    results.put("eth_chainId", "\"0x1\"");
    results.put("eth_blockNumber", "\"0x64\"");
    results.put("eth_getCode", "\"0x6000\"");
    results.put("eth_call", "\"0x" + "00".repeat(31) + "01\"");
    results.put("eth_getBalance", "\"0x10\"");
    results.put("eth_getTransactionCount", "\"0x5\"");
    results.put("eth_estimateGas", "\"0x5208\"");
    results.put(
        "eth_feeHistory",
        "{\"oldestBlock\":\"0x1\",\"baseFeePerGas\":[\"0x1\",\"0x2\"],\"gasUsedRatio\":[0.5]}");
    results.put("eth_getBlockByNumber", "{\"number\":\"0x1\",\"baseFeePerGas\":\"0x7\"}");
    results.put("eth_maxPriorityFeePerGas", "\"0x3b9aca00\"");
    results.put("eth_getTransactionByHash", "null");
    results.put("eth_getTransactionReceipt", "null");
    results.put("eth_sendRawTransaction", "\"0x" + "ab".repeat(32) + "\"");
    return Map.copyOf(results);
  }
}
