package org.knowm.xchange.bitget.uta.v3.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Transfer request body for {@code POST /api/v3/account/transfer}.
 *
 * <p>Account type values: {@code uta}, {@code spot}, {@code p2p}, {@code usdt_futures}, {@code
 * coin_futures}, {@code usdc_futures}, {@code margin} (see provider docs for the current list).
 * {@code allowBorrow} applies to futures transfers; {@code symbol} is required for margin
 * transfers. {@code clientOid} is idempotency-scoped per clientOid.
 */
@Data
@Builder(toBuilder = true)
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitgetUtaV3TransferRequest {

  @JsonProperty("fromType")
  private String fromType;

  @JsonProperty("toType")
  private String toType;

  @JsonProperty("amount")
  private BigDecimal amount;

  @JsonProperty("coin")
  private String coin;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("allowBorrow")
  private String allowBorrow;

  @JsonProperty("clientOid")
  private String clientOid;
}
