package org.knowm.xchange.cryptocom.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One order-book dataframe. Each entry in {@code bids}/{@code asks} is {@code [price, quantity,
 * numberOfOrders]}.
 *
 * <p>The official v1 book channel contract attaches a sequence chain to every dataframe:
 *
 * <ul>
 *   <li>a full snapshot carries {@code u} only - the update id, unique and incremental per
 *       instrument per WebSocket session;
 *   <li>a partial update carries both {@code u} and {@code pu} (the previous update id the
 *       client must have applied just before this one).
 * </ul>
 *
 * An update shall only be applied when {@code pu} equals the {@code u} of the last applied
 * dataframe; otherwise the client must rebuild from a fresh snapshot. See {@code
 * CryptoComOrderBookAssembler}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComOrderBookData {

  @JsonProperty("bids")
  private List<List<String>> bids;

  @JsonProperty("asks")
  private List<List<String>> asks;

  @JsonProperty("t")
  private Long timestamp;

  /** Update id of this dataframe, unique and incremental per instrument per WebSocket session. */
  @JsonProperty("u")
  private Long sequence;

  /** Id of the previous update that must have been applied before this one (partial updates). */
  @JsonProperty("pu")
  private Long previousSequence;
}
