package org.knowm.xchange.bitfinex.config.converter;

import com.fasterxml.jackson.databind.util.StdConverter;
import org.knowm.xchange.dto.Order.OrderStatus;

/**
 * Converts string to {@code OrderStatus}
 */
public class StringToOrderStatusConverter extends StdConverter<String, OrderStatus> {

  @Override
  public OrderStatus convert(String value) {

    if (value.contains("PARTIALLY FILLED")) {
      return OrderStatus.PARTIALLY_FILLED;
    }

    if (value.endsWith("CANCELED")) {
      return OrderStatus.CANCELED;
    }

    if (value.startsWith("EXECUTED")) {
      return OrderStatus.FILLED;
    }

    switch (value) {
      case "ACTIVE":
        return OrderStatus.OPEN;
      case "EXECUTED":
      case "FORCED EXECUTED":
        return OrderStatus.FILLED;
      case "INSUFFICIENT BALANCE (U1)":
      case "RSN_POS_REDUCE_INCR":
      case "RSN_POS_REDUCE_FLIP":
      case "RSN_PAUSE":
        return OrderStatus.CANCELED;
      case "PARTIALLY FILLED":
      case "INSUFFICIENT BALANCE (G1)":
        return OrderStatus.PARTIALLY_FILLED;
      default:
        throw new IllegalArgumentException("Can't map " + value);
    }
  }
}
