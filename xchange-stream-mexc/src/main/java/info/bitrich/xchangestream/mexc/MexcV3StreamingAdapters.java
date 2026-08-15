package info.bitrich.xchangestream.mexc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PrivateAccountV3Api;
import com.mxc.push.common.protobuf.PrivateDealsV3Api;
import com.mxc.push.common.protobuf.PrivateOrdersV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;

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

  /**
   * Adapts a {@code spot@private.account.v3.api.pb} push. The provider pushes one event per
   * currency; {@code available = balanceAmount}, {@code frozen = frozenAmount}, {@code total =
   * available + frozen}, and the timestamp is the event time (epoch millis). This matches the REST
   * account snapshot mapping ({@code free}/{@code locked} in {@code MexcV3Adapters.adaptWallet}).
   */
  public static Balance adaptAccountPush(String canonicalJson)
      throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper wrapper = parsePush(canonicalJson);
    if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PRIVATEACCOUNT) {
      throw new IllegalArgumentException(
          "Unexpected MEXC v3 push body for private.account channel: " + wrapper.getBodyCase());
    }
    PrivateAccountV3Api account = wrapper.getPrivateAccount();
    BigDecimal available = new BigDecimal(account.getBalanceAmount());
    BigDecimal frozen = new BigDecimal(account.getFrozenAmount());
    return new Balance.Builder()
        .currency(Currency.getInstance(account.getVcoinName()))
        .total(available.add(frozen))
        .available(available)
        .frozen(frozen)
        .timestamp(new Date(account.getTime()))
        .build();
  }

  /**
   * Adapts a {@code spot@private.orders.v3.api.pb} push. {@code tradeType} 1 = buy (BID), 2 = sell
   * (ASK); {@code orderType} 5 = market, anything else = limit. {@code clientId} maps to the
   * order's user reference. The pair comes from the wrapper's {@code symbol}.
   */
  public static Order adaptOrderPush(String canonicalJson) throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper wrapper = parsePush(canonicalJson);
    if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PRIVATEORDERS) {
      throw new IllegalArgumentException(
          "Unexpected MEXC v3 push body for private.orders channel: " + wrapper.getBodyCase());
    }
    PrivateOrdersV3Api order = wrapper.getPrivateOrders();
    OrderType type = order.getTradeType() == 1 ? OrderType.BID : OrderType.ASK;
    CurrencyPair pair = MexcV3Symbols.toCurrencyPair(wrapper.getSymbol());
    BigDecimal originalAmount = new BigDecimal(order.getQuantity());
    BigDecimal averagePrice = new BigDecimal(order.getAvgPrice());
    BigDecimal cumulativeAmount = new BigDecimal(order.getCumulativeQuantity());
    Date timestamp = new Date(order.getCreateTime());
    String id = order.getId();
    String userReference = order.getClientId();
    OrderStatus status = mapOrderStatus(order.getStatus());
    if (order.getOrderType() == ORDER_TYPE_MARKET) {
      return new MarketOrder(
          type, originalAmount, pair, id, timestamp, averagePrice, cumulativeAmount, null, status,
          userReference);
    }
    return new LimitOrder(
        type, originalAmount, pair, id, timestamp, new BigDecimal(order.getPrice()), averagePrice,
        cumulativeAmount, null, status, userReference);
  }

  /**
   * Adapts a {@code spot@private.deals.v3.api.pb} push. {@code tradeType} 1 = buy (BID), 2 = sell
   * (ASK); {@code clientOrderId} maps to {@link UserTrade#getOrderUserReference()}. The pair comes
   * from the wrapper's {@code symbol}.
   */
  public static UserTrade adaptUserTradePush(String canonicalJson)
      throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper wrapper = parsePush(canonicalJson);
    if (wrapper.getBodyCase() != PushDataV3ApiWrapper.BodyCase.PRIVATEDEALS) {
      throw new IllegalArgumentException(
          "Unexpected MEXC v3 push body for private.deals channel: " + wrapper.getBodyCase());
    }
    PrivateDealsV3Api deal = wrapper.getPrivateDeals();
    return UserTrade.builder()
        .type(deal.getTradeType() == 1 ? OrderType.BID : OrderType.ASK)
        .originalAmount(new BigDecimal(deal.getQuantity()))
        .instrument(MexcV3Symbols.toCurrencyPair(wrapper.getSymbol()))
        .price(new BigDecimal(deal.getPrice()))
        .timestamp(new Date(deal.getTime()))
        .id(deal.getTradeId())
        .orderId(deal.getOrderId())
        .orderUserReference(deal.getClientOrderId())
        .feeAmount(new BigDecimal(deal.getFeeAmount()))
        .feeCurrency(Currency.getInstance(deal.getFeeCurrency()))
        .build();
  }

  /** MEXC order status codes on the private orders channel. */
  private static OrderStatus mapOrderStatus(int status) {
    switch (status) {
      case 1:
        return OrderStatus.NEW;
      case 2:
        return OrderStatus.FILLED;
      case 3:
        return OrderStatus.PARTIALLY_FILLED;
      case 4:
        return OrderStatus.CANCELED;
      case 5:
        return OrderStatus.PARTIALLY_CANCELED;
      default:
        return OrderStatus.UNKNOWN;
    }
  }

  /** MEXC order type code for market orders on the private orders channel. */
  private static final int ORDER_TYPE_MARKET = 5;
}
