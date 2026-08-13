package org.knowm.xchange.gateio.dto.marketdata;

import java.math.BigDecimal;
import java.util.List;
import lombok.Value;

/**
 * One spot candlestick (GET /spot/candlesticks).
 *
 * <p>The provider returns an array-of-arrays where each row is {@code [t, v_q, c, h, l, o, v_b,
 * closed]} per the pinned wire contract: start time (Unix seconds), quote-currency volume, close,
 * high, low, open, base-currency volume, and a window-complete flag. All numbers arrive as
 * strings; the closed flag may arrive as a JSON boolean, which Jackson coerces to {@code "true" /
 * "false"}.
 */
@Value
public class GateioCandleStick {

  /** Start time of the candle, Unix seconds. */
  long time;

  /** Trading volume in quote currency. */
  BigDecimal quoteVolume;

  /** Close price. */
  BigDecimal close;

  /** Highest price. */
  BigDecimal high;

  /** Lowest price. */
  BigDecimal low;

  /** Open price. */
  BigDecimal open;

  /** Trading volume in base currency. */
  BigDecimal baseVolume;

  /** Whether the candlestick window is complete. */
  boolean closed;

  /** Parses one provider row; index order is fixed by the pinned wire contract. */
  public static GateioCandleStick fromRow(List<String> row) {
    if (row.size() < 8) {
      throw new IllegalArgumentException(
          "candlestick row must have 8 elements, got " + row.size());
    }
    return new GateioCandleStick(
        Long.parseLong(row.get(0)),
        new BigDecimal(row.get(1)),
        new BigDecimal(row.get(2)),
        new BigDecimal(row.get(3)),
        new BigDecimal(row.get(4)),
        new BigDecimal(row.get(5)),
        new BigDecimal(row.get(6)),
        Boolean.parseBoolean(row.get(7)));
  }
}
