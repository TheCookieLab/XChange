package info.bitrich.xchangestream.kucoin.dto.uta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** UTA private order event payload {@code {O, U, oi, os, ci, s, oT, q, p, ...}}. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtaOrderData {

  private Long O;
  private Long U;
  private String oi;
  private Integer os;
  private String ci;
  private String s;
  private String oT;
  private BigDecimal q;
  private BigDecimal p;
  private String qU;
  private BigDecimal aP;
  private BigDecimal fS;
  private BigDecimal f;
  private String fC;
  private BigDecimal rS;
  private String cR;
  private String tIF;
  private Boolean pO;
  private Boolean rO;
  private String mM;
  private String pS;
  private String stp;
  private String lR;
  private String ti;
  private String toi;
  private String t;
}
