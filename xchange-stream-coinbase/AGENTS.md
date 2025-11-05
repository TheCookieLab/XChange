# Agent Notes for XChange Coinbase Streaming

This file captures lessons learned while building the new `xchange-stream-coinbase` module so
future AI agents can avoid the same potholes.

1. **Reuse existing DTO contracts.**  
   - `StreamingMarketDataService` expects `Ticker`, `Trade`, `OrderBook`, etc., from core XChange.
   - `CandleStick` has no `instrument` field; do not expect `getInstrument()`. Attach the pair via
     stream context instead of embedding it into the DTO.

2. **OrderBook construction.**  
   - Core `OrderBook` constructors accept `(Date, List<LimitOrder>, List<LimitOrder>)` or stream
     variants. There is *no* constructor that accepts `CurrencyPair` or extra channel metadata.
   - Maintain pair/channel state outside the order book object.

3. **CurrencyPair internals are private.**  
   - Use `currencyPair.getBase().getCurrencyCode()` rather than `currencyPair.base`.
   - Same for the counter currency.

4. **Streaming service contract.**  
   - `NettyStreamingService.disconnect()` returns `Completable`. Override with the same signature.
   - `StreamingExchange.getStreamingTradeService()` must return `StreamingTradeService`. Wrap the
     Coinbase-specific implementation or have it implement the interface directly.

5. **JWT refresh strategy.**  
   - `CoinbaseV3Digest` now exposes `generateWebsocketJwt()`. Cache the digest or supplier once per
     exchange spec; don’t recreate it for every refresh.
   - Coinbase expires JWTs after 120 seconds. Refresh 20–30 seconds early to avoid disconnects.

6. **Rate limiting.**  
   - Public subscribe: 8 msgs/sec/IP. Private: 750 msgs/sec/IP. Guard at the streaming-service
     level so all higher layers can request without thinking about throttling.

7. **Tests via reflection.**  
   - Order book state and similar helpers are package-private; reflection-based tests are fine but
     keep them local to this module so we can refactor later.

Feel free to extend this file as the implementation evolves. The goal is to keep tribal knowledge
close to the codebase.***
