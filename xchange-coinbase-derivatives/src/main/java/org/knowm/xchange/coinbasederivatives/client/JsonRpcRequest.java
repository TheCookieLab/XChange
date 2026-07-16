package org.knowm.xchange.coinbasederivatives.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Coinbase derivatives JSON-RPC 2.0 request envelope. */
@JsonPropertyOrder({"jsonrpc", "id", "method", "params"})
public record JsonRpcRequest(long id, String method, Object params) {
  @JsonProperty("jsonrpc")
  public String jsonrpc() {
    return "2.0";
  }
}
