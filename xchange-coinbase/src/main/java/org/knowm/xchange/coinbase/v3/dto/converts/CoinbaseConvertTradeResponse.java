package org.knowm.xchange.coinbase.v3.dto.converts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Response from committing or getting a convert trade.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_commitconverttrade">Commit Convert Trade</a>
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getconverttrade">Get Convert Trade</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseConvertTradeResponse {

  private final CoinbaseConvertTrade trade;

  @JsonCreator
  public CoinbaseConvertTradeResponse(@JsonProperty("trade") CoinbaseConvertTrade trade) {
    this.trade = trade;
  }

  @Override
  public String toString() {
    return "CoinbaseConvertTradeResponse [trade=" + (trade == null ? null : trade.getId()) + "]";
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CoinbaseConvertTrade {
    private final String id;
    private final String status;
    private final String fromAccount;
    private final String toAccount;
    private final BigDecimal fromAmount;
    private final BigDecimal toAmount;

    @JsonCreator
    public CoinbaseConvertTrade(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("from_account") String fromAccount,
        @JsonProperty("to_account") String toAccount,
        @JsonProperty("from_amount") BigDecimal fromAmount,
        @JsonProperty("to_amount") BigDecimal toAmount) {
      this.id = id;
      this.status = status;
      this.fromAccount = fromAccount;
      this.toAccount = toAccount;
      this.fromAmount = fromAmount;
      this.toAmount = toAmount;
    }
  }
}

