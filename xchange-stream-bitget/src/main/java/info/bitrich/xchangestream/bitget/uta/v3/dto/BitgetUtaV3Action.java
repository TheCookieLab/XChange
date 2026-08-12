package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Push action of a Bitget UTA v3 WebSocket notification.
 *
 * <p>v3 pushes carry {@code snapshot} (full state, e.g. order book rebuild or first position dump)
 * or {@code update} (incremental state change). Unlike the classic v2 feed, both values occur on
 * the same channel.
 *
 * @since 5.1.0
 */
public enum BitgetUtaV3Action {
  @JsonProperty("snapshot")
  SNAPSHOT,
  @JsonProperty("update")
  UPDATE
}
