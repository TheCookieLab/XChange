package info.bitrich.xchangestream.mexc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;

/**
 * Converts decoded MEXC Spot v3 protobuf pushes (canonical JSON of {@link PushDataV3ApiWrapper})
 * into XChange DTOs. Provider values are decimal strings; they are parsed exactly and never
 * rounded.
 */
public final class MexcV3StreamingAdapters {

  private MexcV3StreamingAdapters() {}

  /** Re-parses the canonical JSON of a binary push into the typed wrapper. */
  public static PushDataV3ApiWrapper parsePush(String canonicalJson)
      throws InvalidProtocolBufferException {
    return MexcV3ProtoCodec.fromJson(canonicalJson);
  }

  /**
   * Adapts an {@code aggre.bookTicker} push. The timestamp is the wrapper's message generation
   * time ({@code createTime}, epoch millis).
   */
  public static Ticker adaptBookTicker(String canonicalJson, CurrencyPair currencyPair)
      throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper wrapper = parsePush(canonicalJson);
    if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PUBLICAGGREBOOKTICKER) {
      throw new IllegalArgumentException(
          "Unexpected MEXC v3 push body for bookTicker channel: " + wrapper.getBodyCase());
    }
    com.mxc.push.common.protobuf.PublicAggreBookTickerV3Api ticker =
        wrapper.getPublicAggreBookTicker();
    return new Ticker.Builder()
        .currencyPair(currencyPair)
        .bid(new BigDecimal(ticker.getBidPrice()))
        .bidSize(new BigDecimal(ticker.getBidQuantity()))
        .ask(new BigDecimal(ticker.getAskPrice()))
        .askSize(new BigDecimal(ticker.getAskQuantity()))
        .timestamp(Date.from(Instant.ofEpochMilli(wrapper.getCreateTime())))
        .build();
  }

  /**
   * Adapts an {@code aggre.deals} push. {@code tradeType} 1 = buy (BID), 2 = sell (ASK); each item
   * carries its own {@code tradeId} and timestamp (epoch millis).
   */
  public static List<Trade> adaptAggreDeals(String canonicalJson, CurrencyPair currencyPair)
      throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper wrapper = parsePush(canonicalJson);
    if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PUBLICAGGREDEALS) {
      throw new IllegalArgumentException(
          "Unexpected MEXC v3 push body for aggre.deals channel: " + wrapper.getBodyCase());
    }
    com.mxc.push.common.protobuf.PublicAggreDealsV3Api deals = wrapper.getPublicAggreDeals();
    List<Trade> trades = new ArrayList<>(deals.getDealsCount());
    for (com.mxc.push.common.protobuf.PublicAggreDealsV3ApiItem item : deals.getDealsList()) {
      OrderType type = item.getTradeType() == 1 ? OrderType.BID : OrderType.ASK;
      trades.add(
          Trade.builder()
              .type(type)
              .originalAmount(new BigDecimal(item.getQuantity()))
              .instrument(currencyPair)
              .price(new BigDecimal(item.getPrice()))
              .timestamp(Date.from(Instant.ofEpochMilli(item.getTime())))
              .id(item.getTradeId())
              .build());
    }
    return trades;
  }

  /**
   * Adapts a {@code kline} push into a single-candle {@link CandleStickData}. MEXC pushes the
   * latest (in-progress) candle every second; the candle is therefore marked not completed.
   * {@code windowStart}/{@code windowEnd} are epoch seconds.
   */
  public static CandleStickData adaptKline(String canonicalJson, CurrencyPair currencyPair)
      throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper wrapper = parsePush(canonicalJson);
    if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PUBLICSPOTKLINE) {
      throw new IllegalArgumentException(
          "Unexpected MEXC v3 push body for kline channel: " + wrapper.getBodyCase());
    }
    com.mxc.push.common.protobuf.PublicSpotKlineV3Api kline = wrapper.getPublicSpotKline();
    BigDecimal open = new BigDecimal(kline.getOpeningPrice());
    BigDecimal close = new BigDecimal(kline.getClosingPrice());
    CandleStick stick =
        new CandleStick(
            Instant.ofEpochSecond(kline.getWindowStart()),
            open,
            close,
            new BigDecimal(kline.getHighestPrice()),
            new BigDecimal(kline.getLowestPrice()),
            close,
            new BigDecimal(kline.getVolume()),
            new BigDecimal(kline.getAmount()),
            null,
            null,
            null,
            null,
            null,
            false);
    return new CandleStickData(currencyPair, Collections.singletonList(stick));
  }
}
