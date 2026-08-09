package info.bitrich.xchangestream.kraken;

import info.bitrich.xchangestream.kraken.dto.request.KrakenSubscribeMessage;
import info.bitrich.xchangestream.kraken.dto.request.KrakenUnsubscribeMessage;
import info.bitrich.xchangestream.kraken.dto.request.KrakenUnsubscribeMessage.Params;
import info.bitrich.xchangestream.kraken.dto.response.KrakenBalancesMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenExecutionsMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenTickerMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenTradeMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.UserTrade;

@UtilityClass
public class KrakenStreamingAdapters {

  public Ticker toTicker(KrakenTickerMessage.Payload payload) {
    if (payload == null) {
      return null;
    }

    return new Ticker.Builder()
        .instrument(payload.getCurrencyPair())
        .last(payload.getLastPrice())
        .bid(payload.getBestBidPrice())
        .bidSize(payload.getBestBidSize())
        .ask(payload.getBestAskPrice())
        .askSize(payload.getBestAskSize())
        .high(payload.getHigh24h())
        .low(payload.getLow24h())
        .volume(payload.getAssetVolume24h())
        .percentageChange(payload.getChangePercentage24h())
        .build();
  }

  public Trade toTrade(KrakenTradeMessage.Payload payload) {
    if (payload == null) {
      return null;
    }

    return Trade.builder()
        .type(payload.getOrderSide())
        .originalAmount(payload.getAssetAmount())
        .instrument(payload.getCurrencyPair())
        .price(payload.getPrice())
        .timestamp(toDate(payload.getCreatedAt()))
        .id(payload.getId())
        .build();
  }

  /**
   * Computes the Kraken v2 order book checksum: CRC32 over the top 10 bid levels (best first)
   * followed by the top 10 ask levels (best first), each level formatted as {@code price:qty}
   * with 8 decimal places and levels joined by commas.
   *
   * @param bids bid levels by price (any order)
   * @param asks ask levels by price (any order)
   * @return CRC32 checksum
   */
  public static long checksum(
      Map<BigDecimal, BigDecimal> bids, Map<BigDecimal, BigDecimal> asks) {
    List<String> levels = new ArrayList<>();
    appendLevels(levels, bids, true); // best bid first
    appendLevels(levels, asks, false); // best ask first
    CRC32 crc32 = new CRC32();
    crc32.update(String.join(",", levels).getBytes(StandardCharsets.UTF_8));
    return crc32.getValue();
  }

  private static void appendLevels(
      List<String> levels, Map<BigDecimal, BigDecimal> levelsByPrice, boolean descending) {
    levelsByPrice.entrySet().stream()
        .sorted(
            descending
                ? Map.Entry.<BigDecimal, BigDecimal>comparingByKey().reversed()
                : Map.Entry.<BigDecimal, BigDecimal>comparingByKey())
        .limit(10)
        .forEach(
            entry ->
                levels.add(
                    entry.getKey().setScale(8, RoundingMode.HALF_UP).toPlainString()
                        + ':'
                        + entry.getValue().setScale(8, RoundingMode.HALF_UP).toPlainString()));
  }

  /** Returns unique subscription id. Can be used as key for subscriptions caching */
  public String toSubscriptionUniqueId(String channelName, CurrencyPair currencyPair) {
    return Stream.of(channelName, currencyPair)
        .filter(Objects::nonNull)
        .map(Objects::toString)
        .collect(Collectors.joining("_"));
  }

  public KrakenSubscribeMessage toSubscribeMessage(String channelName, CurrencyPair currencyPair) {
    return KrakenSubscribeMessage.builder()
        .params(
            KrakenSubscribeMessage.Params.builder()
                .channel(channelName)
                .currencyPair(currencyPair)
                .build())
        .build();
  }

  public KrakenUnsubscribeMessage toUnsubscribeMessage(String subscriptionUniqueId) {
    var splitted = subscriptionUniqueId.split("_");
    var channelName = splitted[0];
    var currencyPair = splitted.length > 1 ? new CurrencyPair(splitted[1]) : null;

    return KrakenUnsubscribeMessage.builder()
        .params(Params.builder().channel(channelName).currencyPair(currencyPair).build())
        .build();
  }

  public UserTrade toUserTrade(KrakenExecutionsMessage.Payload payload) {
    if (payload == null) {
      return null;
    }

    return UserTrade.builder()
        .type(payload.getOrderSide())
        .originalAmount(payload.getAssetAmount())
        .instrument(payload.getCurrencyPair())
        .price(payload.getAverageTradePrice())
        .timestamp(toDate(payload.getCreatedAt()))
        .id(payload.getTradeId())
        .orderId(payload.getOrderId())
        .feeAmount(payload.getFeeAmount())
        .feeCurrency(payload.getFeeCurrency())
        .orderUserReference(payload.getClientOid())
        .build();
  }

  public Balance toBalance(KrakenBalancesMessage.Payload payload) {
    if (payload == null) {
      return null;
    }

    return new Balance(payload.getCurrency(), payload.getBalance());
  }

  public Date toDate(Instant instant) {
    return Optional.ofNullable(instant).map(Date::from).orElse(null);
  }
}
