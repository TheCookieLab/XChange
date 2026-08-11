package info.bitrich.xchangestream.kucoin.dto.uta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/** UTA private order event payload {@code {O, U, oi, os, ci, s, oT, q, p, ...}}. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtaOrderData {

  @JsonProperty("O")
  @Getter(onMethod_ = @JsonProperty("O"))
  @Setter(onMethod_ = @JsonProperty("O"))
  private Long O;
  @JsonProperty("U")
  @Getter(onMethod_ = @JsonProperty("U"))
  @Setter(onMethod_ = @JsonProperty("U"))
  private Long U;
  private String oi;
  private Integer os;
  private String ci;
  private String s;
  @JsonProperty("oT")
  @Getter(onMethod_ = @JsonProperty("oT"))
  @Setter(onMethod_ = @JsonProperty("oT"))
  private String oT;
  private BigDecimal q;
  private BigDecimal p;
  @JsonProperty("qU")
  @Getter(onMethod_ = @JsonProperty("qU"))
  @Setter(onMethod_ = @JsonProperty("qU"))
  private String qU;
  @JsonProperty("aP")
  @Getter(onMethod_ = @JsonProperty("aP"))
  @Setter(onMethod_ = @JsonProperty("aP"))
  private BigDecimal aP;
  @JsonProperty("fS")
  @Getter(onMethod_ = @JsonProperty("fS"))
  @Setter(onMethod_ = @JsonProperty("fS"))
  private BigDecimal fS;
  private BigDecimal f;
  @JsonProperty("fC")
  @Getter(onMethod_ = @JsonProperty("fC"))
  @Setter(onMethod_ = @JsonProperty("fC"))
  private String fC;
  @JsonProperty("rS")
  @Getter(onMethod_ = @JsonProperty("rS"))
  @Setter(onMethod_ = @JsonProperty("rS"))
  private BigDecimal rS;
  @JsonProperty("cR")
  @Getter(onMethod_ = @JsonProperty("cR"))
  @Setter(onMethod_ = @JsonProperty("cR"))
  private String cR;
  @JsonProperty("tIF")
  @Getter(onMethod_ = @JsonProperty("tIF"))
  @Setter(onMethod_ = @JsonProperty("tIF"))
  private String tIF;
  @JsonProperty("pO")
  @Getter(onMethod_ = @JsonProperty("pO"))
  @Setter(onMethod_ = @JsonProperty("pO"))
  private Boolean pO;
  @JsonProperty("rO")
  @Getter(onMethod_ = @JsonProperty("rO"))
  @Setter(onMethod_ = @JsonProperty("rO"))
  private Boolean rO;
  @JsonProperty("mM")
  @Getter(onMethod_ = @JsonProperty("mM"))
  @Setter(onMethod_ = @JsonProperty("mM"))
  private String mM;
  @JsonProperty("pS")
  @Getter(onMethod_ = @JsonProperty("pS"))
  @Setter(onMethod_ = @JsonProperty("pS"))
  private String pS;
  private String stp;
  @JsonProperty("lR")
  @Getter(onMethod_ = @JsonProperty("lR"))
  @Setter(onMethod_ = @JsonProperty("lR"))
  private String lR;
  private String ti;
  private String toi;
  private String t;
}
