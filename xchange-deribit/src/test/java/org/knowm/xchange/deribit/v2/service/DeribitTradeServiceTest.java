package org.knowm.xchange.deribit.v2.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.deribit.DeribitExchangeWiremock;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;

class DeribitTradeServiceTest extends DeribitExchangeWiremock {

  @Test
  void trade_history() throws IOException {
    UserTrades userTrades =
        exchange
            .getTradeService()
            .getTradeHistory(
                DeribitTradeHistoryParams.builder()
                    .currency(Currency.USDT)
                    .build());

    assertThat(userTrades.getUserTrades()).hasSize(1);

    UserTrade expected =
        UserTrade.builder()
            .type(OrderType.BID)
            .originalAmount(new BigDecimal("2.0"))
            .instrument(new CurrencyPair("USDC/USDT"))
            .price(new BigDecimal("1.0008"))
            .timestamp(Date.from(Instant.parse("2025-11-22T23:38:35.497Z")))
            .id("USDC_USDT-21102496")
            .orderId("USDC_USDT-6470719424")
            .feeAmount(new BigDecimal("0.0"))
            .feeCurrency(Currency.USDC)
            .build();
    assertThat(userTrades.getUserTrades()).first().usingRecursiveComparison().isEqualTo(expected);
  }


}
