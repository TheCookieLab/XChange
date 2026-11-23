package org.knowm.xchange.kraken.dto.trade;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.dto.Order.IOrderFlags;

/** Kraken order flags; JSON deserialization rejects unknown non-empty flag names. */
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

      if (!jsonParser.hasToken(JsonToken.VALUE_STRING)) {
        return ctxt.reportInputMismatch(KrakenOrderFlags.class, "Expected a Kraken order flags string");
      }
      String orderFlagsString = jsonParser.getText();
      Set<KrakenOrderFlags> orderFlags = EnumSet.noneOf(KrakenOrderFlags.class);
      if (orderFlagsString == null || orderFlagsString.isEmpty()) {
        return orderFlags;
      }

      int start = 0;
      while (start < orderFlagsString.length()) {
        int commaIndex = orderFlagsString.indexOf(',', start);
        int end = commaIndex < 0 ? orderFlagsString.length() : commaIndex;
        String flagString = orderFlagsString.substring(start, end).trim();
        if (!flagString.isEmpty()) {
          KrakenOrderFlags flag = fromString(flagString);
          if (flag == null) {
            return ctxt.reportInputMismatch(
                KrakenOrderFlags.class, "Unknown Kraken order flag '%s'", flagString);
          }
          orderFlags.add(flag);
        }
        if (commaIndex < 0) {
          break;
        }
        start = commaIndex + 1;
      }
      return orderFlags;
    }
  }
}
