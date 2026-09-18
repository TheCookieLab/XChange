package org.knowm.xchange.polymarket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;

/**
 * Opt-in on-disk cache of the Gamma market catalog, keyed by the Gamma host and the {@code closed}
 * filter it was read with. Line 1 is a JSON header ({@code {version, gammaUri, closed, cursor,
 * fullWalkAt}}); every later line is one {@link PolymarketGammaMarket}. The walk is ordered by
 * immutable market id ascending, so resuming from {@code cursor} finds markets created since the
 * last walk; rows whose state changed in the meantime (a market closing) are only reconciled by the
 * next from-the-start sweep, which the max age forces.
 *
 * <p>The full catalog is about 189,000 rows, so the file is on the order of 100 MB; caching is
 * opt-in for that reason.
 */
final class PolymarketGammaCatalogCache {

  /** Cache format version; a cache written by another version is ignored. */
  static final int VERSION = 1;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path path;
  private final String gammaUri;
  private final Boolean closed;
  private final Map<String, PolymarketGammaMarket> markets = new LinkedHashMap<>();
  private String cursor;
  private long fullWalkAtMillis;

  private PolymarketGammaCatalogCache(Path path, String gammaUri, Boolean closed) {
    this.path = path;
    this.gammaUri = gammaUri;
    this.closed = closed;
  }

  /**
   * Reads the cache written for {@code gammaUri} and {@code closed}. Any problem — a missing or
   * unreadable file, an absent or mismatched header, or a malformed row — yields an empty cache, so
   * the caller sweeps from the start; a cache is an optimisation and must never fail a discovery.
   */
  static PolymarketGammaCatalogCache load(Path path, String gammaUri, Boolean closed) {
    PolymarketGammaCatalogCache cache = new PolymarketGammaCatalogCache(path, gammaUri, closed);
    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return cache;
      }
      JsonNode header = MAPPER.readTree(headerLine);
      if (header.path("version").asInt() != VERSION
          || !Objects.equals(header.path("gammaUri").asText(null), gammaUri)
          || !Objects.equals(
              header.hasNonNull("closed") ? header.get("closed").asBoolean() : null, closed)) {
        return cache;
      }
      cache.cursor = header.hasNonNull("cursor") ? header.get("cursor").asText() : null;
      cache.fullWalkAtMillis = header.path("fullWalkAt").asLong(0L);
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        cache.put(MAPPER.readValue(line, PolymarketGammaMarket.class));
      }
    } catch (IOException | RuntimeException e) {
      // Nothing is logged here: the caller overwrites the file after the sweep this triggers.
      return new PolymarketGammaCatalogCache(path, gammaUri, closed);
    }
    return cache;
  }

  /** True when the catalog was swept from the start within {@code ttlSeconds}. */
  boolean isFresh(long ttlSeconds) {
    return fullWalkAtMillis > 0
        && System.currentTimeMillis() - fullWalkAtMillis <= ttlSeconds * 1000L;
  }

  /** Cursor a walk resumes from, or {@code null} to start at the first market. */
  String cursor() {
    return cursor;
  }

  /** Cached rows in the order they were first read (market id ascending). */
  List<PolymarketGammaMarket> markets() {
    return new ArrayList<>(markets.values());
  }

  /** Adds rows read by the latest walk; a row seen again replaces its earlier copy in place. */
  void merge(List<PolymarketGammaMarket> discovered) {
    for (PolymarketGammaMarket market : discovered) {
      put(market);
    }
  }

  void advanceCursor(String nextCursor) {
    this.cursor = nextCursor;
  }

  /** Records that the whole catalog was just swept from the start, which restarts the max age. */
  void markFullWalk() {
    fullWalkAtMillis = System.currentTimeMillis();
  }

  /**
   * Rewrites the cache atomically.
   *
   * @throws IOException when the configured cache cannot be written; silently never caching would
   *     hide a misconfigured path
   */
  void save() throws IOException {
    Path absolute = path.toAbsolutePath();
    Path parent = absolute.getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
    try {
      try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        writer.write(headerJson());
        writer.newLine();
        for (PolymarketGammaMarket market : markets.values()) {
          writer.write(MAPPER.writeValueAsString(market));
          writer.newLine();
        }
      }
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
        // Best effort; the write failure below is the failure that matters.
      }
      throw new IOException("Polymarket Gamma catalog cache write failed: " + path, e);
    }
  }

  private void put(PolymarketGammaMarket market) {
    String id = market.id();
    // Provider ids are numeric, so they never collide with this prefix: a row without an id is kept
    // rather than silently dropped.
    markets.put(id == null ? "unidentified-" + markets.size() : id, market);
  }

  private String headerJson() throws IOException {
    ObjectNode header = MAPPER.createObjectNode();
    header.put("version", VERSION);
    header.put("gammaUri", gammaUri);
    header.put("closed", closed);
    header.put("cursor", cursor);
    header.put("fullWalkAt", fullWalkAtMillis);
    return MAPPER.writeValueAsString(header);
  }
}
