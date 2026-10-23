package org.knowm.xchange.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import org.junit.Test;

public class ObjectMapperHelperTest {

  public static class DummyPojo {
    private String name;

    public DummyPojo() {}

    public DummyPojo(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @JsonSerialize(using = FailingSerializer.class)
  public static class FailingPojo {}

  public static class FailingSerializer extends JsonSerializer<FailingPojo> {
    @Override
    public void serialize(
        FailingPojo value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      throw new IOException("Simulated serialization failure");
    }
  }

  @Test
  public void testToJSONAndReadValue() throws Exception {
    DummyPojo pojo = new DummyPojo("testName");
    String json = ObjectMapperHelper.toJSON(pojo);
    assertThat(json).contains("\"name\" : \"testName\"");

    String compactJson = ObjectMapperHelper.toCompactJSON(pojo);
    assertThat(compactJson).isEqualTo("{\"name\":\"testName\"}");

    DummyPojo deserialized = ObjectMapperHelper.readValue(compactJson, DummyPojo.class);
    assertThat(deserialized.getName()).isEqualTo("testName");
  }

  @Test
  public void testViaJSON() throws Exception {
    DummyPojo pojo = new DummyPojo("roundtrip");
    DummyPojo copy = ObjectMapperHelper.viaJSON(pojo);
    assertThat(copy.getName()).isEqualTo("roundtrip");
  }

  @Test
  public void testSerializationErrorHandling() {
    FailingPojo failingPojo = new FailingPojo();
    String result = ObjectMapperHelper.toJSON(failingPojo);
    assertThat(result).startsWith("Problem serializing class ");
  }
}
