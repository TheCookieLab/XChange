package info.bitrich.xchangestream.kucoin.dto.uta;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonProperty("T")
  private String topic;

  @JsonProperty("P")
  private Long timestamp;

  @JsonProperty("t")
  private String type;

  @JsonProperty("dp")
  private String depth;

  @JsonProperty("d")
  private JsonNode data;

  /** @return the channel portion of the topic (before the dot), or {@code null} */
  public String channel() {
    if (topic == null) {
      return null;
    }
    int dot = topic.indexOf('.');
    return dot < 0 ? topic : topic.substring(0, dot);
  }

  /** @return the tradeType portion of the topic (after the dot), or {@code null} */
  public String tradeType() {
    if (topic == null) {
      return null;
    }
    int dot = topic.indexOf('.');
    return dot < 0 ? null : topic.substring(dot + 1);
  }

  public String symbol() {
    return data == null ? null : data.path("s").asText(null);
  }

  public boolean isSnapshot() {
    return "snapshot".equals(type);
  }

  public boolean isDelta() {
    return "delta".equals(type);
  }

  /** Order book payload: {@code {O, C, M, s, b: [[price,size]...], a: [[price,size]...]}}. */
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonAutoDetect(
      fieldVisibility = JsonAutoDetect.Visibility.ANY,
      getterVisibility = JsonAutoDetect.Visibility.NONE,
      isGetterVisibility = JsonAutoDetect.Visibility.NONE,
      setterVisibility = JsonAutoDetect.Visibility.NONE)
  public static class OrderBookData {
    @JsonProperty("O")
    private Long O;
    @JsonProperty("C")
    private Long C;
    @JsonProperty("M")
    private Long M;
    private String s;
    private List<List<BigDecimal>> b;
    private List<List<BigDecimal>> a;
  }

  /** Ticker payload: {@code {a, A, b, B, l, q, s, ...}}. */
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TickerData {
    @JsonProperty("a")
    private BigDecimal ask;

    @JsonProperty("A")
    private BigDecimal askSize;

    @JsonProperty("b")
    private BigDecimal bid;

    @JsonProperty("B")
    private BigDecimal bidSize;

    @JsonProperty("l")
    private BigDecimal last;

    @JsonProperty("q")
    private BigDecimal lastSize;

    @JsonProperty("s")
    private String symbol;
  }
}
