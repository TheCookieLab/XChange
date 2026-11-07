package org.knowm.xchange.kraken.dto.trade;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.dto.Order.IOrderFlags;

public enum KrakenOrderFlags implements IOrderFlags {
  FCIB, // prefer fee in base currency
  FCIQ, // prefer fee in quote currency
  NOMPP, // no market price protection
  POST, // for market maker orders
  VIQC; // volume in quote currency

  private static final Map<String, KrakenOrderFlags> fromString = new HashMap<>();

  static {
    for (KrakenOrderFlags orderFlag : values()) fromString.put(orderFlag.toString(), orderFlag);
  }

  public static KrakenOrderFlags fromString(String orderTypeString) {

    return fromString.get(orderTypeString.toLowerCase());
  }

  @Override
  public String toString() {

    return super.toString().toLowerCase();
  }

  static class KrakenOrderFlagsDeserializer extends JsonDeserializer<Set<KrakenOrderFlags>> {

    @Override
    public Set<KrakenOrderFlags> deserialize(JsonParser jsonParser, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {

      // Performance optimization: Avoid building Jackson JsonNode AST instances on high-frequency streaming paths.
      // Direct getText() streams string tokens without intermediate TextNode allocation.
      String orderFlagsString = jsonParser.getText();
      Set<KrakenOrderFlags> orderFlags = EnumSet.noneOf(KrakenOrderFlags.class);
      if (orderFlagsString == null || orderFlagsString.isEmpty()) {
        return orderFlags;
      }

      // Performance optimization: Avoid String.split(",") regex overhead and Matcher/String[] allocations.
      // Use index scanning with indexOf(',') to parse comma-delimited tokens with minimal allocations.
      int start = 0;
      int commaIndex;
      while ((commaIndex = orderFlagsString.indexOf(',', start)) != -1) {
        if (commaIndex > start) {
          KrakenOrderFlags flag = fromString(orderFlagsString.substring(start, commaIndex).trim());
          if (flag != null) {
            orderFlags.add(flag);
          }
        }
        start = commaIndex + 1;
      }
      if (start < orderFlagsString.length()) {
        KrakenOrderFlags flag = fromString(orderFlagsString.substring(start).trim());
        if (flag != null) {
          orderFlags.add(flag);
        }
      }
      return orderFlags;
    }
  }
}
