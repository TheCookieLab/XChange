package org.knowm.xchange.coinsph.service;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrency;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;

/** Funding history parameters for Coins.ph */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CoinsphFundingHistoryParams
    implements TradeHistoryParams,
        TradeHistoryParamsTimeSpan,
        TradeHistoryParamCurrency,
        TradeHistoryParamLimit {

  private Currency currency;
  private Date startTime;
  private Date endTime;
  private Integer limit;

  @Builder.Default private boolean includeDeposits = true;

  @Builder.Default private boolean includeWithdrawals = true;
}
