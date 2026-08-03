package org.knowm.xchange.prediction;

/**
 * Typed outcome side for prediction-market contracts.
 *
 * <p>Prediction-market providers quote binary event markets from a YES/NO perspective rather than
 * a base/counter perspective. Adapters use this enum to map provider-native order sides (for
 * example Kalshi YES-side bids or Polymarket outcome-token buys) to generic XChange order types
 * without silently treating NO exposure as a spot sell.
 */
public enum PredictionOutcomeSide {

  /** The affirmative outcome of an event market (for example "the event happens"). */
  YES,

  /** The negative outcome of an event market (for example "the event does not happen"). */
  NO;

  /**
   * Parses an outcome side from provider text, case-insensitively.
   *
   * @param value provider-native side text such as {@code "yes"}, {@code "YES"}, or {@code "No"}
   * @return the matching outcome side
   * @throws IllegalArgumentException when the text is not a known outcome side
   */
  public static PredictionOutcomeSide fromString(String value) {
    if (value != null) {
      String normalized = value.trim();
      for (PredictionOutcomeSide side : values()) {
        if (side.name().equalsIgnoreCase(normalized)) {
          return side;
        }
      }
    }
    throw new IllegalArgumentException("Unknown prediction outcome side: " + value);
  }
}
