package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.knowm.xchange.currency.CurrencyPair.TRX_USDT;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams;
import org.knowm.xchange.okx.service.OkxTradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamInstrument;

@Slf4j
public class OkxExchangeIntegration {

  // Enter your authentication details here to run private endpoint tests
  private static final String API_KEY = System.getenv("okx_apikey");
  private static final String SECRET_KEY = System.getenv("okx_secretkey");
  private static final String PASSPHRASE = System.getenv("okx_passphrase");

  @Test
  public void testCreateExchangeShouldApplyDefaultSpecification() {
    ExchangeSpecification spec = new OkxExchange().getDefaultExchangeSpecification();
    final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);

    assertThat(exchange.getExchangeSpecification().getSslUri()).isEqualTo("https://www.okx.com");
    assertThat(exchange.getExchangeSpecification().getHost()).isEqualTo("okx.com");
    assertThat(exchange.getExchangeSpecification().getResilience().isRateLimiterEnabled())
        .isEqualTo(false);
    assertThat(exchange.getExchangeSpecification().getResilience().isRetryEnabled())
        .isEqualTo(false);
  }

  @Test
  public void testCreateExchangeShouldApplyResilience() {
    ExchangeSpecification spec = new OkxExchange().getDefaultExchangeSpecification();
    ExchangeSpecification.ResilienceSpecification resilienceSpecification =
        new ExchangeSpecification.ResilienceSpecification();
    resilienceSpecification.setRateLimiterEnabled(true);
    resilienceSpecification.setRetryEnabled(true);
    spec.setResilience(resilienceSpecification);

    final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);

    assertThat(exchange.getExchangeSpecification().getResilience().isRateLimiterEnabled())
        .isEqualTo(true);
    assertThat(exchange.getExchangeSpecification().getResilience().isRetryEnabled())
        .isEqualTo(true);
  }

  @Test
  public void testMetaData() {
    final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(OkxExchange.class);

    exchange.getExchangeMetaData().getInstruments().entrySet().forEach(System.out::println);
  }

  @Test
  public void testOpenPosition() throws Exception {
    Properties properties = new Properties();
    try {
      properties.load(this.getClass().getResourceAsStream("/secret.keys"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ExchangeSpecification spec = new OkxExchange().getDefaultExchangeSpecification();
    spec.setApiKey(properties.getProperty("apikey"));
    spec.setSecretKey(properties.getProperty("secret"));
    spec.setExchangeSpecificParametersItem(
        OkxExchange.PARAM_PASSPHRASE, properties.getProperty("passphrase"));
    spec.setExchangeSpecificParametersItem(OkxExchange.PARAM_SIMULATED, "1");
    final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
    exchange.getTradeService().getOpenPositions().getOpenPositions().forEach(System.out::println);
  }

  @Test
  public void testOrderActions() throws Exception {
    Properties properties = new Properties();
    try {
      properties.load(this.getClass().getResourceAsStream("/secret.keys"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ExchangeSpecification spec = new OkxExchange().getDefaultExchangeSpecification();
    spec.setApiKey(properties.getProperty("apikey"));
    spec.setSecretKey(properties.getProperty("secret"));
    spec.setExchangeSpecificParametersItem(
        OkxExchange.PARAM_PASSPHRASE, properties.getProperty("passphrase"));
    spec.setExchangeSpecificParametersItem(OkxExchange.PARAM_SIMULATED, "1");
    final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
    final OkxTradeService okxTradeService = (OkxTradeService) exchange.getTradeService();

    assertThat(exchange.getExchangeSpecification().getSslUri()).isEqualTo("https://www.okx.com");
    assertThat(exchange.getExchangeSpecification().getHost()).isEqualTo("okx.com");

    // Place a single order
    LimitOrder limitOrder =
        new LimitOrder(
            Order.OrderType.ASK, BigDecimal.TEN, TRX_USDT, null, new Date(), new BigDecimal(100));

    String orderId = okxTradeService.placeLimitOrder(limitOrder);
    log.info("Placed orderId: {}", orderId);

    // Amend the above order
    String userReferenceId = RandomStringUtils.randomAlphanumeric(20);
    LimitOrder limitOrder2 =
        new LimitOrder.Builder(Order.OrderType.ASK, TRX_USDT)
            .originalAmount(BigDecimal.TEN)
            .limitPrice(new BigDecimal(1000))
            .id(orderId)
            .userReference(userReferenceId)
            .build();
    String orderId2 = okxTradeService.changeOrder(limitOrder2);
    log.info("Amended orderId: {}", orderId2);

    // Get non-existent Order Detail
    Order failOrder =
        okxTradeService.getOrder(new DefaultQueryOrderParamInstrument(TRX_USDT, "2132465465"));
    log.info("Null Order: {}", failOrder);

    // Get Order Detail
    Order amendedOrder =
        okxTradeService.getOrder(new DefaultQueryOrderParamInstrument(TRX_USDT, orderId2));
    log.info("Amended Order: {}", amendedOrder);

    // Cancel that order
    boolean result =
        exchange
            .getTradeService()
            .cancelOrder(
                new OkxTradeParams.OkxCancelOrderParams(TRX_USDT, orderId2, userReferenceId));
    log.info("Cancellation result: {}", result);

    // Place batch orders
    List<String> orderIds =
        okxTradeService.placeLimitOrder(Arrays.asList(limitOrder, limitOrder, limitOrder));
    log.info("Placed batch orderIds: {}", orderIds);

    // Amend batch orders
    List<LimitOrder> amendOrders = new ArrayList<>();
    for (String id : orderIds) {
      amendOrders.add(
          new LimitOrder(
              Order.OrderType.ASK, BigDecimal.TEN, TRX_USDT, id, new Date(), new BigDecimal(1000)));
    }
    List<String> amendedOrderIds = okxTradeService.changeOrder(amendOrders);
    log.info("Amended batch orderIds: {}", amendedOrderIds);

    OpenOrders openOrders = okxTradeService.getOpenOrders();
    log.info("Open Orders: {}", openOrders);

    // Cancel batch orders
    List<CancelOrderParams> cancelOrderParams = new ArrayList<>();
    for (String id : orderIds) {
      cancelOrderParams.add(new OkxTradeParams.OkxCancelOrderParams(TRX_USDT, id));
    }
    List<Boolean> results = okxTradeService.cancelOrder(cancelOrderParams);
    log.info("Cancelled order results: {}", results);
  }
}
