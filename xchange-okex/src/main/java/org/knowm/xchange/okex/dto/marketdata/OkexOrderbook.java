package org.knowm.xchange.okex.dto.marketdata;

import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.okx.dto.marketdata.OkxOrderbook;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxOrderbook} instead.
 */
@Deprecated
public class OkexOrderbook {

  private final OkxOrderbook delegate;

  public OkexOrderbook(OkxOrderbook delegate) {
    this.delegate = delegate;
  }

  public List<OkexPublicOrder> getAsks() {
    return delegate.getAsks().stream().map(OkexPublicOrder::new).collect(Collectors.toList());
  }

  public List<OkexPublicOrder> getBids() {
    return delegate.getBids().stream().map(OkexPublicOrder::new).collect(Collectors.toList());
  }

  public String getTs() {
    return delegate.getTs();
  }
}
