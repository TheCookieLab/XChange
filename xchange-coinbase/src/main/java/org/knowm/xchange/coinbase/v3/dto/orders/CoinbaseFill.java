package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.Getter;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.coinbase.CoinbaseAdapters;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFill {

  private final String entryId;
  private final String tradeId;
  private final String orderId;
  private final Date tradeTime;
  private final String tradeType;
  private final BigDecimal price;
  private final BigDecimal size;
  private final BigDecimal commission;
  private final String productId;
  private final String liquidityIndicator;
  private final boolean sizeInQuote;
  private final String userId;
  private final String side;
  private final String retailPortfolioId;

  @JsonCreator
  public CoinbaseFill(
      @JsonProperty("entry_id") String entryId,
      @JsonProperty("trade_id") String tradeId,
      @JsonProperty("order_id") String orderId,
      @JsonProperty("trade_time") String tradeTime,
      @JsonProperty("trade_type") String tradeType,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("size") BigDecimal size,
      @JsonProperty("commission") BigDecimal commission,
      @JsonProperty("product_id") String productId,
      @JsonProperty("liquidity_indicator") String liquidityIndicator,
      @JsonProperty("size_in_quote") boolean sizeInQuote,
      @JsonProperty("user_id") String userId,
      @JsonProperty("side") String side,
      @JsonProperty("retail_portfolio_id") String retailPortfolioId) {

    this.entryId = entryId;
    this.tradeId = tradeId;
    this.orderId = orderId;
    this.tradeTime = tradeTime == null ? null
        : Date.from(DateTimeFormatter.ISO_INSTANT.parse(tradeTime, Instant::from));
    this.tradeType = tradeType;
    this.price = price;
    this.size = size;
    this.commission = commission;
    this.productId = productId;
    this.liquidityIndicator = liquidityIndicator;
    this.sizeInQuote = sizeInQuote;
    this.userId = userId;
    this.side = side;
    this.retailPortfolioId = retailPortfolioId;
  }

  public Order.OrderType getOrderType() {
    return CoinbaseAdapters.adaptOrderType(side);
  }

  public Instrument getInstrument() {
    return CoinbaseAdapters.adaptInstrument(productId);
  }

  public Currency getFeeCurrency() {
    // Commission currency is quote currency for spot
    if (productId == null) return null;
    String[] tokens = productId.split("-");
    return tokens.length == 2 ? Currency.getInstance(tokens[1]) : null;
  }
}


