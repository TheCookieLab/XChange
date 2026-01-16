# Agent Notes for XChange Coinbase Streaming

This file captures lessons learned while building the new `xchange-stream-coinbase` module so
future AI agents can avoid the same potholes.

1. **Reuse existing DTO contracts.**  
   - `StreamingMarketDataService` expects `Ticker`, `Trade`, `OrderBook`, etc., from core XChange.
   - `CandleStick` has no `instrument` field; do not expect `getInstrument()`. Attach the pair via
     stream context instead of embedding it into the DTO.
2. **Prefer typed Coinbase streaming DTOs.**  
   - Parse raw JSON into `info.bitrich.xchangestream.coinbase.dto.*` via `CoinbaseStreamingAdapters.toStreamingMessage(...)`.
   - Keep adapter logic on typed messages rather than `JsonNode` to simplify parsing and testing.

3. **OrderBook construction.**  
   - Core `OrderBook` constructors accept `(Date, List<LimitOrder>, List<LimitOrder>)` or stream
     variants. There is *no* constructor that accepts `CurrencyPair` or extra channel metadata.
   - Maintain pair/channel state outside the order book object.

4. **CurrencyPair internals are private.**  
   - Use `currencyPair.getBase().getCurrencyCode()` rather than `currencyPair.base`.
   - Same for the counter currency.

5. **Streaming service contract.**  
   - `NettyStreamingService.disconnect()` returns `Completable`. Override with the same signature.
   - `StreamingExchange.getStreamingTradeService()` must return `StreamingTradeService`. Wrap the
     Coinbase-specific implementation or have it implement the interface directly.

6. **JWT refresh strategy.**  
   - `CoinbaseV3Digest` now exposes `generateWebsocketJwt()`. Cache the digest or supplier once per
     exchange spec; don’t recreate it for every refresh.
   - Coinbase expires JWTs after 120 seconds. Refresh 20–30 seconds early to avoid disconnects.

7. **Rate limiting.**  
   - Public subscribe: 8 msgs/sec/IP. Private: 750 msgs/sec/IP. Guard at the streaming-service
     level so all higher layers can request without thinking about throttling.

8. **Unit Testing**  
   - After changes verify the build by navigating to the project root and issuing Maven goals, ex: `cd ${PROJECT_ROOT} && mvn clean install -pl xchange-stream-coinbase -am`

Feel free to extend this file as the implementation evolves. The goal is to keep tribal knowledge
close to the codebase.***
