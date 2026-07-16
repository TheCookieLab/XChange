package org.knowm.xchange.coinbasederivatives.client;

import java.util.Map;

/** Provider credit-related metadata observed without assuming unpublished field names. */
public record RateCreditMetadata(Map<String, String> values) {
  public RateCreditMetadata {
    values = Map.copyOf(values);
  }
}
