package org.knowm.xchange.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.config.BitgetJacksonObjectMapperFactory;
import org.knowm.xchange.bitget.uta.v3.market.BitgetUtaV3Instrument;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Fill;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;

/**
 * Wire-contract tests for {@link BitgetUtaV3Adapters#toPlaceOrderRequest(MarketOrder)}.
 *
 * <p>The UTA v3 place-order endpoint ({@code POST /api/v3/trade/place-order}) accepts exactly one
 * size parameter: {@code qty} (required). There is no {@code amount} parameter; per the official
 * docs {@code qty} is the base-coin quantity for limit and market-sell orders and the quote-coin
 * spend for market-buy orders on spot/margin categories. Because XChange's {@code originalAmount}
 * is always base-denominated, spot/margin market buys require {@link
 * BitgetUtaV3OrderFlags#MARKET_BUY_QUOTE_AMOUNT}; without it the adapter rejects the order instead
 * of silently reinterpreting the base amount as quote spend.
 */
class BitgetUtaV3AdaptersTest {

  private static final ObjectMapper MAPPER = mapper();

  private static ObjectMapper mapper() {
    ObjectMapper mapper = new ObjectMapper();
    new BitgetJacksonObjectMapperFactory().configureObjectMapper(mapper);
    return mapper;
  }

  @Test
  void spot_market_buy_without_quote_amount_flag_fails_before_placement() {
    MarketOrder buy =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("100"))
            .userReference("buy-1")
            .build();

    assertThatThrownBy(() -> BitgetUtaV3Adapters.toPlaceOrderRequest(buy))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("MARKET_BUY_QUOTE_AMOUNT");
  }

  @Test
  void spot_market_buy_with_quote_amount_flag_sends_quote_spend_as_qty() throws Exception {
    MarketOrder buy =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("100"))
            .userReference("buy-1")
            .flag(BitgetUtaV3OrderFlags.MARKET_BUY_QUOTE_AMOUNT)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(buy)));

    assertThat(json.get("category").asText()).isEqualTo("spot");
    assertThat(json.get("orderType").asText()).isEqualTo("market");
    assertThat(json.has("qty")).as("spot market order must carry the required qty field").isTrue();
    assertThat(json.get("qty").asText())
        .as("with the quote-amount flag, originalAmount is the quote-coin spend")
        .isEqualTo("100");
    assertThat(json.has("amount"))
        .as("v3 place-order has no amount parameter; amount must not be sent")
        .isFalse();
  }

  @Test
  void margin_market_buy_requires_quote_amount_flag() throws Exception {
    MarketOrder buy =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("100"))
            .flag(BitgetUtaV3OrderFlags.MARGIN)
            .build();

    assertThatThrownBy(() -> BitgetUtaV3Adapters.toPlaceOrderRequest(buy))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("MARKET_BUY_QUOTE_AMOUNT");

    JsonNode json =
        MAPPER.readTree(
            MAPPER.writeValueAsString(
                BitgetUtaV3Adapters.toPlaceOrderRequest(
                    new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
                        .originalAmount(new BigDecimal("100"))
                        .flag(BitgetUtaV3OrderFlags.MARGIN)
                        .flag(BitgetUtaV3OrderFlags.MARKET_BUY_QUOTE_AMOUNT)
                        .build())));
    assertThat(json.get("category").asText()).isEqualTo("margin");
    assertThat(json.get("qty").asText()).isEqualTo("100");
  }

  @Test
  void futures_market_buy_keeps_base_quantity_without_quote_flag() throws Exception {
    MarketOrder buy =
        new MarketOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.5"))
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(buy)));

    assertThat(json.get("category").asText()).isEqualTo("usdt-futures");
    assertThat(json.get("qty").asText())
        .as("futures market buys keep the base contract count; the quote-amount gate is spot-only")
        .isEqualTo("0.5");
  }

  @Test
  void market_buy_order_maps_base_executed_amount_not_quote_spend() {
    BitgetUtaV3Order dto =
        BitgetUtaV3Order.builder()
            .orderId("42")
            .category("spot")
            .side("buy")
            .orderType("market")
            .qty(new BigDecimal("100"))
            .cumExecQty(new BigDecimal("0.5"))
            .orderStatus("filled")
            .build();

    Order order = BitgetUtaV3Adapters.toOrder(dto, CurrencyPair.BTC_USDT);

    assertThat(order).isInstanceOf(MarketOrder.class);
    assertThat(order.getOriginalAmount())
        .as("the provider's qty is the quote spend; originalAmount must stay base-denominated")
        .isEqualByComparingTo("0.5");
    assertThat(order.getCumulativeAmount()).isEqualByComparingTo("0.5");
  }

  @Test
  void market_sell_order_keeps_qty_as_original_amount() {
    BitgetUtaV3Order dto =
        BitgetUtaV3Order.builder()
            .orderId("42")
            .category("spot")
            .side("sell")
            .orderType("market")
            .qty(new BigDecimal("0.5"))
            .cumExecQty(new BigDecimal("0.5"))
            .orderStatus("filled")
            .build();

    Order order = BitgetUtaV3Adapters.toOrder(dto, CurrencyPair.BTC_USDT);

    assertThat(order.getOriginalAmount())
        .as("market sells and limit orders carry base qty unchanged")
        .isEqualByComparingTo("0.5");
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
  void spot_order_with_margin_flag_lifts_category_to_margin() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.MARGIN)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("category").asText()).isEqualTo("margin");
    assertThat(json.has("marginMode"))
        .as("margin orders are not derivatives; no marginMode/holdMode fields")
        .isFalse();
    assertThat(json.has("holdMode")).isFalse();
  }

  @Test
  void spot_order_without_margin_flag_stays_spot() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("category").asText()).isEqualTo("spot");
  }

  @Test
  void futures_order_ignores_margin_flag() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.MARGIN)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("category").asText()).isEqualTo("usdt-futures");
  }

  @Test
  void futures_limit_order_flags_select_isolated_hedge_mode() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.ISOLATED_MARGIN)
            .flag(BitgetUtaV3OrderFlags.HEDGE_MODE)
            .flag(BitgetUtaV3OrderFlags.POS_SIDE_LONG)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("marginMode").asText()).isEqualTo("isolated");
    assertThat(json.get("holdMode").asText()).isEqualTo("hedge_mode");
    assertThat(json.get("posSide").asText()).isEqualTo("long");
  }

  @Test
  void futures_market_order_flags_select_isolated_hedge_mode() throws Exception {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.ASK, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.5"))
            .flag(BitgetUtaV3OrderFlags.ISOLATED_MARGIN)
            .flag(BitgetUtaV3OrderFlags.HEDGE_MODE)
            .flag(BitgetUtaV3OrderFlags.POS_SIDE_SHORT)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("marginMode").asText()).isEqualTo("isolated");
    assertThat(json.get("holdMode").asText()).isEqualTo("hedge_mode");
    assertThat(json.get("posSide").asText()).isEqualTo("short");
  }

  @Test
  void hedge_mode_without_position_side_fails_before_placement() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.HEDGE_MODE)
            .build();

    assertThatThrownBy(() -> BitgetUtaV3Adapters.toPlaceOrderRequest(order))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("POS_SIDE_LONG");
  }

  @Test
  void one_way_futures_order_omits_posSide() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("holdMode").asText()).isEqualTo("one_way_mode");
    assertThat(json.has("posSide"))
        .as("one-way mode orders must not carry a position side")
        .isFalse();
  }

  @Test
  void futures_limit_order_with_reduce_only_flag_serializes_yes() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.ASK, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.REDUCE_ONLY)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("reduceOnly").asText()).isEqualTo("yes");
  }

  @Test
  void futures_market_order_with_reduce_only_flag_serializes_yes() throws Exception {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.ASK, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.5"))
            .flag(BitgetUtaV3OrderFlags.REDUCE_ONLY)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("reduceOnly").asText()).isEqualTo("yes");
  }

  @Test
  void futures_order_without_reduce_only_flag_omits_the_key() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.has("reduceOnly"))
        .as("the provider default (no) applies when the flag is absent")
        .isFalse();
  }

  @Test
  void spot_order_ignores_reduce_only_flag() throws Exception {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .flag(BitgetUtaV3OrderFlags.REDUCE_ONLY)
            .build();

    JsonNode json =
        MAPPER.readTree(MAPPER.writeValueAsString(BitgetUtaV3Adapters.toPlaceOrderRequest(order)));

    assertThat(json.get("category").asText()).isEqualTo("spot");
    assertThat(json.has("reduceOnly"))
        .as("reduceOnly is a derivatives-only wire parameter")
        .isFalse();
  }

  @Test
  void fill_fees_in_one_currency_are_summed() throws Exception {
    BitgetUtaV3Fill fill =
        BitgetUtaV3Fill.builder()
            .execId("e1")
            .symbol("BTCUSDT")
            .category("spot")
            .side("buy")
            .execPrice(new BigDecimal("60000"))
            .execQty(new BigDecimal("0.1"))
            .createdTime("1725040472073")
            .feeDetail(
                java.util.List.of(
                    BitgetUtaV3Order.BitgetUtaV3Fee.builder()
                        .feeCoin("USDT")
                        .fee(new BigDecimal("1.5"))
                        .build(),
                    BitgetUtaV3Order.BitgetUtaV3Fee.builder()
                        .feeCoin("USDT")
                        .fee(new BigDecimal("0.5"))
                        .build()))
            .build();

    UserTrade trade = BitgetUtaV3Adapters.toUserTrade(fill, CurrencyPair.BTC_USDT);

    assertThat(trade.getFeeAmount()).isEqualByComparingTo("2");
    assertThat(trade.getFeeCurrency()).isEqualTo(Currency.USDT);
  }

  @Test
  void fill_fees_in_mixed_currencies_keep_first_denomination_only() throws Exception {
    BitgetUtaV3Fill fill =
        BitgetUtaV3Fill.builder()
            .execId("e1")
            .symbol("BTCUSDT")
            .category("spot")
            .side("buy")
            .execPrice(new BigDecimal("60000"))
            .execQty(new BigDecimal("0.1"))
            .createdTime("1725040472073")
            .feeDetail(
                java.util.List.of(
                    BitgetUtaV3Order.BitgetUtaV3Fee.builder()
                        .feeCoin("USDT")
                        .fee(new BigDecimal("1.5"))
                        .build(),
                    BitgetUtaV3Order.BitgetUtaV3Fee.builder()
                        .feeCoin("USDT")
                        .fee(new BigDecimal("0.5"))
                        .build(),
                    BitgetUtaV3Order.BitgetUtaV3Fee.builder()
                        .feeCoin("BGB")
                        .fee(new BigDecimal("0.01"))
                        .build()))
            .build();

    UserTrade trade = BitgetUtaV3Adapters.toUserTrade(fill, CurrencyPair.BTC_USDT);

    assertThat(trade.getFeeAmount())
        .as("fees in a different denomination must not be added to the first")
        .isEqualByComparingTo("2");
    assertThat(trade.getFeeCurrency()).isEqualTo(Currency.USDT);
  }

  @Test
  void dated_delivery_row_maps_to_dated_futures_contract() {
    BitgetUtaV3Instrument row =
        BitgetUtaV3Instrument.builder()
            .symbol("BTCUSD1226")
            .category("coin-futures")
            .baseCoin("BTC")
            .quoteCoin("USD")
            .deliveryTime("1766707200000") // 2025-12-26T00:00:00Z
            .build();

    Instrument instrument = BitgetUtaV3Adapters.toInstrument(row);

    assertThat(instrument)
        .isEqualTo(new FuturesContract(new CurrencyPair(Currency.BTC, Currency.USD), "1226"));
    // requests made with the mapped instrument target the catalog symbol, not the perpetual twin
    assertThat(BitgetUtaV3Adapters.toString(instrument)).isEqualTo("BTCUSD1226");
  }

  @Test
  void perpetual_row_maps_to_perp_prompt_and_round_trips() {
    BitgetUtaV3Instrument row =
        BitgetUtaV3Instrument.builder()
            .symbol("BTCUSD")
            .category("coin-futures")
            .baseCoin("BTC")
            .quoteCoin("USD")
            .build();

    Instrument instrument = BitgetUtaV3Adapters.toInstrument(row);

    assertThat(instrument)
        .isEqualTo(new FuturesContract(new CurrencyPair(Currency.BTC, Currency.USD), "PERP"));
    assertThat(BitgetUtaV3Adapters.toString(instrument)).isEqualTo("BTCUSD");
  }

  @Test
  void dated_and_perp_rows_on_the_same_pair_do_not_collapse() {
    BitgetUtaV3Instrument delivery =
        BitgetUtaV3Instrument.builder()
            .symbol("BTCUSD1226")
            .category("coin-futures")
            .baseCoin("BTC")
            .quoteCoin("USD")
            .deliveryTime("1766707200000")
            .build();
    BitgetUtaV3Instrument perpetual =
        BitgetUtaV3Instrument.builder()
            .symbol("BTCUSD")
            .category("coin-futures")
            .baseCoin("BTC")
            .quoteCoin("USD")
            .build();

    Instrument deliveryInstrument = BitgetUtaV3Adapters.toInstrument(delivery);
    Instrument perpetualInstrument = BitgetUtaV3Adapters.toInstrument(perpetual);

    assertThat(deliveryInstrument)
        .as("a delivery contract and a perpetual on the same pair must not share a catalog key")
        .isNotEqualTo(perpetualInstrument);
    assertThat(BitgetUtaV3Adapters.toString(deliveryInstrument))
        .isNotEqualTo(BitgetUtaV3Adapters.toString(perpetualInstrument));
  }

  @Test
  void dated_row_without_symbol_suffix_derives_prompt_from_delivery_time() {
    BitgetUtaV3Instrument row =
        BitgetUtaV3Instrument.builder()
            .symbol("BTCUSD")
            .category("coin-futures")
            .baseCoin("BTC")
            .quoteCoin("USD")
            .deliveryTime("1766707200000")
            .build();

    assertThat(BitgetUtaV3Adapters.toInstrument(row))
        .isEqualTo(new FuturesContract(new CurrencyPair(Currency.BTC, Currency.USD), "1226"));
  }

  /**
   * Spot and margin fills share the plain {@link CurrencyPair} identity, so an instrument-scoped
   * history must query both categories; futures and account-wide history keep a single category.
   */
  @Test
  void to_history_categories_covers_spot_and_margin_for_currency_pairs() {
    assertThat(BitgetUtaV3Adapters.toHistoryCategories(CurrencyPair.BTC_USDT))
        .containsExactly(
            org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category.SPOT,
            org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category.MARGIN);
    assertThat(
            BitgetUtaV3Adapters.toHistoryCategories(
                new FuturesContract(new CurrencyPair(Currency.BTC, Currency.USD), "1226")))
        .hasSize(1)
        .allMatch(
            category ->
                category != null
                    && category.isDerivative()
                    && category
                        == org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category
                            .COIN_FUTURES);
    assertThat(BitgetUtaV3Adapters.toHistoryCategories(null))
        .containsExactly(
            new org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category[] {null});
  }
}
