package org.knowm.xchange.kraken.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of the Kraken AddOrderBatch endpoint.
 *
 * <p>The provider returns one entry per submitted order, in request order; each entry carries the
 * exchange transaction id and a description. Raw identity is preserved so partial batch outcomes
 * can be reconciled per order.
 */
public class KrakenAddOrderBatchResponse {

  private final List<KrakenBatchOrder> orders;

  public KrakenAddOrderBatchResponse(@JsonProperty("orders") List<KrakenBatchOrder> orders) {
    this.orders = orders;
  }

  /**
   * @return per-order results in request order
   */
  public List<KrakenBatchOrder> getOrders() {
    return orders;
  }

  @Override
  public String toString() {
    return "KrakenAddOrderBatchResponse [orders=" + orders + "]";
  }

  /** One submitted order within a batch. */
  public static class KrakenBatchOrder {

    private final String transactionId;
    private final String orderDescription;
    private final String closeDescription;

    public KrakenBatchOrder(
        @JsonProperty("txid") String transactionId,
        @JsonProperty("descr") KrakenOrderResponse.KrakenOrderResponseDescription description) {

      this.transactionId = transactionId;
      this.orderDescription = description == null ? null : description.getOrderDescription();
      this.closeDescription = description == null ? null : description.getCloseDescription();
    }

    /**
     * @return exchange transaction id of the submitted order
     */
    public String getTransactionId() {
      return transactionId;
    }

    /**
     * @return native order description text
     */
    public String getOrderDescription() {
      return orderDescription;
    }

    /**
     * @return native close-order description text, when present
     */
    public String getCloseDescription() {
      return closeDescription;
    }

    @Override
    public String toString() {
      return "KrakenBatchOrder [transactionId="
          + transactionId
          + ", orderDescription="
          + orderDescription
          + ", closeDescription="
          + closeDescription
          + "]";
    }
  }
}
