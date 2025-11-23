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
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPosition.MarginMode;
import org.knowm.xchange.dto.account.OpenPosition.Type;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;

class DeribitTradeServiceTest extends DeribitExchangeWiremock {

  TradeService tradeService = exchange.getTradeService();

  @Test
  void open_positions() throws IOException {
    var expected =
        OpenPosition.builder()
            .instrument(new FuturesContract(new CurrencyPair("BTC/USDC"), "PERPETUAL"))
            .type(Type.LONG)
            .marginMode(MarginMode.CROSS)
            .size(new BigDecimal("0.0001"))
            .price(new BigDecimal("85295.36"))
            .liquidationPrice(new BigDecimal("1904.376197"))
            .unRealisedPnl(new BigDecimal("0.005986"))
            .build();

    var actual = tradeService.getOpenPositions();

    assertThat(actual.getOpenPositions()).hasSize(1);

    assertThat(actual.getOpenPositions()).first().usingRecursiveComparison().isEqualTo(expected);
  }


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
