package org.knowm.xchange.dto.account;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.knowm.xchange.instrument.Instrument;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OpenPosition implements Serializable {

  /** The instrument */
  private Instrument instrument;

  /** Is this a long or a short position */
  private Type type;

  /** The size of the position */
  private BigDecimal size;

  /** The average entry price for the position */
  private BigDecimal price;

  /** The estimatedLiquidationPrice */
  private BigDecimal liquidationPrice;

  /** The unrealised pnl of the position */
  private BigDecimal unRealisedPnl;

  public enum Type {
    LONG,
    SHORT
  }

}
