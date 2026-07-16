package info.bitrich.xchangestream.coinbasederivatives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.trade.MarketOrder;

class CoinbaseDerivativesStreamingServicesTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  private static final FuturesContract CONTRACT =
      (FuturesContract)
          CoinbaseDerivativesAdapters.registerInstrument(
              new CoinbaseDerivativesInstrument(
                  "BTC_USDC-PERPETUAL",
                  "future",
                  "BTC",
                  "USDC",
                  "USDC",
                  true,
                  null,
                  null,
                  null,
                  null,
                  null));

  @Test
  void adaptsPublicMarketStreamsFromDeterministicFixtures() throws Exception {
    FixtureService fixture = new FixtureService();
    fixture.publicData =
        MAPPER.readTree(
            "{\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"last_price\":36457.5,\"best_bid_price\":36442.5,\"best_ask_price\":36443,\"current_funding\":0.000001,\"funding_8h\":0.0000211,\"timestamp\":1623060194301,\"stats\":{\"high\":36824.5,\"low\":35213.5,\"volume\":7871.02139035}} ");
    CoinbaseDerivativesStreamingMarketDataService service =
        new CoinbaseDerivativesStreamingMarketDataService(fixture);

    assertEquals(new BigDecimal("36457.5"), service.getTicker(CONTRACT).blockingFirst().getLast());
    assertEquals(
        new BigDecimal("0.0000211"),
        service.getFundingRate(CONTRACT).blockingFirst().getFundingRate());

    fixture.publicData =
        MAPPER.readTree(
            "[{\"amount\":10,\"direction\":\"sell\",\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"price\":8950,\"timestamp\":1590484512188,\"trade_id\":\"48079269\"}]");
    assertEquals("48079269", service.getTrades(CONTRACT).blockingFirst().getId());

    fixture.publicData =
        MAPPER.readTree(
            "{\"type\":\"snapshot\",\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"timestamp\":1554373962454,\"asks\":[[\"new\",5042.64,40]],\"bids\":[[\"new\",5042.34,30]]}");
    assertEquals(1, service.getOrderBook(CONTRACT).blockingFirst().getAsks().size());
    assertEquals(2, service.getOrderBookUpdates(CONTRACT).blockingFirst().size());

    fixture.publicData =
        MAPPER.readTree(
            "{\"open\":8869.79,\"close\":8791.25,\"high\":8870.31,\"low\":8788.25,\"cost\":460,\"tick\":1573645080000,\"volume\":0.05219351}");
    assertEquals(
        new BigDecimal("8791.25"),
        service
            .getCandleStick(CONTRACT, CandleStickInterval.m1)
            .blockingFirst()
            .getCandleSticks()
            .get(0)
            .getClose());
  }

  @Test
  void adaptsPrivatePortfolioOrderFillAndPositionStreams() throws Exception {
    FixtureService fixture = new FixtureService();
    CoinbaseDerivativesStreamingAccountService accountService =
        new CoinbaseDerivativesStreamingAccountService(fixture);
    CoinbaseDerivativesStreamingTradeService tradeService =
        new CoinbaseDerivativesStreamingTradeService(fixture);

    fixture.privateData =
        MAPPER.readTree(
            "{\"currency\":\"USDC\",\"equity\":302.6188592,\"available_funds\":301.38036328}");
    assertEquals(
        new BigDecimal("301.38036328"),
        accountService.getBalanceChanges(Currency.USDC).blockingFirst().getAvailable());

    fixture.privateData =
        MAPPER.readTree(
            "{\"orders\":[{\"amount\":10,\"average_price\":17391,\"direction\":\"sell\",\"filled_amount\":10,\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"label\":\"reusable\",\"last_update_timestamp\":1605780344032,\"order_id\":\"3398016\",\"order_state\":\"filled\",\"order_type\":\"market\",\"price\":15665.5}],\"positions\":[{\"average_price\":15000,\"direction\":\"buy\",\"floating_profit_loss\":0.906961435,\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"size_currency\":10.646886321}],\"trades\":[{\"amount\":10,\"direction\":\"sell\",\"fee\":1.6E-7,\"fee_currency\":\"USDC\",\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"label\":\"reusable\",\"order_id\":\"3398016\",\"price\":17391,\"timestamp\":1605780344032,\"trade_id\":\"1430914\"}]}");

    assertInstanceOf(MarketOrder.class, tradeService.getOrderChanges(CONTRACT).blockingFirst());
    assertEquals("1430914", tradeService.getUserTrades(CONTRACT).blockingFirst().getId());
    assertEquals(
        new BigDecimal("10.646886321"),
        tradeService.getPositionChanges(CONTRACT).blockingFirst().getSize());
  }

  private static final class FixtureService extends CoinbaseDerivativesStreamingService {
    private JsonNode publicData;
    private JsonNode privateData;

    private FixtureService() {
      super("ws://localhost", new CoinbaseDerivativesStreamConfiguration(() -> "fixture-jwt"));
    }

    @Override
    public Observable<JsonNode> subscribePublicChannel(String channel) {
      return Observable.just(notification(channel, publicData));
    }

    @Override
    public Observable<JsonNode> subscribePrivateChannel(String channel) {
      return Observable.just(notification(channel, privateData));
    }

    private JsonNode notification(String channel, JsonNode data) {
      return MAPPER.createObjectNode().put("channel", channel).set("data", data);
    }
  }
}
