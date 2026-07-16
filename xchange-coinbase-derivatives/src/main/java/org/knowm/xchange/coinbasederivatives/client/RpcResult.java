package org.knowm.xchange.coinbasederivatives.client;

/** Typed JSON-RPC result together with its transport correlation ID. */
public record RpcResult<T>(long requestId, T value) {}
