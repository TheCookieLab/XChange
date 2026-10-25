package org.knowm.xchange.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

public class ObjectMapperHelperTest {

  public static class TestBean {
    private String name;

    public TestBean() {}

    public TestBean(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class SelfReferentialBean {
    private SelfReferentialBean self = this;

    public SelfReferentialBean getSelf() {
      return self;
    }
  }

  @Test
  public void testToJSON() {
    TestBean bean = new TestBean("test");
    String json = ObjectMapperHelper.toJSON(bean);
    assertNotNull(json);
    assertTrue(json.contains("name") && json.contains("test"));
  }

  @Test
  public void testToCompactJSON() {
    TestBean bean = new TestBean("test");
    String json = ObjectMapperHelper.toCompactJSON(bean);
    assertNotNull(json);
    assertEquals("{\"name\":\"test\"}", json);
  }

  @Test
  public void testToJSONWithUnserializableObject() {
    SelfReferentialBean bean = new SelfReferentialBean();
    String json = ObjectMapperHelper.toJSON(bean);
    assertTrue(json.startsWith("Problem serializing"));
  }

  @Test
  public void testViaJSON() throws IOException {
    TestBean bean = new TestBean("test");
    TestBean copy = ObjectMapperHelper.viaJSON(bean);
    assertNotNull(copy);
    assertEquals("test", copy.getName());
  }
}
