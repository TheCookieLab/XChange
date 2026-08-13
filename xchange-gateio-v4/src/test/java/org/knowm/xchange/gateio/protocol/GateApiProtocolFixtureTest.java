package org.knowm.xchange.gateio.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Offline CI gate over the pinned Gate API v4 protocol fixture.
 *
 * <p>Validates the pinned normalized snapshot ({@code protocol/gate-api-v4-2026-08-13.json}) and
 * the implemented-surface manifest ({@code protocol/implemented-endpoints.json}): every implemented
 * endpoint must exist in the fixture with the same verb, and every required param the fixture
 * documents for an implemented endpoint must be covered by the manifest. Fails when the pinned
 * contract drifts in implemented paths/verbs/required fields. The companion drift tool {@code
 * scripts/check-gate-api-drift.py} re-checks against freshly extracted docs pages (requires
 * gate.com access).
 */
class GateApiProtocolFixtureTest {

  private static final String FIXTURE = "/protocol/gate-api-v4-2026-08-13.json";
  private static final String MANIFEST = "/protocol/implemented-endpoints.json";
  private static final String PINNED_AT = "2026-08-13";

  private static JsonNode load(String resource) throws IOException {
    try (InputStream in = GateApiProtocolFixtureTest.class.getResourceAsStream(resource)) {
      assertThat(in).as("missing resource %s", resource).isNotNull();
      return new ObjectMapper().readTree(in);
    }
  }

  /** Required param names from a fixture endpoint: body-nested fields are prefixed {@code body.}. */
  private static Set<String> requiredParams(JsonNode endpoint) {
    Set<String> required = new HashSet<>();
    for (JsonNode p : endpoint.path("params")) {
      if (!p.path("required").asBoolean(false)) {
        continue;
      }
      String in = p.path("in").asText("");
      int depth = p.path("depth").asInt(0);
      if ("body".equals(in) && depth == 0) {
        continue; // container; nested fields carry the real requirements
      }
      required.add(("body".equals(in) ? "body." : "") + p.path("name").asText());
    }
    return required;
  }

  @Test
  void fixture_isPinnedAndWellFormed() throws IOException {
    JsonNode fixture = load(FIXTURE);

    assertThat(fixture.path("fixture").asText()).isEqualTo("gate-api-v4");
    assertThat(fixture.path("pinned_at").asText()).isEqualTo(PINNED_AT);
    assertThat(fixture.path("source_base").asText()).startsWith("https://www.gate.com/");

    JsonNode endpoints = fixture.path("endpoints");
    assertThat(endpoints.size()).isGreaterThanOrEqualTo(200);

    Set<String> seen = new HashSet<>();
    for (JsonNode ep : endpoints) {
      String method = ep.path("method").asText();
      String path = ep.path("path").asText();
      assertThat(method).matches("GET|POST|DELETE|PUT|PATCH");
      assertThat(path).startsWith("/");
      assertThat(seen.add(method + " " + path))
          .as("duplicate endpoint %s %s", method, path)
          .isTrue();
      for (JsonNode p : ep.path("params")) {
        assertThat(p.path("name").asText()).isNotEmpty();
        assertThat(p.path("in").asText()).isIn("query", "path", "header", "body");
        assertThat(p.path("required").isBoolean()).isTrue();
        assertThat(p.path("depth").isInt()).isTrue();
      }
    }
  }

  @Test
  void implementedEndpoints_arePinnedInFixture_withRequiredParamsCovered() throws IOException {
    JsonNode fixture = load(FIXTURE);
    JsonNode manifest = load(MANIFEST);

    Set<String> fixtureKeys = new HashSet<>();
    for (JsonNode ep : fixture.path("endpoints")) {
      fixtureKeys.add(ep.path("method").asText() + " " + ep.path("path").asText());
    }
    Set<String> extraKeys = new HashSet<>();
    for (JsonNode ep : fixture.path("endpoints")) {
      if (ep.hasNonNull("source")) {
        extraKeys.add(ep.path("method").asText() + " " + ep.path("path").asText());
      }
    }

    assertThat(manifest.size()).isGreaterThanOrEqualTo(20);
    List<String> problems = new ArrayList<>();
    for (JsonNode entry : manifest) {
      String key = entry.path("method").asText() + " " + entry.path("path").asText();
      if (entry.path("extra").asBoolean(false)) {
        // extra endpoints are pinned with provenance; they must exist in the fixture with a source
        assertThat(extraKeys).as("extra endpoint %s lacks provenance", key).contains(key);
        continue;
      }
      assertThat(fixtureKeys).as("implemented endpoint %s missing from fixture", key).contains(key);

      JsonNode endpoint = null;
      for (JsonNode ep : fixture.path("endpoints")) {
        if ((ep.path("method").asText() + " " + ep.path("path").asText()).equals(key)) {
          endpoint = ep;
          break;
        }
      }
      Set<String> fixtureRequired = requiredParams(endpoint);
      Set<String> manifestRequired = new HashSet<>();
      for (JsonNode rp : entry.path("required_params")) {
        manifestRequired.add(rp.asText());
      }
      Set<String> uncovered = new HashSet<>(fixtureRequired);
      uncovered.removeAll(manifestRequired);
      if (!uncovered.isEmpty()) {
        problems.add(key + " requires params not in manifest: " + uncovered);
      }
    }
    assertThat(problems)
        .as("protocol drift in implemented surface — re-pin or extend the manifest")
        .isEmpty();
  }

  @Test
  void spotAccounts_extraEndpoint_isPinnedWithProvenance() throws IOException {
    JsonNode fixture = load(FIXTURE);

    JsonNode spotAccounts = null;
    for (JsonNode ep : fixture.path("endpoints")) {
      if (ep.path("method").asText().equals("GET")
          && ep.path("path").asText().equals("/spot/accounts")) {
        spotAccounts = ep;
        break;
      }
    }
    assertThat(spotAccounts).as("GET /spot/accounts must stay pinned").isNotNull();
    assertThat(spotAccounts.path("source").asText()).contains("gateapi-go");
    assertThat(requiredParams(spotAccounts)).isEmpty();
  }

  @Test
  void manifest_listsCurrentImplementedSurface() throws IOException {
    JsonNode manifest = load(MANIFEST);

    Set<String> keys = new HashSet<>();
    for (JsonNode entry : manifest) {
      keys.add(entry.path("method").asText() + " " + entry.path("path").asText());
    }
    assertThat(keys).contains(
        "GET /spot/time",
        "GET /spot/currencies",
        "GET /spot/currencies/{currency}",
        "GET /spot/order_book",
        "GET /spot/tickers",
        "GET /wallet/currency_chains",
        "POST /spot/orders",
        "GET /spot/orders",
        "GET /spot/orders/{order_id}",
        "DELETE /spot/orders/{order_id}",
        "GET /spot/my_trades",
        "GET /wallet/withdrawals",
        "GET /wallet/deposits",
        "POST /withdrawals",
        "GET /spot/trades",
        "GET /spot/candlesticks",
        "GET /spot/open_orders",
        "PATCH /spot/orders/{order_id}",
        "DELETE /spot/orders",
        "POST /spot/batch_orders",
        "POST /spot/cancel_batch_orders",
        "POST /spot/countdown_cancel_all");
  }
}
