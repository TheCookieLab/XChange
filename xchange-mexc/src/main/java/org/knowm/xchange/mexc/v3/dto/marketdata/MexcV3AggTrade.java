package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One aggregated public trade from {@code GET /api/v3/aggTrades}.
 *
 * <p>Wire fields are single letters: {@code a} aggregate trade id, {@code f}/{@code l} first/last
 * constituent trade id, {@code p} price, {@code q} quantity, {@code T} time, {@code m} buyer-maker
 * flag, {@code M} best-match flag.
 */
public class MexcV3AggTrade {

  private final long a;
  private final long f;
  private final long l;
  private final String p;
  private final String q;
  private final long T;
  private final boolean m;
  private final boolean M;

  public MexcV3AggTrade(
      @JsonProperty("a") long a,
      @JsonProperty("f") long f,
      @JsonProperty("l") long l,
      @JsonProperty("p") String p,
      @JsonProperty("q") String q,
      @JsonProperty("T") long T,
      @JsonProperty("m") boolean m,
      @JsonProperty("M") boolean M) {
    this.a = a;
    this.f = f;
    this.l = l;
    this.p = p;
    this.q = q;
    this.T = T;
    this.m = m;
    this.M = M;
  }

  public long getA() {
    return a;
  }

  public long getF() {
    return f;
  }

  public long getL() {
    return l;
  }

  public String getP() {
    return p;
  }

  public String getQ() {
    return q;
  }

  public long getT() {
    return T;
  }

  public boolean isM() {
    return m;
  }

  public boolean isMbestMatch() {
    return M;
  }
}
