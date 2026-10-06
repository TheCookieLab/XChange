package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexNewOfferRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("currency")
  protected String currency;

  @JsonProperty("amount")
  protected BigDecimal amount;

  @JsonProperty("rate")
  protected BigDecimal rate;

  @JsonProperty("period")
  protected int period;

  @JsonProperty("direction")
  protected String direction;

  public BitfinexNewOfferRequest(
      String currency, BigDecimal amount, BigDecimal rate, int period, String direction) {

    this.request = "/v1/offer/new";
    this.currency = currency;
    this.amount = amount;
    this.rate = rate;
    this.period = period;
    this.direction = direction;
  }

  public String getRequest() {

    return request;
  }

  public void setRequest(String request) {

    this.request = request;
  }

  public String getCurrency() {

    return currency;
  }

  public String getAmount() {

    return amount.toPlainString();
  }

  public String getRate() {

    return rate.toPlainString();
  }

  public int getPeriod() {

    return period;
  }

  public String getDirection() {

    return direction;
  }
}
