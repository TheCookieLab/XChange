package org.knowm.xchange.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

  public static class FailingBean {
    public String getValue() {
      throw new IllegalStateException("test-redaction-canary");
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

  @Test
  public void testLoggingDoesNotExposeValuesOrSerializationExceptions() throws IOException {
    Logger logger = (Logger) LoggerFactory.getLogger(ObjectMapperHelper.class);
    Level originalLevel = logger.getLevel();
    boolean originalAdditive = logger.isAdditive();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    try {
      TestBean copy = ObjectMapperHelper.viaJSON(new TestBean("test-redaction-canary"));
      assertEquals("test-redaction-canary", copy.getName());
      assertTrue(ObjectMapperHelper.toJSON(new FailingBean()).startsWith("Problem serializing"));

      assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR));
      for (ILoggingEvent event : appender.list) {
        assertFalse(event.getFormattedMessage().contains("test-redaction-canary"));
        assertNull(event.getThrowableProxy());
      }
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      logger.setAdditive(originalAdditive);
      appender.stop();
    }
  }
}
