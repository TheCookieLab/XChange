package info.bitrich.xchangestream.kucoin.dto.uta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Regression: UTA order and order-book payload fields with an uppercase letter in the first two
 * characters (O/U, oT, qU, aP, fS, fC, rS, cR, tIF, pO, rO, mM, pS, lR, and order-book O/C/M) must
 * bind from the exact-case JSON keys via explicit {@code @JsonProperty}, not from the
 * Lombok-derived property names (which Jackson lowercases: {@code O -> o}, {@code oT -> ot}).
 */
class UtaOrderDataParsingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void parsesUtaOrderEventPayload() throws Exception {
    String payload =
        "{\"O\":1,\"U\":2,\"oi\":\"client-1\",\"os\":0,\"ci\":\"c1\",\"s\":\"BTC-USDT\","
            + "\"oT\":\"limit\",\"q\":\"0.001\",\"p\":\"90000\",\"qU\":\"90\",\"aP\":\"91000\","
            + "\"fS\":\"0.1\",\"f\":\"0.05\",\"fC\":\"USDT\",\"rS\":\"0.2\",\"cR\":\"R1\","
            + "\"tIF\":\"GTC\",\"pO\":true,\"rO\":false,\"mM\":\"cross\",\"pS\":\"new\","
            + "\"stp\":\"none\",\"lR\":\"L1\",\"ti\":\"t1\",\"toi\":\"t2\",\"t\":\"BUY\"}";

    UtaOrderData order = MAPPER.readValue(payload, UtaOrderData.class);

    assertEquals(1L, order.getO());
    assertEquals(2L, order.getU());
    assertEquals("limit", order.getOT());
    assertEquals("90", order.getQU());
    assertEquals(new BigDecimal("91000"), order.getAP());
    assertEquals(new BigDecimal("0.1"), order.getFS());
    assertEquals("USDT", order.getFC());
    assertEquals(new BigDecimal("0.2"), order.getRS());
    assertEquals("R1", order.getCR());
    assertEquals("GTC", order.getTIF());
    assertEquals(Boolean.TRUE, order.getPO());
    assertEquals(Boolean.FALSE, order.getRO());
    assertEquals("cross", order.getMM());
    assertEquals("new", order.getPS());
    assertEquals("L1", order.getLR());
    // control fields with lowercase names
    assertEquals("BTC-USDT", order.getS());
    assertEquals(new BigDecimal("0.001"), order.getQ());
    assertEquals(new BigDecimal("90000"), order.getP());
    assertEquals(new BigDecimal("0.05"), order.getF());
    assertEquals("BUY", order.getT());
  }

  @Test
  void serializesWithExactCaseKeys() throws Exception {
    UtaOrderData order = new UtaOrderData();
    order.setO(1L);
    order.setU(2L);
    order.setOT("x");
    order.setQU("90");
    order.setAP(new BigDecimal("1"));
    order.setFS(new BigDecimal("0.1"));
    order.setFC("USDT");
    order.setRS(new BigDecimal("0.2"));
    order.setCR("r");
    order.setTIF("GTC");
    order.setPO(true);
    order.setRO(false);
    order.setMM("m");
    order.setPS("p");
    order.setLR("l");

    String json = MAPPER.writeValueAsString(order);

    assertTrue(json.contains("\"O\":1"), json);
    assertTrue(json.contains("\"U\":2"), json);
    assertTrue(json.contains("\"oT\":\"x\""), json);
    assertTrue(json.contains("\"qU\":\"90\""), json);
    assertTrue(json.contains("\"aP\":1"), json);
    assertTrue(json.contains("\"fS\":0.1"), json);
    assertTrue(json.contains("\"fC\":\"USDT\""), json);
    assertTrue(json.contains("\"rS\":0.2"), json);
    assertTrue(json.contains("\"cR\":\"r\""), json);
    assertTrue(json.contains("\"tIF\":\"GTC\""), json);
    assertTrue(json.contains("\"pO\":true"), json);
    assertTrue(json.contains("\"rO\":false"), json);
    assertTrue(json.contains("\"mM\":\"m\""), json);
    assertTrue(json.contains("\"pS\":\"p\""), json);
    assertTrue(json.contains("\"lR\":\"l\""), json);
    // implicit accessor properties (legacy-mangled keys) must merge into the
    // explicit @JsonProperty names instead of being emitted alongside them
    assertFalse(json.contains("\"o\":"), json);
    assertFalse(json.contains("\"u\":"), json);
    assertFalse(json.contains("\"ot\":"), json);
    assertFalse(json.contains("\"qu\":"), json);
    assertFalse(json.contains("\"ap\":"), json);
    assertFalse(json.contains("\"fs\":"), json);
    assertFalse(json.contains("\"fc\":"), json);
    assertFalse(json.contains("\"rs\":"), json);
    assertFalse(json.contains("\"cr\":"), json);
    assertFalse(json.contains("\"tif\":"), json);
    assertFalse(json.contains("\"po\":"), json);
    assertFalse(json.contains("\"ro\":"), json);
    assertFalse(json.contains("\"mm\":"), json);
    assertFalse(json.contains("\"ps\":"), json);
    assertFalse(json.contains("\"lr\":"), json);
  }

  @Test
  void parsesOrderBookPayload() throws Exception {
    String payload =
        "{\"O\":1,\"C\":2,\"M\":3,\"s\":\"BTC-USDT\","
            + "\"b\":[[\"90000\",\"0.1\"]],\"a\":[[\"90001\",\"0.2\"]]}";

    UtaWsFrame.OrderBookData book = MAPPER.readValue(payload, UtaWsFrame.OrderBookData.class);

    assertEquals(1L, book.getO());
    assertEquals(2L, book.getC());
    assertEquals(3L, book.getM());
    assertEquals("BTC-USDT", book.getS());
    assertEquals(1, book.getB().size());
    assertEquals(1, book.getA().size());
  }
}
