package org.knowm.xchange.dase.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.dase.DaseAuthenticated;
import org.knowm.xchange.dase.dto.trade.DaseBatchCancelOrdersRequest;
import org.knowm.xchange.dase.dto.trade.DaseBatchGetOrdersRequest;
import org.knowm.xchange.dase.dto.trade.DaseBatchGetOrdersResponse;
import org.knowm.xchange.dase.dto.trade.DaseCancelAllOrdersQuery;
import org.knowm.xchange.dase.dto.trade.DaseOrder;
import org.knowm.xchange.dase.dto.trade.DaseOrdersListResponse;
import org.knowm.xchange.dase.dto.trade.DasePlaceOrderInput;
import org.knowm.xchange.dase.dto.trade.DasePlaceOrderResponse;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

/** Raw access to Orders endpoints. */
public class DaseTradeServiceRaw extends BaseExchangeService<Exchange> implements BaseService {

  private final String apiKey;
  private final DaseDigest signatureCreator;
  private final DaseAuthenticated daseAuth;
  private final DaseTimestampFactory timestampFactory;

  public DaseTradeServiceRaw(Exchange exchange) {
    super(exchange);
    this.apiKey = exchange.getExchangeSpecification().getApiKey();
    String secret = exchange.getExchangeSpecification().getSecretKey();
    this.signatureCreator = secret == null || secret.isEmpty() ? null : DaseDigest.createInstance(secret);
    this.timestampFactory = new DaseTimestampFactory();
    this.daseAuth =
        ExchangeRestProxyBuilder.forInterface(
                DaseAuthenticated.class, exchange.getExchangeSpecification())
            .build();
  }

  protected void ensureCredentialsPresent() {
    if (apiKey == null || apiKey.isEmpty() || signatureCreator == null) {
      throw new org.knowm.xchange.exceptions.ExchangeException(
          "API credentials are not configured for Dase exchange");
    }
  }

  public DaseOrdersListResponse getOrders(String market, String status, Integer limit, String before)
      throws IOException {
    ensureCredentialsPresent();
    return daseAuth.getOrders(apiKey, signatureCreator, timestampFactory, market, status, limit, before);
  }

  public DasePlaceOrderResponse placeOrder(DasePlaceOrderInput body) throws IOException {
    ensureCredentialsPresent();
    return daseAuth.placeOrder(apiKey, signatureCreator, timestampFactory, body);
  }

  public DaseOrder getOrder(String orderId) throws IOException {
    ensureCredentialsPresent();
    return daseAuth.getOrder(apiKey, signatureCreator, timestampFactory, orderId);
  }

  public void cancelOrderRaw(String orderId) throws IOException {
    ensureCredentialsPresent();
    daseAuth.cancelOrder(apiKey, signatureCreator, timestampFactory, orderId);
  }

  public void batchCancelOrdersRaw(List<String> orderIds) throws IOException {
    ensureCredentialsPresent();
    DaseBatchCancelOrdersRequest req = new DaseBatchCancelOrdersRequest();
    req.orderIds = orderIds;
    daseAuth.batchCancelOrders(apiKey, signatureCreator, timestampFactory, req);
  }

  public void cancelAllOrdersRaw(String market) throws IOException {
    ensureCredentialsPresent();
    DaseCancelAllOrdersQuery q = new DaseCancelAllOrdersQuery();
    q.market = market;
    daseAuth.cancelAllOrders(apiKey, signatureCreator, timestampFactory, q);
  }

  public DaseBatchGetOrdersResponse batchGetOrdersRaw(List<String> orderIds) throws IOException {
    ensureCredentialsPresent();
    DaseBatchGetOrdersRequest req = new DaseBatchGetOrdersRequest();
    req.orderIds = orderIds;
    return daseAuth.batchGetOrders(apiKey, signatureCreator, timestampFactory, req);
  }
}


