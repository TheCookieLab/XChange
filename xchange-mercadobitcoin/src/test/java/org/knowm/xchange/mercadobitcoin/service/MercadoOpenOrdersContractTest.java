package org.knowm.xchange.mercadobitcoin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.mercadobitcoin.MercadoBitcoinExchange;
import org.knowm.xchange.mercadobitcoin.dto.MercadoBitcoinBaseTradeApiResult;
import org.knowm.xchange.mercadobitcoin.dto.trade.MercadoBitcoinUserOrders;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

class MercadoOpenOrdersContractTest {
  @Test
  void factoryParamsForwardPairAndDefaultKeepsLegacyTwoMarkets() throws Exception {
    Exchange exchange = mock(Exchange.class);
    when(exchange.getExchangeSpecification()).thenReturn(new MercadoBitcoinExchange().getDefaultExchangeSpecification());
    MercadoBitcoinTradeService service = spy(new MercadoBitcoinTradeService(exchange));
    MercadoBitcoinBaseTradeApiResult<MercadoBitcoinUserOrders> empty =
        new MercadoBitcoinBaseTradeApiResult<>(1, null, new MercadoBitcoinUserOrders());
    doReturn(empty).when(service).getMercadoBitcoinUserOrders(anyString(), isNull(), eq("active"), isNull(), isNull(), isNull(), isNull());
    OpenOrdersParams params = service.createOpenOrdersParams();
    assertInstanceOf(OpenOrdersParamCurrencyPair.class, params);
    service.getOpenOrders(params);
    verify(service).getMercadoBitcoinUserOrders("btc_brl", null, "active", null, null, null, null);
    verify(service).getMercadoBitcoinUserOrders("ltc_brl", null, "active", null, null, null, null);
    clearInvocations(service);
    ((OpenOrdersParamCurrencyPair) params).setCurrencyPair(new CurrencyPair("ETH/BRL"));
    service.getOpenOrders(params);
    verify(service).getMercadoBitcoinUserOrders("eth_brl", null, "active", null, null, null, null);
    verify(service, never()).getMercadoBitcoinUserOrders("btc_brl", null, "active", null, null, null, null);
    verify(service, never()).getMercadoBitcoinUserOrders("ltc_brl", null, "active", null, null, null, null);
  }
}
