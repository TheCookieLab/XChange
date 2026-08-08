# XChange Uniswap

XChange implementation for **Uniswap v4 on Ethereum mainnet**: amount-specific
exact-input/exact-output quotes, balances, and bounded market swaps through the
standard `Exchange` / `MarketDataService` / `TradeService` / `AccountService`
contracts. `xchange-core` is untouched.

The module executes through the official **Universal Router 2.1.1** with
**Permit2** allowances, quotes through the on-node **v4 Quoter** lens, signs
locally with a password-encrypted **Web3 V3 keystore**, and tracks orders from
transaction receipts and **PoolManager** swap logs.

## Safety model

Everything is fail-closed:

- **Configuration** — chain id, token registry, pool keys, deployment
  addresses, code hashes, and risk bounds are validated at startup
  (`UniswapConfig`, `TokenRegistry`, `PoolKeyRegistry`, `DeploymentRegistry`).
- **Deployment pinning** — at startup the node must report the configured chain
  id and the runtime code of PoolManager, Quoter, Universal Router, and Permit2
  must hash to the pinned keccak-256 values.
- **Keys stay local** — signing happens on the XChange host from a V3 keystore;
  the password comes from a `SecretProvider` (default: the
  `UNISWAP_KEYSTORE_PASSWORD` environment variable) and never touches the
  `ExchangeSpecification`, logs, or persisted config.
- **Bounded execution** — orders re-quote at execution time and enforce
  slippage, deadline, gas, route, token, and quote-age limits; Permit2
  allowances are bounded to the quoted input plus a configured margin and
  expire after the deadline.
- **At-most-once broadcast** — nonces are serialized per address, the
  transaction hash is computed locally before broadcast, and an ambiguous send
  is never blindly retried: the hash is reconciled against the node first.
- **No node control surfaces** — only `eth` HTTP namespace reads/writes are
  used; the `personal` namespace and node-side unlock are never touched.

## Configuration

Configure an `ExchangeSpecification` for `UniswapExchange` with the parameters
below (constants in `UniswapConfig.Keys`). `sslUri` (or `uniswap.rpc-url`) is
the JSON-RPC endpoint — use a loopback address or a persistent SSH local
forward, never an exposed node.

| Parameter | Required | Meaning |
|---|---|---|
| `sslUri` / `uniswap.rpc-url` | yes | JSON-RPC endpoint (http/https) |
| `uniswap.chain-id` | no | default `1` (Ethereum mainnet) |
| `uniswap.wallet-address` | yes | wallet address owning the keystore |
| `uniswap.keystore-path` | yes | path of the encrypted V3 keystore (owner-only permissions) |
| `uniswap.password-provider-class` | no | `SecretProvider` class name; default reads `UNISWAP_KEYSTORE_PASSWORD` |
| `uniswap.tokens` | yes | JSON array: `{symbol, address, decimals, native?}` |
| `uniswap.pool-keys` | yes | JSON array: `{pair, currency0, currency1, fee, tickSpacing, hooks?}` |
| `uniswap.deployments` | yes | JSON: addresses + `codeHashes` for poolManager, quoter, universalRouter, permit2 |
| `uniswap.quote-ref-size` | no | reference base amount for the standard ticker (default `1`) |
| `uniswap.max-slippage-bps` | yes | maximum slippage in basis points (e.g. `100` = 1%) |
| `uniswap.max-deadline-seconds` | yes | transaction deadline |
| `uniswap.max-quote-age-seconds` | yes | reference-quote staleness limit |
| `uniswap.max-fee-per-gas-gwei` | yes | EIP-1559 max fee cap |
| `uniswap.max-priority-fee-per-gas-gwei` | yes | EIP-1559 priority fee cap |
| `uniswap.max-gas-limit` | yes | gas limit cap |
| `uniswap.allowance-margin-bps` | no | Permit2 approval margin over the quoted input (default `500`) |
| `uniswap.verify-on-startup` | no | chain/deployment verification (default `true`) |

Example `pool-keys` entry for an ETH/USDC pool (currencies must be sorted, the
base side may be the wrapped token):

```json
[{"pair":"ETH/USDC",
  "currency0":"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
  "currency1":"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
  "fee":3000,"tickSpacing":60}]
```

Example `deployments` (official mainnet addresses; **you must pin the code
hashes** from your own node or the official registry):

```json
{"poolManager":"0x000000000004444c5dc75cB358380D2e3dE08A90",
 "quoter":"0x52f0e24d1c21c8a0cb1e5a5dd6198556bd9e1203",
 "universalRouter":"0x4c82d1fbfe28c977cbb58d8c7ff8fcf9f70a2cca",
 "permit2":"0x000000000022D473030F116dDEE9F6B43aC78BA3",
 "codeHashes":{"poolManager":"0x…","quoter":"0x…",
               "universalRouter":"0x…","permit2":"0x…"}}
```

## Supported operations

- **Market data** — `getTicker(pair)` (reference-size bid/ask from one block),
  raw `quoteExactInput(pair, amount)` / `quoteExactOutput(pair, amount)` with a
  captured block number.
- **Account** — `getAccountInfo()` and raw balances for the configured tokens
  and native currency; `requestDepositAddress` returns the wallet address.
- **Trade** — `placeMarketOrder`: ASK = exact-input base, BID = exact-output
  base. The order id is the locally computed transaction hash.
  `getOrder(id)` / raw `getOrderStatus(id)` reconcile receipts and PoolManager
  logs into status, fills, average price, and fees.

Everything else (limit/stop orders, cancellation, open orders, trade history,
withdrawals) throws `NotAvailableFromExchangeException`.

## Node hardening (required before any funded use)

The Geth/Prysm host must be locked down before a funded canary:

1. Bind Geth HTTP, WebSocket, and AuthRPC to `127.0.0.1`; enable only the `eth`
   HTTP namespace. Keep the Engine API/JWT local between Prysm and Geth.
2. Reach `8545` through a persistent SSH local forward, e.g.
   `ssh -L 127.0.0.1:18545:127.0.0.1:8545 user@node`.
3. Bind Prysm REST/gRPC and validator APIs to loopback, remove wildcard CORS,
   and keep the validator RPC disabled.
4. Expose only SSH from the trusted LAN plus the required execution/consensus
   P2P ports; audit the firewall with root access.
5. Resolve Geth freezer "canonical hash missing" errors before funding the
   canary wallet.

Verify with `curl` that the management APIs are not reachable from the LAN and
that `eth_chainId` works on the loopback endpoint.

## Wallet creation

```java
char[] password = System.console().readPassword("keystore password: ");
String address = LocalKeystoreSigner.createKeystore(Path.of("wallet.json"), password);
System.out.println(address);
```

The keystore is written with owner-only permissions. Provide the password at
runtime through `UNISWAP_KEYSTORE_PASSWORD` (or a custom `SecretProvider`);
never store it in configuration or source.

## Rollout and rollback

1. **Unfunded smoke** — run quotes, ticker, and balances against the hardened
   loopback node (`xchange-examples` `UniswapQuoteBalanceDemo`). Verify chain id
   and pinned code hashes.
2. **Funded canary** (separately approved) — fund the wallet with a minimal
   amount, run one bounded swap, reconcile by hash/receipt, record fill and
   fees, and verify the Permit2 allowance state.
3. **Rollback** — disable the module configuration, stop the signing process,
   revoke Permit2 allowances (`permit2.lockdown`), and keep the transaction and
   receipt evidence. Node hardening stays in place.

## Dependencies

Web3j 6.0.0 (`org.web3j:core`) plus its Jackson 3 / RxJava 2 / okhttp graph.
The reactor pins the kotlin-stdlib versions so the okio and tuweni paths
converge; unused web3j transports (IPC, WebSocket, AWS KMS signing) are
excluded from the module. See the root `pom.xml` for the convergence pins.
