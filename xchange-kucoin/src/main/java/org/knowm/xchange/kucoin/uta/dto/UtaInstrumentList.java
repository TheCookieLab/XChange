package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** Instrument catalog response carrying the authoritative {@code tradeType} for the whole list. */
@Data
public class UtaInstrumentList {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("list")
  private List<UtaInstrument> list;
}
