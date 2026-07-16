package info.bitrich.xchangestream.coinbasederivatives;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded reconnect delay that defensively recognizes provider credit metadata and errors. */
final class CreditAwareBackoff {

  private static final Duration MINIMUM = Duration.ofSeconds(1);
  private static final Duration MAXIMUM = Duration.ofSeconds(30);
  private final AtomicInteger consecutiveCreditFailures = new AtomicInteger();

  boolean isCreditFailure(JsonNode message) {
    if (message == null) {
      return false;
    }
    if (containsCreditExhaustion(message)) {
      consecutiveCreditFailures.incrementAndGet();
      return true;
    }
    return false;
  }

  Duration nextDelay() {
    int exponent = Math.min(5, Math.max(0, consecutiveCreditFailures.get() - 1));
    return MINIMUM.multipliedBy(1L << exponent).compareTo(MAXIMUM) > 0
        ? MAXIMUM
        : MINIMUM.multipliedBy(1L << exponent);
  }

  void recovered() {
    consecutiveCreditFailures.set(0);
  }

  private boolean containsCreditExhaustion(JsonNode node) {
    if (node.isTextual()) {
      String value = node.textValue().toLowerCase();
      return value.contains("credit")
          && (value.contains("exhaust")
              || value.contains("insufficient")
              || value.contains("rate"));
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String name = field.getKey().toLowerCase();
        JsonNode value = field.getValue();
        if (name.contains("credit") && value.isNumber() && value.decimalValue().signum() <= 0) {
          return true;
        }
        if (containsCreditExhaustion(value)) {
          return true;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        if (containsCreditExhaustion(child)) {
          return true;
        }
      }
    }
    return false;
  }
}
