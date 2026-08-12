package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Instrument family of a Bitget UTA v3 WebSocket channel {@code instType} argument.
 *
 * <p>Public channels use the market families ({@code spot}, {@code usdt-futures}, {@code
 * coin-futures}, {@code usdc-futures}); private channels (account/order/position) always use {@link
 * #UTA} since UTA v3 is a single unified account.
 *
 * @since 5.1.0
 */
@Getter
@AllArgsConstructor
public enum BitgetUtaV3InstType {
  SPOT("spot"),
  USDT_FUTURES("usdt-futures"),
  COIN_FUTURES("coin-futures"),
  USDC_FUTURES("usdc-futures"),
  /** Unified Trading Account private channels. */
  UTA("UTA");

  @JsonValue private final String wireName;
}
