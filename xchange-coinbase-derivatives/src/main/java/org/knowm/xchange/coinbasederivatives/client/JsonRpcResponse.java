package org.knowm.xchange.coinbasederivatives.client;

import com.fasterxml.jackson.databind.JsonNode;

/** Coinbase derivatives JSON-RPC 2.0 response envelope. */
public record JsonRpcResponse(String jsonrpc, Long id, JsonNode result, JsonRpcError error) {}
