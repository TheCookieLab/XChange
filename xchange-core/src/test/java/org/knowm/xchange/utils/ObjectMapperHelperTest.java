package org.knowm.xchange.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import org.junit.Test;

public class ObjectMapperHelperTest {

  @JsonSerialize(using = FailingSerializer.class)
  private static class FailingObject {}

  private static class FailingSerializer extends JsonSerializer<FailingObject> {
    @Override
    public void serialize(
        FailingObject value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      throw new IOException("Simulated serialization failure");
    }
  }

  @Test
  public void testToJSONWithSerializationError() {
    FailingObject failingObject = new FailingObject();
    String result = ObjectMapperHelper.toJSON(failingObject);
    assertThat(result).contains("Problem serializing");
  }

  @Test
  public void testToCompactJSONWithSerializationError() {
    FailingObject failingObject = new FailingObject();
    String result = ObjectMapperHelper.toCompactJSON(failingObject);
    assertThat(result).contains("Problem serializing");
  }
}
