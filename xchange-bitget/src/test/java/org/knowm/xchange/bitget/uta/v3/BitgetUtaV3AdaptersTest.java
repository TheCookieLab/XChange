package org.knowm.xchange.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.config.BitgetJacksonObjectMapperFactory;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;

/**
 * Wire-contract tests for {@link BitgetUtaV3Adapters#toPlaceOrderRequest(MarketOrder)}.
 *
 * <p>The UTA v3 place-order endpoint ({@code POST /api/v3/trade/place-order}) accepts exactly one
 * size parameter: {@code qty} (required). There is no {@code amount} parameter; per the official
 * docs {@code qty} is the base-coin quantity for limit and market-sell orders and the quote-coin
 * spend for market-buy orders on spot/margin categories.
 */
class BitgetUtaV3AdaptersTest {

  private static final ObjectMapper MAPPER = mapper();

  private static ObjectMapper mapper() {
    ObjectMapper mapper = new ObjectMapper();
    new BitgetJacksonObjectMapperFactory().configureObjectMapper(mapper);
    return mapper;
  }

  @Test
  void spot_market_buy_uses_qty_not_amount() throws Exception {
    MarketOrder buy =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("100"))
            .userReference("buy-1")
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(buy)));

    assertThat(json.get("category").asText()).isEqualTo("spot");
    assertThat(json.get("orderType").asText()).isEqualTo("market");
    assertThat(json.has("qty")).as("spot market order must carry the required qty field").isTrue();
    assertThat(json.get("qty").asText()).isEqualTo("100");
    assertThat(json.has("amount"))
        .as("v3 place-order has no amount parameter; amount must not be sent")
        .isFalse();
  }

  @Test
  void spot_market_sell_uses_qty_not_amount() throws Exception {
    MarketOrder sell =
        new MarketOrder.Builder(OrderType.ASK, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.5"))
            .userReference("sell-1")
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(sell)));

    assertThat(json.get("category").asText()).isEqualTo("spot");
    assertThat(json.has("qty")).isTrue();
    assertThat(json.get("qty").asText()).isEqualTo("0.5");
    assertThat(json.has("amount")).isFalse();
  }

  @Test
  void futures_limit_order_defaults_to_cross_one_way() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("category").asText()).isEqualTo("usdt-futures");
    assertThat(json.get("marginMode").asText()).isEqualTo("crossed");
    assertThat(json.get("holdMode").asText()).isEqualTo("one_way_mode");
  }

  @Test
  void futures_limit_order_flags_select_isolated_hedge_mode() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.ISOLATED_MARGIN)
            .flag(BitgetUtaV3OrderFlags.HEDGE_MODE)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("marginMode").asText()).isEqualTo("isolated");
    assertThat(json.get("holdMode").asText()).isEqualTo("two_way_mode");
  }

  @Test
  void futures_market_order_flags_select_isolated_hedge_mode() throws Exception {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.ASK, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.5"))
            .flag(BitgetUtaV3OrderFlags.ISOLATED_MARGIN)
            .flag(BitgetUtaV3OrderFlags.HEDGE_MODE)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("marginMode").asText()).isEqualTo("isolated");
    assertThat(json.get("holdMode").asText()).isEqualTo("two_way_mode");
  }
}
