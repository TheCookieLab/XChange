# XChange Coinbase Derivatives Stream

JSON-RPC 2.0 WebSocket support for Coinbase Advanced international derivatives on the
Coinbase-managed Starbase gateway.

## Configuration

Use `CoinbaseDerivativesStreamingExchange`. The default endpoint is
`wss://drb.coinbase.com/ws/api/v2`; override it with the exchange-specific `WebsocketUri`
parameter. Public streams work without credentials. Private streams use the exchange
specification API key as the CDP key resource name and the secret as its P-256 private-key PEM.
A fresh JWT is generated for every `public/auth` exchange.

Cancel on Disconnect is **disabled by default**. Enable it only by setting
`CancelOnDisconnect=true`; choose the provider scope with
`CancelOnDisconnectScope=CONNECTION` or `ACCOUNT`. This option changes live order side effects and
is logged on every connection generation.

## Streams

The market-data service exposes ticker, trades, full/incremental books, and funding data. The
account service exposes portfolio balance changes. The trade service exposes order, fill, and
position changes. Use discovered `FuturesContract` instruments such as
`new FuturesContract(CurrencyPair.BTC_USDC, "PERPETUAL")` rather than treating symbol parsing as an
authoritative instrument catalog.

The transport correlates every request with a numeric JSON-RPC ID and the local connection
generation, rejects late responses, answers heartbeat test requests, proactively reauthenticates,
and resubscribes after reconnect. Order-book `change_id`/`prev_change_id` gaps terminate the
affected stream with `CoinbaseDerivativesStreamGapException`; consumers must obtain a new snapshot
instead of continuing from unknown state. Replayed notifications are deduplicated using provider
event, trade, and versioned order identifiers.

Rate-credit metadata is not yet specified with stable field names. The client therefore detects
credit exhaustion defensively from structured metadata/errors and applies a bounded 1-30 second
pace before authentication, requests, and resubscription. It does not guess a provider refill
formula.

Coinbase `label` values are surfaced as XChange user references. Labels are neither unique nor
idempotent. Never deduplicate placements or blindly replay an ambiguously completed placement from
its label. The streaming transport fails non-replayable requests on disconnect and identifies them
as such in the sanitized exception.
