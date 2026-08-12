package info.bitrich.xchangestream.bitget.uta.v3;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3AccountData.BitgetUtaV3CoinData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3FillData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3KlineData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3PositionData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3PublicTradeData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3TickerData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3Adapters;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Category;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/**
 * Conversions between Bitget UTA v3 WebSocket wire DTOs and XChange core DTOs.
 *
 * <p>Instrument identity rules mirror the REST adapters: spot maps to {@link CurrencyPair}, futures
 * to {@link FuturesContract} with prompt {@code PERP} (perpetuals) or the provider's expiry suffix
 * (dated deliveries, e.g. {@code BTCUSD1226}). Private channels (order, fill, position,
 * account) are account-wide; pushed {@code symbol}/{@code category} fields (or the caller's
 * instrument for positions, whose pushes carry no category) resolve the instrument.
 *
 * @since 5.1.0
 */
@UtilityClass
public class BitgetUtaV3StreamingAdapters {

  /** Symbol text for a v3 WebSocket channel, e.g. {@code BTCUSDT}. */
  public String toString(Instrument instrument) {
    return BitgetUtaV3Adapters.toString(instrument);
  }

  /** {@code instType} for a channel from an instrument, mirroring the REST category mapping. */
  public BitgetUtaV3InstType toInstType(Instrument instrument) {
    switch (BitgetUtaV3Adapters.toCategory(instrument)) {
      case USDT_FUTURES:
        return BitgetUtaV3InstType.USDT_FUTURES;
      case USDC_FUTURES:
        return BitgetUtaV3InstType.USDC_FUTURES;
      case COIN_FUTURES:
        return BitgetUtaV3InstType.COIN_FUTURES;
      default:
        return BitgetUtaV3InstType.SPOT;
    }
  }

  /**
   * Instrument for a pushed {@code category}+{@code symbol} pair (orders and fills carry both;
   * positions do not and must be resolved from the caller's instrument instead). Dated delivery
   * symbols (base+quote+{@code MMdd}, e.g. {@code BTCUSD1226}) map to {@link FuturesContract} with
   * the expiry suffix as prompt, mirroring the REST catalog identity; perpetuals keep prompt
   * {@code PERP}.
   */
  public Instrument toInstrument(String category, String symbol) {
    BitgetUtaV3Category parsed = null;
    if (category != null) {
      for (BitgetUtaV3Category candidate : BitgetUtaV3Category.values()) {
        if (candidate.getWireName().equalsIgnoreCase(category)) {
          parsed = candidate;
          break;
        }
      }
    }
    if (parsed != null && parsed.isDerivative()) {
      String deliverySuffix = deliverySuffix(symbol);
      if (deliverySuffix != null) {
        return new FuturesContract(
            parseCurrencyPair(symbol.substring(0, symbol.length() - deliverySuffix.length())),
            deliverySuffix);
      }
      return new FuturesContract(parseCurrencyPair(symbol), "PERP");
    }
    return parseCurrencyPair(symbol);
  }

  /**
   * Trailing 4-digit delivery expiry suffix ({@code MMdd}) of a derivative symbol, e.g. {@code
   * 1226} for {@code BTCUSD1226}, or {@code null} for unsuffixed perpetual symbols. Spot/perpetual
   * symbols always end with the quote currency code, never with digits, so a digit suffix uniquely
   * identifies a dated delivery contract.
   */
  private static String deliverySuffix(String symbol) {
    if (symbol == null || symbol.length() <= 4) {
      return null;
    }
    String suffix = symbol.substring(symbol.length() - 4);
    for (int i = 0; i < suffix.length(); i++) {
      if (suffix.charAt(i) < '0' || suffix.charAt(i) > '9') {
        return null;
      }
    }
    return suffix;
  }

  /**
   * Quote currencies the provider pairs against, in preference order. Bitget lists both USDT and
   * USDC margin/futures families, USD coin-margined contracts, and BTC/ETH margined contracts;
   * these disambiguate concatenated symbols whose prefix is also a registered currency (e.g. {@code
   * BCHUSD} must be {@code BCH/USD}, not the coincidental {@code BC/HUSD}).
   */
  private static final Set<Currency> PREFERRED_QUOTE_CURRENCIES =
      Set.of(Currency.USDT, Currency.USDC, Currency.USD, Currency.BTC, Currency.ETH);

  private CurrencyPair parseCurrencyPair(String symbol) {
    // Bitget symbols are base+counter with no separator, e.g. BTCUSDT. Prefer a split where both
    // halves are registered currencies; when several splits are valid, the provider's quote
    // currency disambiguates (USDT/USDC/USD/BTC/ETH first), then the longest base (BCH/USD rather
    // than the coincidental BC/HUSD for BCHUSD).
    Currency bestBase = null;
    Currency bestCounter = null;
    int bestBaseLength = 0;
    for (int split = 1; split < symbol.length(); split++) {
      Currency base = Currency.getInstanceNoCreate(symbol.substring(0, split));
      Currency counter = Currency.getInstanceNoCreate(symbol.substring(split));
      if (base == null || counter == null) {
        continue;
      }
      boolean candidatePreferred = PREFERRED_QUOTE_CURRENCIES.contains(counter);
      boolean bestPreferred =
          bestCounter != null && PREFERRED_QUOTE_CURRENCIES.contains(bestCounter);
      if (bestCounter == null
          || (candidatePreferred && !bestPreferred)
          || (candidatePreferred == bestPreferred && split > bestBaseLength)) {
        bestBase = base;
        bestCounter = counter;
        bestBaseLength = split;
      }
    }
    if (bestCounter != null) {
      return new CurrencyPair(bestBase, bestCounter);
    }
    // unknown base (e.g. 1000PEPE): longest registered suffix wins as the counter, the remainder
    // is the base (1000PEPE+USDT for 1000PEPEUSDT)
    for (int split = symbol.length() - 1; split > 0; split--) {
      Currency counter = Currency.getInstanceNoCreate(symbol.substring(split));
      if (counter != null) {
        return new CurrencyPair(Currency.getInstance(symbol.substring(0, split)), counter);
      }
    }
    throw new IllegalArgumentException(
        "Cannot parse Bitget symbol into a currency pair: " + symbol);
  }

  /**
   * XChange ticker for a v3 ticker push; the timestamp comes from the push envelope. The provider's
   * {@code price24hPcnt} is a decimal fraction ({@code 0.015} = 1.5%) while {@link
   * Ticker.Builder#percentageChange} contracts percentage units ({@code 1} = 1%), so the wire value
   * is scaled by 100.
   */
  public Ticker toTicker(BitgetUtaV3TickerData dto, Instrument instrument, Long envelopeTs) {
    return new Ticker.Builder()
        .instrument(instrument)
        .open(dto.getOpenPrice24h())
        .last(dto.getLastPrice())
        .bid(dto.getBidPrice())
        .ask(dto.getAskPrice())
        .high(dto.getHighPrice24h())
        .low(dto.getLowPrice24h())
        .volume(dto.getVolume24h())
        .quoteVolume(dto.getTurnover24h())
        .timestamp(envelopeTs == null ? null : new Date(envelopeTs))
        .bidSize(dto.getBidSize())
        .askSize(dto.getAskSize())
        .percentageChange(
            dto.getPrice24hPcnt() == null
                ? null
                : dto.getPrice24hPcnt().movePointRight(2))
        .build();
  }

  /** XChange candle data for a v3 kline push. */
  public CandleStickData toCandle(BitgetUtaV3KlineData dto, Instrument instrument) {
    CandleStick candle =
        new CandleStick(
            dto.getStart() == null ? null : Instant.ofEpochMilli(dto.getStart()),
            dto.getOpen(),
            dto.getClose(),
            dto.getHigh(),
            dto.getLow(),
            dto.getClose(),
            dto.getVolume(),
            dto.getTurnover(),
            null,
            null,
            null,
            null,
            null,
            false);
    return new CandleStickData(instrument, List.of(candle));
  }

  /** XChange public trade for a v3 {@code publicTrade} push. */
  public Trade toTrade(BitgetUtaV3PublicTradeData dto, Instrument instrument) {
    return Trade.builder()
        .type("buy".equals(dto.getSide()) ? OrderType.BID : OrderType.ASK)
        .originalAmount(dto.getVolume())
        .instrument(instrument)
        .price(dto.getPrice())
        .timestamp(dto.getTimestamp() == null ? null : new Date(dto.getTimestamp()))
        .id(dto.getId())
        .build();
  }

  /** XChange open position for a v3 position push; instrument resolved by the caller. */
  public OpenPosition toOpenPosition(BitgetUtaV3PositionData dto, Instrument instrument) {
    return OpenPosition.builder()
        .instrument(instrument)
        .type("long".equals(dto.getPosSide()) ? OpenPosition.Type.LONG : OpenPosition.Type.SHORT)
        .marginMode(
            "isolated".equals(dto.getMarginMode())
                ? OpenPosition.MarginMode.ISOLATED
                : OpenPosition.MarginMode.CROSS)
        .size(dto.getSize())
        .price(dto.getAvgPrice())
        .liquidationPrice(dto.getLiquidationPrice())
        .unRealisedPnl(dto.getUnrealisedPnl())
        .createdAt(toInstant(dto.getCreatedTime()))
        .updatedAt(toInstant(dto.getUpdatedTime()))
        .build();
  }

  /**
   * XChange balance for a per-coin entry of a v3 account push. Mirrors the REST account
   * adaptation: {@code total} is the coin equity (balance + frozen margin + unrealized PnL) and
   * {@code borrowed} is the outstanding debt, so streamed and REST consumers see the same
   * account value and exposure for leveraged UTA balances.
   */
  public Balance toBalance(BitgetUtaV3CoinData dto, Currency currency) {
    return new Balance.Builder()
        .currency(currency)
        .total(dto.getEquity() != null ? dto.getEquity() : dto.getBalance())
        .available(dto.getAvailable())
        .frozen(dto.getLocked())
        .borrowed(dto.getDebts() != null ? dto.getDebts() : BigDecimal.ZERO)
        .build();
  }

  /**
   * XChange user trade for a v3 fill push; instrument resolved from push category+symbol.
   *
   * <p>Fee detail may carry entries in several currencies (e.g. a discount token plus the trading
   * currency). {@link UserTrade} carries a single fee amount and currency, so only entries sharing
   * the first fee coin are summed; entries in other denominations are excluded rather than added
   * across currencies and mislabeled. Mirrors {@link BitgetUtaV3Adapters#toUserTrade}.
   */
  public UserTrade toUserTrade(BitgetUtaV3FillData dto, Instrument instrument) {
    BigDecimal fee = null;
    Currency feeCurrency = null;
    if (dto.getFeeDetail() != null && !dto.getFeeDetail().isEmpty()) {
      String feeCoin = null;
      for (org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3Order.BitgetUtaV3Fee detail :
          dto.getFeeDetail()) {
        if (detail.getFeeCoin() == null || detail.getFee() == null) {
          continue;
        }
        if (feeCoin == null) {
          feeCoin = detail.getFeeCoin();
          feeCurrency = Currency.getInstance(feeCoin);
        }
        if (feeCoin.equals(detail.getFeeCoin())) {
          fee = fee == null ? detail.getFee() : fee.add(detail.getFee());
        }
      }
    }
    return UserTrade.builder()
        .type("buy".equals(dto.getSide()) ? OrderType.BID : OrderType.ASK)
        .originalAmount(dto.getExecQty())
        .instrument(instrument)
        .price(dto.getExecPrice())
        .timestamp(dto.getExecTime() == null ? null : new Date(dto.getExecTime()))
        .id(dto.getExecId())
        .orderId(dto.getOrderId())
        .orderUserReference(dto.getClientOid())
        .feeAmount(fee)
        .feeCurrency(feeCurrency)
        .build();
  }

  /**
   * Wire kline interval for an XChange interval. Bitget supports 1m/3m/5m/15m/30m/1H/4H/6H/12H/1D.
   *
   * @throws IllegalArgumentException for intervals Bitget does not offer
   */
  public String toInterval(CandleStickInterval interval) {
    switch (interval) {
      case m1:
        return "1m";
      case m3:
        return "3m";
      case m5:
        return "5m";
      case m15:
        return "15m";
      case m30:
        return "30m";
      case h1:
        return "1H";
      case h4:
        return "4H";
      case h6:
        return "6H";
      case h12:
        return "12H";
      case d1:
        return "1D";
      default:
        throw new IllegalArgumentException(
            "Unsupported Bitget UTA v3 kline interval "
                + interval
                + "; supported: 1m, 3m, 5m, 15m, 30m, 1H, 4H, 6H, 12H, 1D");
    }
  }

  private Instant toInstant(String epochMillis) {
    if (epochMillis == null || epochMillis.isEmpty()) {
      return null;
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(epochMillis));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
