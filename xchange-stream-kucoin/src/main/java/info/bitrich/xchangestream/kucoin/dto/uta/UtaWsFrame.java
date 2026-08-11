package info.bitrich.xchangestream.kucoin.dto.uta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * UTA WebSocket frame: {@code {T, P, t?, dp?, d?}}.
 *
 * <p>{@code T} is the topic ("&lt;channel&gt;.&lt;tradeType&gt;", e.g. {@code obu.SPOT}); {@code P}
 * is a provider timestamp; {@code t} is {@code snapshot} or {@code delta} for order book pushes;
 * {@code dp} is the depth mode for {@code obu}; {@code d} carries the payload.
 *
 * @see <a href="https://www.kucoin.com/docs-new/websocket-api/base-info/introduction-uta">UTA
 *     WebSocket introduction</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtaWsFrame {

  private String T;
  private Long P;
  private String t;
  private String dp;
  private JsonNode d;

  /** @return the channel portion of {@code T} (before the dot), or {@code null} */
  public String channel() {
    if (T == null) {
      return null;
    }
    int dot = T.indexOf('.');
    return dot < 0 ? T : T.substring(0, dot);
  }

  /** @return the tradeType portion of {@code T} (after the dot), or {@code null} */
  public String tradeType() {
    if (T == null) {
      return null;
    }
    int dot = T.indexOf('.');
    return dot < 0 ? null : T.substring(dot + 1);
  }

  public String symbol() {
    return d == null ? null : d.path("s").asText(null);
  }

  public boolean isSnapshot() {
    return "snapshot".equals(t);
  }

  public boolean isDelta() {
    return "delta".equals(t);
  }

  /** Order book payload: {@code {O, C, M, s, b: [[price,size]...], a: [[price,size]...]}}. */
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OrderBookData {
    private Long O;
    private Long C;
    private Long M;
    private String s;
    private List<List<BigDecimal>> b;
    private List<List<BigDecimal>> a;
  }

  /** Ticker payload: {@code {a, A, b, B, l, q, s, ...}}. */
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TickerData {
    private BigDecimal a;
    private BigDecimal A;
    private BigDecimal b;
    private BigDecimal B;
    private BigDecimal l;
    private BigDecimal q;
    private String s;
  }
}
