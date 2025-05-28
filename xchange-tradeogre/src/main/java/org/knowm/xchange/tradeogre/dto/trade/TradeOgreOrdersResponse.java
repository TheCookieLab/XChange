package org.knowm.xchange.tradeogre.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class TradeOgreOrdersResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("error")
    private String error;

    @JsonProperty("orders")
    private List<TradeOgreOrder> orders;

    // Handle case where response is directly an array
    public List<TradeOgreOrder> getOrdersList() {
        return orders != null ? orders : Collections.emptyList();
    }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
}
