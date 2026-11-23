package org.knowm.xchange.btcturk.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.btcturk.BTCTurkExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

class BTCTurkOpenOrdersContractTest {
  @Test
  void factoryParamsForwardPairAndPreserveAllMarketsDefault() throws Exception {
    Exchange exchange = mock(Exchange.class);
    when(exchange.getExchangeSpecification()).thenReturn(new BTCTurkExchange().getDefaultExchangeSpecification());
    BTCTurkTradeService service = spy(new BTCTurkTradeService(exchange));
    doReturn(Collections.emptyList()).when(service).getBTCTurkOpenOrders();
    CurrencyPair pair = new CurrencyPair("BTC/TRY");
    doReturn(Collections.emptyList()).when(service).getBTCTurkOpenOrders(pair);
    OpenOrdersParams params = service.createOpenOrdersParams();
    assertInstanceOf(OpenOrdersParamCurrencyPair.class, params);
    service.getOpenOrders(params);
    service.getOpenOrders();
    verify(service, times(2)).getBTCTurkOpenOrders();
    ((OpenOrdersParamCurrencyPair) params).setCurrencyPair(pair);
    service.getOpenOrders(params);
    verify(service).getBTCTurkOpenOrders(pair);
  }
}
