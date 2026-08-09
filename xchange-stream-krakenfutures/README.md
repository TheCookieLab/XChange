# XChange Stream Kraken Futures (Futures WebSocket)

Canonical XChange adapter for the Kraken Futures WebSocket API. See the
[Kraken family guide](../docs/kraken-family.md) for the full architecture and capability matrix.

## Capabilities

- Order book snapshots and deltas (provider `seq` values preserved)
- Ticker, trades, funding rates
- Private fills

## Notes

- The private endpoint authenticates with the Futures REST credentials.
- Sequence continuity validation on book snapshot/delta streams is in progress; do not
  treat socket reconnects as book continuity.
