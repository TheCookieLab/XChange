package org.knowm.xchange.coinbasederivatives.client;

import com.fasterxml.jackson.databind.JsonNode;

/** Structured JSON-RPC error returned by the Coinbase derivatives gateway. */
public record JsonRpcError(int code, String message, JsonNode data) {}
