package org.knowm.xchange.bitso.dto.marketdata;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/** DTO for Bitso API v3 available books endpoint */
@Value
@Jacksonized
@Builder
public class BitsoAvailableBooks {

  private Boolean success;

  private List<BitsoBook> payload;

  @Value
  @Jacksonized
  @Builder
  public static class BitsoBook {

    private String book;

    private String minimumAmount;

    private String maximumAmount;

    private String minimumPrice;

    private String maximumPrice;

    private String minimumValue;

    private String maximumValue;

    private String tickSize;

    // Additional fields that may be present in real API responses

    private String defaultChart;

    private Object fees; // Complex fee structure, using Object for flexibility
  }
}
