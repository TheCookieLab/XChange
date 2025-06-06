package org.knowm.xchange.bitso.dto.marketdata;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/** DTO for Bitso API v3 trades endpoint */
@Value
@Jacksonized
@Builder
public class BitsoTrades {

  private Boolean success;

  private List<BitsoTrade> payload;

  @Value
  @Jacksonized
  @Builder
  public static class BitsoTrade {

    private String book;

    private String createdAt;

    private String amount;

    private String makerSide;

    private String price;

    private Long tid;
  }
}
