package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;

/**
 * Bitget UTA v3 fill (private trade) push payload from the {@code fill} channel.
 *
 * <p>The WebSocket fill channel pushes one entry per executed fill with {@code execTime} as the
 * timestamp and a per-fill {@code feeDetail}; the REST {@code trade/fills} endpoint uses {@code
 * createdTime} and {@code feeDetail} per fill, which is why this DTO is distinct from the REST fill
 * DTO. The channel is account-wide (no symbol in the subscription argument); the pushed {@code
 * symbol} and {@code category} identify the instrument per fill.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3FillData {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("updatedTime")
  private Long updatedTime;

  @JsonProperty("side")
  private String side;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("execPnl")
  private BigDecimal execPnl;

  @JsonProperty("feeDetail")
  private List<BitgetUtaV3Order.BitgetUtaV3Fee> feeDetail;

  @JsonProperty("execTime")
  private Long execTime;

  @JsonProperty("tradeScope")
  private String tradeScope;

  @JsonProperty("tradeSide")
  private String tradeSide;

  @JsonProperty("execId")
  private String execId;

  @JsonProperty("execLinkId")
  private String execLinkId;

  @JsonProperty("execPrice")
  private BigDecimal execPrice;

  @JsonProperty("holdSide")
  private String holdSide;

  @JsonProperty("execValue")
  private BigDecimal execValue;

  @JsonProperty("category")
  private String category;

  @JsonProperty("execQty")
  private BigDecimal execQty;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("isRPI")
  private String isRPI;
}
