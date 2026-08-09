package org.knowm.xchange.krakenfutures.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.krakenfutures.dto.KrakenFuturesResult;

/**
 * An open futures position with full risk detail: mark/liq/index prices, unrealized and realized
 * PnL and funding, collateral, leverage, and margin requirements.
 *
 * @author Panchen
 */
@Getter
@ToString
public class KrakenFuturesOpenPosition extends KrakenFuturesResult {

  private final Date fillTime;
  private final String symbol;
  private final String instrument;
  private final String side;
  private final BigDecimal size;
  private final BigDecimal price;

  private final BigDecimal markPrice;
  private final BigDecimal limitPrice;
  private final BigDecimal liqPrice;
  private final BigDecimal unrealizedFunding;
  private final BigDecimal realizedFunding;
  private final BigDecimal unrealizedPnl;
  private final BigDecimal realizedPnl;
  private final BigDecimal collateral;
  private final BigDecimal leverage;
  private final BigDecimal margin;
  private final BigDecimal initialMargin;
  private final BigDecimal maintMargin;
  private final BigDecimal indexPrice;
  private final BigDecimal value;

  public KrakenFuturesOpenPosition(
      @JsonProperty("result") String result,
      @JsonProperty("error") String error,
      @JsonProperty("fillTime") Date fillTime,
      @JsonProperty("symbol") String symbol,
      @JsonProperty("instrument") String instrument,
      @JsonProperty("side") String side,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("markPrice") BigDecimal markPrice,
      @JsonProperty("limitPrice") BigDecimal limitPrice,
      @JsonProperty("liqPrice") BigDecimal liqPrice,
      @JsonProperty("unrealizedFunding") BigDecimal unrealizedFunding,
      @JsonProperty("realizedFunding") BigDecimal realizedFunding,
      @JsonProperty("unrealizedPnl") BigDecimal unrealizedPnl,
      @JsonProperty("realizedPnl") BigDecimal realizedPnl,
      @JsonProperty("collateral") BigDecimal collateral,
      @JsonProperty("leverage") BigDecimal leverage,
      @JsonProperty("margin") BigDecimal margin,
      @JsonProperty("initialMargin") BigDecimal initialMargin,
      @JsonProperty("maintMargin") BigDecimal maintMargin,
      @JsonProperty("indexPrice") BigDecimal indexPrice,
      @JsonProperty("value") BigDecimal value) {

    super(result, error);

    this.fillTime = fillTime;
    this.symbol = symbol;
    this.instrument = instrument;
    this.side = side;
    this.size = size;
    this.price = price;
    this.markPrice = markPrice;
    this.limitPrice = limitPrice;
    this.liqPrice = liqPrice;
    this.unrealizedFunding = unrealizedFunding;
    this.realizedFunding = realizedFunding;
    this.unrealizedPnl = unrealizedPnl;
    this.realizedPnl = realizedPnl;
    this.collateral = collateral;
    this.leverage = leverage;
    this.margin = margin;
    this.initialMargin = initialMargin;
    this.maintMargin = maintMargin;
    this.indexPrice = indexPrice;
    this.value = value;
  }
}
