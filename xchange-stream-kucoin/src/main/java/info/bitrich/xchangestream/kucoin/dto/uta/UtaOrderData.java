package info.bitrich.xchangestream.kucoin.dto.uta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** UTA private order event payload {@code {O, U, oi, os, ci, s, oT, q, p, ...}}. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtaOrderData {

  @JsonProperty("O")
  private Long O;
  @JsonProperty("U")
  private Long U;
  private String oi;
  private Integer os;
  private String ci;
  private String s;
  @JsonProperty("oT")
  private String oT;
  private BigDecimal q;
  private BigDecimal p;
  @JsonProperty("qU")
  private String qU;
  @JsonProperty("aP")
  private BigDecimal aP;
  @JsonProperty("fS")
  private BigDecimal fS;
  private BigDecimal f;
  @JsonProperty("fC")
  private String fC;
  @JsonProperty("rS")
  private BigDecimal rS;
  @JsonProperty("cR")
  private String cR;
  @JsonProperty("tIF")
  private String tIF;
  @JsonProperty("pO")
  private Boolean pO;
  @JsonProperty("rO")
  private Boolean rO;
  @JsonProperty("mM")
  private String mM;
  @JsonProperty("pS")
  private String pS;
  private String stp;
  @JsonProperty("lR")
  private String lR;
  private String ti;
  private String toi;
  private String t;
}
