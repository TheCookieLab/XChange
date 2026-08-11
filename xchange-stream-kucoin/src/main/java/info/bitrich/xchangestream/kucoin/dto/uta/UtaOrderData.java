package info.bitrich.xchangestream.kucoin.dto.uta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * UTA private order event payload {@code {O, U, oi, os, ci, s, oT, q, p, ...}}.
 *
 * <p>Accessors for the uppercase-initial fields are declared manually with the exact wire key:
 * Lombok's accessor-derived property names would be lowercased by Jackson's legacy bean mangling
 * ({@code O -> o}, {@code oT -> ot}), silently dropping the fields.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtaOrderData {

  @JsonProperty("O")
  private Long O;

  @JsonProperty("O")
  public Long getO() {
    return O;
  }

  @JsonProperty("O")
  public void setO(Long O) {
    this.O = O;
  }

  @JsonProperty("U")
  private Long U;

  @JsonProperty("U")
  public Long getU() {
    return U;
  }

  @JsonProperty("U")
  public void setU(Long U) {
    this.U = U;
  }

  private String oi;
  private Integer os;
  private String ci;
  private String s;

  @JsonProperty("oT")
  private String oT;

  @JsonProperty("oT")
  public String getOT() {
    return oT;
  }

  @JsonProperty("oT")
  public void setOT(String oT) {
    this.oT = oT;
  }

  private BigDecimal q;
  private BigDecimal p;

  @JsonProperty("qU")
  private String qU;

  @JsonProperty("qU")
  public String getQU() {
    return qU;
  }

  @JsonProperty("qU")
  public void setQU(String qU) {
    this.qU = qU;
  }

  @JsonProperty("aP")
  private BigDecimal aP;

  @JsonProperty("aP")
  public BigDecimal getAP() {
    return aP;
  }

  @JsonProperty("aP")
  public void setAP(BigDecimal aP) {
    this.aP = aP;
  }

  @JsonProperty("fS")
  private BigDecimal fS;

  @JsonProperty("fS")
  public BigDecimal getFS() {
    return fS;
  }

  @JsonProperty("fS")
  public void setFS(BigDecimal fS) {
    this.fS = fS;
  }

  private BigDecimal f;

  @JsonProperty("fC")
  private String fC;

  @JsonProperty("fC")
  public String getFC() {
    return fC;
  }

  @JsonProperty("fC")
  public void setFC(String fC) {
    this.fC = fC;
  }

  @JsonProperty("rS")
  private BigDecimal rS;

  @JsonProperty("rS")
  public BigDecimal getRS() {
    return rS;
  }

  @JsonProperty("rS")
  public void setRS(BigDecimal rS) {
    this.rS = rS;
  }

  @JsonProperty("cR")
  private String cR;

  @JsonProperty("cR")
  public String getCR() {
    return cR;
  }

  @JsonProperty("cR")
  public void setCR(String cR) {
    this.cR = cR;
  }

  @JsonProperty("tIF")
  private String tIF;

  @JsonProperty("tIF")
  public String getTIF() {
    return tIF;
  }

  @JsonProperty("tIF")
  public void setTIF(String tIF) {
    this.tIF = tIF;
  }

  @JsonProperty("pO")
  private Boolean pO;

  @JsonProperty("pO")
  public Boolean getPO() {
    return pO;
  }

  @JsonProperty("pO")
  public void setPO(Boolean pO) {
    this.pO = pO;
  }

  @JsonProperty("rO")
  private Boolean rO;

  @JsonProperty("rO")
  public Boolean getRO() {
    return rO;
  }

  @JsonProperty("rO")
  public void setRO(Boolean rO) {
    this.rO = rO;
  }

  @JsonProperty("mM")
  private String mM;

  @JsonProperty("mM")
  public String getMM() {
    return mM;
  }

  @JsonProperty("mM")
  public void setMM(String mM) {
    this.mM = mM;
  }

  @JsonProperty("pS")
  private String pS;

  @JsonProperty("pS")
  public String getPS() {
    return pS;
  }

  @JsonProperty("pS")
  public void setPS(String pS) {
    this.pS = pS;
  }

  private String stp;

  @JsonProperty("lR")
  private String lR;

  @JsonProperty("lR")
  public String getLR() {
    return lR;
  }

  @JsonProperty("lR")
  public void setLR(String lR) {
    this.lR = lR;
  }

  private String ti;
  private String toi;
  private String t;
}
