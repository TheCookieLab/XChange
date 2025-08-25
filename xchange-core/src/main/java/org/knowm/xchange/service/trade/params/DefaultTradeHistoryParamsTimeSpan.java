package org.knowm.xchange.service.trade.params;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Common implementation of {@link TradeHistoryParamsTimeSpan}. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DefaultTradeHistoryParamsTimeSpan implements TradeHistoryParamsTimeSpan {

  private Date endTime;
  private Date startTime;

}
