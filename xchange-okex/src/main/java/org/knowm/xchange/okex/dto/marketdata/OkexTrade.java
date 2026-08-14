package org.knowm.xchange.okex.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxTrade} instead.
 */
@Deprecated
public class OkexTrade {

  private final OkxTrade delegate;

  @JsonCreator
  public OkexTrade(OkxTrade delegate) {
    this.delegate = delegate;
  }

  /**
   * Retained legacy value constructor; builds the canonical DTO internally.
   *
   * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxTrade} instead.
   */
  @Deprecated
  public OkexTrade(
      @JsonProperty("tradeId") String tradeId,
      @JsonProperty("instId") String instId,
      @JsonProperty("px") BigDecimal px,
      @JsonProperty("sz") BigDecimal sz,
      @JsonProperty("side") String side,
      @JsonProperty("ts") Date ts) {
    this(new OkxTrade(tradeId, instId, px, sz, side, ts));
  }

  /** Returns the wrapped canonical DTO. */
  public OkxTrade to() {
    return delegate;
  }

  public String getTradeId() {
    return delegate.getTradeId();
  }

  public String getInstId() {
    return delegate.getInstId();
  }

  public BigDecimal getPx() {
    return delegate.getPx();
  }

  public BigDecimal getSz() {
    return delegate.getSz();
  }

  public String getSide() {
    return delegate.getSide();
  }

  public Date getTs() {
    return delegate.getTs();
  }
}
