# XChange Coinbase Derivatives

This module implements Coinbase Advanced international derivatives over the
Deribit-powered Starbase JSON-RPC gateway. It is deliberately independent of
both `xchange-coinbase` and `xchange-deribit`: the wire protocol is similar to
Deribit, but authentication, account ownership, error handling, and compatibility
belong to Coinbase.

The default HTTP endpoint is `https://drb.coinbase.com/api/v2`. Supply a Coinbase
CDP key name as the exchange specification API key and its EC private key PEM as
the secret key. Remote metadata loads the provider instrument catalog; it does
not infer an authoritative catalog from instrument names.

## Order placement

`Order.userReference` is sent as the provider `label`. **A label is neither unique
nor an idempotency key.** The module never retries an order placement after an
ambiguous transport failure. Generic placement returns `order_id`; raw placement
returns `CoinbaseDerivativesPlacementResult`, retaining the request correlation
ID, accepted request, provider status, and all `primary_order_id`/`oto_order_ids`
relationships for reconciliation.

Private reads and cancellation use explicitly replay-safe calls. Callers must
recover ambiguous placements using exchange order and trade IDs, never by
assuming that a repeated label refers to the same order.
