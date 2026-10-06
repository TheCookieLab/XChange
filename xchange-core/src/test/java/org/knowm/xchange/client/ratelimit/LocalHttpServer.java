package org.knowm.xchange.client.ratelimit;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimal loopback HTTP/1.1 server on a dynamic port that records every request and honours {@code
 * Connection: close} like a real server, so tests can count exactly what reached the wire.
 */
final class LocalHttpServer implements Closeable {

  /** One received request. */
  static final class Seen {
    final int connectionId;
    final String method;
    final String target;
    final Map<String, String> headers;
    final String body;

    private Seen(
        int connectionId, String method, String target, Map<String, String> headers, String body) {
      this.connectionId = connectionId;
      this.method = method;
      this.target = target;
      this.headers = headers;
      this.body = body;
    }

    /** Case-insensitive request header, or {@code null}. */
    String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }
  }

  /** What the server does for one request. */
  static final class Reply {
    final int status;
    final Map<String, String> headers = new LinkedHashMap<>();
    final String body;
    final boolean drop;

    private Reply(int status, String body, boolean drop) {
      this.status = status;
      this.body = body;
      this.drop = drop;
    }

    static Reply json(int status, String body) {
      Reply reply = new Reply(status, body, false);
      reply.headers.put("Content-Type", "application/json");
      return reply;
    }

    /** Closes the connection without writing any response. */
    static Reply drop() {
      return new Reply(0, "", true);
    }

    Reply header(String name, String value) {
      headers.put(name, value);
      return this;
    }
  }

  private final ServerSocket serverSocket;
  private final Function<Seen, Reply> responder;
  private final List<Seen> requests = Collections.synchronizedList(new ArrayList<>());
  private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());
  private final Thread acceptor;
  private int nextConnection;

  LocalHttpServer(Function<Seen, Reply> responder) throws IOException {
    this.responder = responder;
    this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    this.acceptor = new Thread(this::acceptLoop, "local-http-acceptor");
    this.acceptor.setDaemon(true);
    this.acceptor.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + serverSocket.getLocalPort();
  }

  /** Snapshot of every request received so far, in arrival order. */
  List<Seen> requests() {
    synchronized (requests) {
      return new ArrayList<>(requests);
    }
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
    synchronized (sockets) {
      for (Socket socket : sockets) {
        socket.close();
      }
    }
  }

  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      try {
        Socket socket = serverSocket.accept();
        sockets.add(socket);
        int id;
        synchronized (this) {
          id = ++nextConnection;
        }
        Thread worker = new Thread(() -> serve(socket, id), "local-http-conn-" + id);
        worker.setDaemon(true);
        worker.start();
      } catch (IOException e) {
        return;
      }
    }
  }

  private void serve(Socket socket, int connectionId) {
    try (Socket closing = socket) {
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();
      while (true) {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
          return;
        }
        String[] parts = requestLine.split(" ");
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
          int colon = line.indexOf(':');
          headers.put(
              line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
              line.substring(colon + 1).trim());
        }
        int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
        byte[] body = in.readNBytes(length);
        Seen seen =
            new Seen(
                connectionId, parts[0], parts[1], headers, new String(body, StandardCharsets.UTF_8));
        requests.add(seen);
        Reply reply = responder.apply(seen);
        if (reply.drop) {
          return;
        }
        byte[] payload = reply.body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder("HTTP/1.1 " + reply.status + " X\r\n");
        for (Map.Entry<String, String> header : reply.headers.entrySet()) {
          head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        boolean close = "close".equalsIgnoreCase(headers.get("connection"));
        head.append("Content-Length: ").append(payload.length).append("\r\n");
        if (close) {
          head.append("Connection: close\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(payload);
        out.flush();
        if (close) {
          return;
        }
      }
    } catch (IOException e) {
      // the peer or the test closed the connection
    }
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        break;
      }
      if (b != '\r') {
        line.write(b);
      }
    }
    if (b == -1 && line.size() == 0) {
      return null;
    }
    return line.toString(StandardCharsets.ISO_8859_1);
  }
}
