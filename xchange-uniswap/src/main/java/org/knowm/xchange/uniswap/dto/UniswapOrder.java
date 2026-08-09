package org.knowm.xchange.uniswap.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;

/**
 * An on-chain Uniswap market order tracked by its transaction hash.
 *
 * <p>The order id is the locally computed transaction hash; receipts and PoolManager logs are
 * authoritative for terminal state.
 */
public record UniswapOrder(
    String orderId,
    CurrencyPair instrument,
    OrderType type,
    UniswapOrderStatus status,
    BigDecimal originalAmount,
    BigDecimal cumulativeAmount,
    BigDecimal averagePrice,
    BigDecimal fee,
    Instant createdAt,
    Instant updatedAt,
    BigInteger blockNumber,
    List<UniswapFill> fills,
    String note) {

  /** Maps the on-chain lifecycle to the XChange order status vocabulary. */
  public OrderStatus toXChangeStatus() {
    switch (status) {
      case PENDING:
        return OrderStatus.PENDING_NEW;
      case MINED:
        return OrderStatus.FILLED;
      case REVERTED:
        return OrderStatus.REJECTED;
      case UNKNOWN:
      default:
        return OrderStatus.UNKNOWN;
    }
  }

  /** Adapts this order to an XChange {@link MarketOrder} for standard trade-service queries. */
  public MarketOrder toMarketOrder() {
    return new MarketOrder.Builder(type, instrument)
        .originalAmount(originalAmount)
        .id(orderId)
        .timestamp(java.util.Date.from(createdAt))
        .orderStatus(toXChangeStatus())
        .cumulativeAmount(cumulativeAmount)
        .averagePrice(averagePrice)
        .fee(fee)
        .build();
  }
}
