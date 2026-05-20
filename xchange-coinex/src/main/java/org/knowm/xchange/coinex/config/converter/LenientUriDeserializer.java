package org.knowm.xchange.coinex.config.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.net.URI;

public class LenientUriDeserializer extends JsonDeserializer<URI> {

  @Override
  public URI deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String value = parser.getValueAsString();
    if (value == null) {
      return null;
    }

    try {
      return URI.create(value);
    } catch (IllegalArgumentException firstFailure) {
      String encodedTemplateValue = value.replace("{", "%7B").replace("}", "%7D");
      try {
        return URI.create(encodedTemplateValue);
      } catch (IllegalArgumentException secondFailure) {
        throw context.weirdStringException(value, URI.class, secondFailure.getMessage());
      }
    }
  }
}
