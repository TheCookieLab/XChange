package org.knowm.xchange.deribit.v2.service.params;

import lombok.Builder;
import lombok.Data;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrency;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOffset;

@Data
@Builder
public class DeribitFundingHistoryParams implements TradeHistoryParamCurrency, TradeHistoryParamLimit,
    TradeHistoryParamOffset {

  private Currency currency;

  private Integer limit;

  private Long offset;
}
