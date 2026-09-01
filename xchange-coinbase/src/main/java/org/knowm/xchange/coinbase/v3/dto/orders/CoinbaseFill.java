package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.Getter;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.instrument.Instrument;

/**
 * A fill from Advanced Trade's historical fills endpoint.
 *
 * <p>Both trade time and sequence time are retained because sequence time is the authoritative
 * pagination watermark. Commission is the exchange-reported amount and is never estimated here.
 */
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
  private final Date sequenceTimestamp;
  private final String liquidityIndicator;
  private final boolean sizeInQuote;
  private final String userId;
  private final String side;
  private final String retailPortfolioId;

  /**
   * Creates a fill from the current Advanced Trade wire fields.
   *
   * @param entryId ledger entry identifier
   * @param tradeId trade identifier
   * @param orderId parent order identifier
   * @param tradeTime execution timestamp
   * @param tradeType exchange trade classification
   * @param price execution price
   * @param size execution quantity
   * @param commission execution fee
   * @param productId canonical product identifier
   * @param sequenceTimestamp response sequence timestamp
   * @param liquidityIndicator maker/taker classification
   * @param sizeInQuote whether the reported size uses quote units
   * @param userId Coinbase user identifier
   * @param side execution side
   * @param retailPortfolioId portfolio identifier
   */
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
      @JsonProperty("sequence_timestamp") String sequenceTimestamp,
      @JsonProperty("liquidity_indicator") String liquidityIndicator,
      @JsonProperty("size_in_quote") boolean sizeInQuote,
      @JsonProperty("user_id") String userId,
      @JsonProperty("side") String side,
      @JsonProperty("retail_portfolio_id") String retailPortfolioId) {
    this.entryId = entryId;
    this.tradeId = tradeId;
    this.orderId = orderId;
    this.tradeTime = parseTimestamp(tradeTime);
    this.tradeType = tradeType;
    this.price = price;
    this.size = size;
    this.commission = commission;
    this.productId = productId;
    this.sequenceTimestamp = parseTimestamp(sequenceTimestamp);
    this.liquidityIndicator = liquidityIndicator;
    this.sizeInQuote = sizeInQuote;
    this.userId = userId;
    this.side = side;
    this.retailPortfolioId = retailPortfolioId;
  }

  /**
   * Preserves the pre-1.0.2 fill construction contract.
   *
   * @deprecated use the constructor that retains {@code sequence_timestamp}
   */
  @Deprecated
  public CoinbaseFill(
      String entryId,
      String tradeId,
      String orderId,
      String tradeTime,
      String tradeType,
      BigDecimal price,
      BigDecimal size,
      BigDecimal commission,
      String productId,
      String liquidityIndicator,
      boolean sizeInQuote,
      String userId,
      String side,
      String retailPortfolioId) {
    this(
        entryId,
        tradeId,
        orderId,
        tradeTime,
        tradeType,
        price,
        size,
        commission,
        productId,
        null,
        liquidityIndicator,
        sizeInQuote,
        userId,
        side,
        retailPortfolioId);
  }

  private static Date parseTimestamp(String value) {
    return value == null || value.isBlank()
        ? null
        : Date.from(DateTimeFormatter.ISO_INSTANT.parse(value, Instant::from));
  }

  /**
   * @return the XChange order type inferred from the API side value.
   */
  public Order.OrderType getOrderType() {
    return CoinbaseAdapters.adaptOrderType(side);
  }

  /**
   * @return the instrument identified by the canonical Coinbase product id.
   */
  public Instrument getInstrument() {
    return CoinbaseAdapters.adaptInstrument(productId);
  }

  /**
   * Returns the commission currency encoded by Coinbase's product conventions.
   *
   * <p>Pair-shaped spot identifiers use their second component. CFM/CFMF and Coinbase Derivatives
   * Exchange futures are USD-settled, so their expiry-bearing product identifiers must not be
   * interpreted as currency pairs. Coinbase International perpetuals are USDC-settled.
   */
  public Currency getFeeCurrency() {
    if (productId == null) {
      return null;
    }
    String normalized = productId.trim().toUpperCase(java.util.Locale.ROOT);
    if (normalized.endsWith("-CDE")
        || normalized.endsWith("-CFM")
        || normalized.endsWith("-CFMF")) {
      return Currency.USD;
    }
    if (normalized.endsWith("-INTX")) {
      return Currency.USDC;
    }
    String[] tokens = normalized.split("-");
    return tokens.length >= 2 ? Currency.getInstance(tokens[1]) : null;
  }
}
