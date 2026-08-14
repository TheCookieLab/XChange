package org.knowm.xchange.okex.dto.trade;

import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByInstrument;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxTradeParams} instead.
 */
@Deprecated
public class OkexTradeParams {

  /**
   * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxTradeParams.OkxCancelOrderParams}
   *     instead.
   */
  @Deprecated
  @Getter
  @ToString
  public static class OkexCancelOrderParams
      implements CancelOrderByIdParams, CancelOrderByInstrument, CancelOrderByUserReferenceParams {
    public final Instrument instrument;
    public final String orderId;
    public final String userReference;

    public OkexCancelOrderParams(Instrument instrument, String orderId, String userReference) {
      this.instrument = instrument;
      this.orderId = orderId;
      this.userReference = userReference;
    }

    public OkexCancelOrderParams(Instrument instrument, String orderId) {
      this.instrument = instrument;
      this.orderId = orderId;
      this.userReference = null;
    }
  }
}
