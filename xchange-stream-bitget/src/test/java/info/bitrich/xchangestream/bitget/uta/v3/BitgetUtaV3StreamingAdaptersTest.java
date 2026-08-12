package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3AccountData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3FillData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3KlineData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3PositionData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3PublicTradeData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3TickerData;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order.BitgetUtaV3Fee;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/** Conversions between UTA v3 WebSocket DTOs and XChange core DTOs. */
class BitgetUtaV3StreamingAdaptersTest {

  @Test
  void toStringConcatenatesBaseAndCounter() {
    assertThat(BitgetUtaV3StreamingAdapters.toString(CurrencyPair.BTC_USDT)).isEqualTo("BTCUSDT");
    assertThat(
            BitgetUtaV3StreamingAdapters.toString(
                new FuturesContract(CurrencyPair.BTC_USDT, "PERP")))
        .isEqualTo("BTCUSDT");
  }

  @Test
  void toInstTypeMapsInstrumentFamilies() {
    assertThat(BitgetUtaV3StreamingAdapters.toInstType(CurrencyPair.BTC_USDT).getWireName())
        .isEqualTo("spot");
    assertThat(
            BitgetUtaV3StreamingAdapters.toInstType(
                    new FuturesContract(CurrencyPair.BTC_USDT, "PERP"))
                .getWireName())
        .isEqualTo("usdt-futures");
    assertThat(
            BitgetUtaV3StreamingAdapters.toInstType(
                    new FuturesContract(new CurrencyPair(Currency.BTC, Currency.USDC), "PERP"))
                .getWireName())
        .isEqualTo("usdc-futures");
    assertThat(
            BitgetUtaV3StreamingAdapters.toInstType(
                    new FuturesContract(new CurrencyPair(Currency.BTC, Currency.USD), "PERP"))
                .getWireName())
        .isEqualTo("coin-futures");
  }

  @Test
  void toInstrumentResolvesSpotFuturesAndUnknownCategories() {
    Instrument spot = BitgetUtaV3StreamingAdapters.toInstrument("spot", "BTCUSDT");
    assertThat(spot).isEqualTo(CurrencyPair.BTC_USDT);

    Instrument futures = BitgetUtaV3StreamingAdapters.toInstrument("usdt-futures", "BTCUSDT");
    assertThat(futures).isEqualTo(new FuturesContract(CurrencyPair.BTC_USDT, "PERP"));

    // unknown category falls back to symbol-only (spot) identity so streams keep flowing
    Instrument unknown = BitgetUtaV3StreamingAdapters.toInstrument("zzz", "BTCUSDT");
    assertThat(unknown).isEqualTo(CurrencyPair.BTC_USDT);

    Instrument nullCategory = BitgetUtaV3StreamingAdapters.toInstrument(null, "BTCUSDT");
    assertThat(nullCategory).isEqualTo(CurrencyPair.BTC_USDT);

    // symbols whose base is not a registered currency parse via the longest-suffix fallback
    Instrument pepe = BitgetUtaV3StreamingAdapters.toInstrument("spot", "1000PEPEUSDT");
    assertThat(pepe).isEqualTo(new CurrencyPair(Currency.getInstance("1000PEPE"), Currency.USDT));
  }

  @Test
  void toTickerMapsV3FieldsAndEnvelopeTimestamp() {
    BitgetUtaV3TickerData dto =
        BitgetUtaV3TickerData.builder()
            .bidPrice(new BigDecimal("100.0"))
            .askPrice(new BigDecimal("101.0"))
            .bidSize(new BigDecimal("0.5"))
            .askSize(new BigDecimal("1.5"))
            .lastPrice(new BigDecimal("100.5"))
            .openPrice24h(new BigDecimal("99.0"))
            .highPrice24h(new BigDecimal("102.0"))
            .lowPrice24h(new BigDecimal("98.0"))
            .volume24h(new BigDecimal("1000.0"))
            .turnover24h(new BigDecimal("100100.0"))
            .price24hPcnt(new BigDecimal("0.015"))
            .build();

    Ticker ticker =
        BitgetUtaV3StreamingAdapters.toTicker(dto, CurrencyPair.BTC_USDT, 1_700_000_000_123L);

    assertThat(ticker.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(ticker.getBid()).isEqualByComparingTo("100.0");
    assertThat(ticker.getAsk()).isEqualByComparingTo("101.0");
    assertThat(ticker.getBidSize()).isEqualByComparingTo("0.5");
    assertThat(ticker.getAskSize()).isEqualByComparingTo("1.5");
    assertThat(ticker.getLast()).isEqualByComparingTo("100.5");
    assertThat(ticker.getOpen()).isEqualByComparingTo("99.0");
    assertThat(ticker.getHigh()).isEqualByComparingTo("102.0");
    assertThat(ticker.getLow()).isEqualByComparingTo("98.0");
    assertThat(ticker.getVolume()).isEqualByComparingTo("1000.0");
    assertThat(ticker.getQuoteVolume()).isEqualByComparingTo("100100.0");
    assertThat(ticker.getPercentageChange())
        .as("price24hPcnt 0.015 is 1.5% and must scale to percentage units")
        .isEqualByComparingTo("1.5");
    assertThat(ticker.getTimestamp()).isEqualTo(new Date(1_700_000_000_123L));
  }

  @Test
  void toCandleMapsKlinePush() {
    BitgetUtaV3KlineData dto =
        BitgetUtaV3KlineData.builder()
            .start(1_700_000_000_000L)
            .open(new BigDecimal("100.0"))
            .close(new BigDecimal("101.0"))
            .high(new BigDecimal("102.0"))
            .low(new BigDecimal("99.0"))
            .volume(new BigDecimal("10.0"))
            .turnover(new BigDecimal("1010.0"))
            .build();

    CandleStickData data = BitgetUtaV3StreamingAdapters.toCandle(dto, CurrencyPair.BTC_USDT);

    assertThat(data.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(data.getCandleSticks()).hasSize(1);
    org.knowm.xchange.dto.marketdata.CandleStick candle = data.getCandleSticks().get(0);
    assertThat(candle.getTimestamp()).isEqualTo(java.time.Instant.ofEpochMilli(1_700_000_000_000L));
    assertThat(candle.getOpen()).isEqualByComparingTo("100.0");
    assertThat(candle.getClose()).isEqualByComparingTo("101.0");
    assertThat(candle.getHigh()).isEqualByComparingTo("102.0");
    assertThat(candle.getLow()).isEqualByComparingTo("99.0");
    assertThat(candle.getVolume()).isEqualByComparingTo("10.0");
    assertThat(candle.isCompleted()).isFalse();
  }

  @Test
  void toTradeMapsPublicTradePush() {
    BitgetUtaV3PublicTradeData dto =
        BitgetUtaV3PublicTradeData.builder()
            .id("12345")
            .price(new BigDecimal("100.5"))
            .volume(new BigDecimal("2.0"))
            .side("buy")
            .timestamp(1_700_000_000_000L)
            .build();

    Trade trade = BitgetUtaV3StreamingAdapters.toTrade(dto, CurrencyPair.BTC_USDT);

    assertThat(trade.getType()).isEqualTo(OrderType.BID);
    assertThat(trade.getOriginalAmount()).isEqualByComparingTo("2.0");
    assertThat(trade.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(trade.getPrice()).isEqualByComparingTo("100.5");
    assertThat(trade.getTimestamp()).isEqualTo(new Date(1_700_000_000_000L));
    assertThat(trade.getId()).isEqualTo("12345");

    BitgetUtaV3PublicTradeData sell = BitgetUtaV3PublicTradeData.builder().side("sell").build();
    assertThat(BitgetUtaV3StreamingAdapters.toTrade(sell, CurrencyPair.BTC_USDT).getType())
        .isEqualTo(OrderType.ASK);
  }

  @Test
  void toOpenPositionMapsPush() {
    BitgetUtaV3PositionData dto =
        BitgetUtaV3PositionData.builder()
            .symbol("BTCUSDT")
            .posSide("long")
            .marginMode("isolated")
            .size(new BigDecimal("0.5"))
            .avgPrice(new BigDecimal("100.0"))
            .liquidationPrice(new BigDecimal("90.0"))
            .unrealisedPnl(new BigDecimal("0.25"))
            .createdTime("1700000000000")
            .updatedTime("1700000001000")
            .build();

    OpenPosition position = BitgetUtaV3StreamingAdapters.toOpenPosition(dto, CurrencyPair.BTC_USDT);

    assertThat(position.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(position.getType()).isEqualTo(OpenPosition.Type.LONG);
    assertThat(position.getMarginMode()).isEqualTo(OpenPosition.MarginMode.ISOLATED);
    assertThat(position.getSize()).isEqualByComparingTo("0.5");
    assertThat(position.getPrice()).isEqualByComparingTo("100.0");
    assertThat(position.getLiquidationPrice()).isEqualByComparingTo("90.0");
    assertThat(position.getUnRealisedPnl()).isEqualByComparingTo("0.25");
    assertThat(position.getCreatedAt())
        .isEqualTo(java.time.Instant.ofEpochMilli(1_700_000_000_000L));
    assertThat(position.getUpdatedAt())
        .isEqualTo(java.time.Instant.ofEpochMilli(1_700_000_001_000L));
  }

  @Test
  void toBalanceMapsPerCoinEntry() {
    BitgetUtaV3AccountData.BitgetUtaV3CoinData dto =
        BitgetUtaV3AccountData.BitgetUtaV3CoinData.builder()
            .coin("BTC")
            .balance(new BigDecimal("1.0"))
            .available(new BigDecimal("0.8"))
            .locked(new BigDecimal("0.2"))
            .build();

    Balance balance = BitgetUtaV3StreamingAdapters.toBalance(dto, Currency.BTC);

    assertThat(balance.getCurrency()).isEqualTo(Currency.BTC);
    assertThat(balance.getTotal()).isEqualByComparingTo("1.0");
    assertThat(balance.getAvailable()).isEqualByComparingTo("0.8");
    assertThat(balance.getFrozen()).isEqualByComparingTo("0.2");
  }

  @Test
  void toBalanceUsesEquityAsTotalAndDebtsAsBorrowedForLeveragedCoins() {
    BitgetUtaV3AccountData.BitgetUtaV3CoinData dto =
        BitgetUtaV3AccountData.BitgetUtaV3CoinData.builder()
            .coin("BTC")
            .balance(new BigDecimal("1.0"))
            .equity(new BigDecimal("1.4"))
            .available(new BigDecimal("0.8"))
            .locked(new BigDecimal("0.2"))
            .debts(new BigDecimal("0.3"))
            .build();

    Balance balance = BitgetUtaV3StreamingAdapters.toBalance(dto, Currency.BTC);

    // mirrors the REST account adaptation: total is equity (balance + frozen margin + unrealized
    // PnL), borrowed is the outstanding debt
    assertThat(balance.getTotal()).isEqualByComparingTo("1.4");
    assertThat(balance.getAvailable()).isEqualByComparingTo("0.8");
    assertThat(balance.getFrozen()).isEqualByComparingTo("0.2");
    assertThat(balance.getBorrowed()).isEqualByComparingTo("0.3");
  }

  @Test
  void toUserTradeMapsFillPushWithAggregatedFees() {
    BitgetUtaV3Fee maker =
        BitgetUtaV3Fee.builder().feeCoin("USDT").fee(new BigDecimal("0.01")).build();
    BitgetUtaV3Fee taker =
        BitgetUtaV3Fee.builder().feeCoin("USDT").fee(new BigDecimal("0.02")).build();
    BitgetUtaV3FillData dto =
        BitgetUtaV3FillData.builder()
            .symbol("BTCUSDT")
            .category("spot")
            .side("buy")
            .execQty(new BigDecimal("0.5"))
            .execPrice(new BigDecimal("100.0"))
            .execTime(1_700_000_000_000L)
            .execId("exec-1")
            .orderId("order-1")
            .clientOid("client-1")
            .feeDetail(List.of(maker, taker))
            .build();

    UserTrade trade = BitgetUtaV3StreamingAdapters.toUserTrade(dto, CurrencyPair.BTC_USDT);

    assertThat(trade.getType()).isEqualTo(OrderType.BID);
    assertThat(trade.getOriginalAmount()).isEqualByComparingTo("0.5");
    assertThat(trade.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(trade.getPrice()).isEqualByComparingTo("100.0");
    assertThat(trade.getTimestamp()).isEqualTo(new Date(1_700_000_000_000L));
    assertThat(trade.getId()).isEqualTo("exec-1");
    assertThat(trade.getOrderId()).isEqualTo("order-1");
    assertThat(trade.getOrderUserReference()).isEqualTo("client-1");
    assertThat(trade.getFeeAmount()).isEqualByComparingTo("0.03");
    assertThat(trade.getFeeCurrency()).isEqualTo(Currency.USDT);
  }

  @Test
  void toUserTradeToleratesMissingFees() {
    BitgetUtaV3FillData dto = BitgetUtaV3FillData.builder().side("sell").execId("exec-2").build();

    UserTrade trade = BitgetUtaV3StreamingAdapters.toUserTrade(dto, CurrencyPair.BTC_USDT);

    assertThat(trade.getType()).isEqualTo(OrderType.ASK);
    assertThat(trade.getFeeAmount()).isNull();
    assertThat(trade.getFeeCurrency()).isNull();
    assertThat(trade.getTimestamp()).isNull();
  }

  @Test
  void toUserTradeIgnoresFeesInOtherDenominations() {
    BitgetUtaV3Fee usdt =
        BitgetUtaV3Fee.builder().feeCoin("USDT").fee(new BigDecimal("0.03")).build();
    BitgetUtaV3Fee bgb =
        BitgetUtaV3Fee.builder().feeCoin("BGB").fee(new BigDecimal("0.001")).build();
    BitgetUtaV3FillData dto =
        BitgetUtaV3FillData.builder()
            .symbol("BTCUSDT")
            .category("spot")
            .side("buy")
            .execQty(new BigDecimal("0.5"))
            .execPrice(new BigDecimal("100.0"))
            .execTime(1_700_000_000_000L)
            .execId("exec-3")
            .orderId("order-3")
            .clientOid("client-3")
            .feeDetail(List.of(usdt, bgb))
            .build();

    UserTrade trade = BitgetUtaV3StreamingAdapters.toUserTrade(dto, CurrencyPair.BTC_USDT);

    assertThat(trade.getFeeAmount())
        .as("fees in a different denomination must not be added to the first")
        .isEqualByComparingTo("0.03");
    assertThat(trade.getFeeCurrency()).isEqualTo(Currency.USDT);
  }

  @Test
  void toIntervalMapsSupportedAndRejectsUnsupported() {
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.m1)).isEqualTo("1m");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.m3)).isEqualTo("3m");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.m5)).isEqualTo("5m");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.m15)).isEqualTo("15m");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.m30)).isEqualTo("30m");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.h1)).isEqualTo("1H");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.h4)).isEqualTo("4H");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.h6)).isEqualTo("6H");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.h12)).isEqualTo("12H");
    assertThat(BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.d1)).isEqualTo("1D");
    assertThatThrownBy(() -> BitgetUtaV3StreamingAdapters.toInterval(CandleStickInterval.h2))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
