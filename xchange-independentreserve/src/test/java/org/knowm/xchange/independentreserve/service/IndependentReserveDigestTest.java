package org.knowm.xchange.independentreserve.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.independentreserve.util.ExchangeEndpoint;
import org.slf4j.LoggerFactory;

public class IndependentReserveDigestTest {

  @Test
  public void testDigestParamsToString() {
    String secretKey = "c2VjcmV0S2V5RGF0YQ==";
    String apiKey = "test-api-key-123";
    String sslUri = "https://api.independentreserve.com";

    IndependentReserveDigest digest =
        IndependentReserveDigest.createInstance(secretKey, apiKey, sslUri);
    assertThat(digest).isNotNull();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("pageIndex", 1);

    String result = digest.digestParamsToString(ExchangeEndpoint.GET_ACCOUNTS, 123456789L, params);
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
  }

  @Test
  public void testDigestParamsToStringDoesNotLogApiKey() {
    String apiKey = "test-api-key-123";
    IndependentReserveDigest digest =
        IndependentReserveDigest.createInstance(
            "c2VjcmV0S2V5RGF0YQ==", apiKey, "https://api.independentreserve.com");

    Logger logger = (Logger) LoggerFactory.getLogger(IndependentReserveDigest.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
    try {
      digest.digestParamsToString(ExchangeEndpoint.GET_ACCOUNTS, 123456789L, new LinkedHashMap<>());
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(messages).anyMatch(message -> message.contains("[REDACTED]"));
    assertThat(messages).noneMatch(message -> message.contains(apiKey));
  }

  @Test
  public void testNullSecretKeyReturnsNullInstance() {
    IndependentReserveDigest digest =
        IndependentReserveDigest.createInstance(null, "key", "https://api.independentreserve.com");
    assertThat(digest).isNull();
  }
}
