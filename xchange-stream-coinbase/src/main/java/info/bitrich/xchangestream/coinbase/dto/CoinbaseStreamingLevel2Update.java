package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingLevel2Update {

  private final String side;
  private final BigDecimal priceLevel;
  private final BigDecimal newQuantity;

  @JsonCreator
  public CoinbaseStreamingLevel2Update(
      @JsonProperty("side") String side,
      @JsonProperty("price_level") BigDecimal priceLevel,
      @JsonProperty("new_quantity") BigDecimal newQuantity) {
    this.side = side;
    this.priceLevel = priceLevel;
    this.newQuantity = newQuantity;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getPriceLevel() {
    return priceLevel;
  }

  public BigDecimal getNewQuantity() {
    return newQuantity;
  }
}
