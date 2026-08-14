package org.knowm.xchange.okx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.*;
import org.knowm.xchange.dto.account.OpenPosition.Type;
import org.knowm.xchange.dto.account.Wallet.WalletFeature;
import org.knowm.xchange.dto.marketdata.*;
import org.knowm.xchange.dto.marketdata.FundingRate.FundingRateInterval;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.meta.WalletHealth;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.dto.trade.LimitOrder.Builder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.*;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk.PositionData;
import org.knowm.xchange.okx.dto.account.OkxTradeFee.FiatList;
import org.knowm.xchange.okx.dto.marketdata.*;
import org.knowm.xchange.okx.dto.trade.*;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxAdapters {

  private static final String TRADING_WALLET_ID = "trading";
  private static final String FOUNDING_WALLET_ID = "founding";
  private static final String FUTURES_WALLET_ID = "futures";
  static final Map<Instrument, Long> instrumentToInstrumentIdMap = new HashMap<>();

  /**
   * Resolves the OKX numeric instrument code for an instrument, preferring the adapted wire
   * instrument and falling back to the direct key. After the unified USD orderbook revamp, remote
   * init registers the server's {@code BTC/USD} code while legacy callers still trade {@code
   * BTC/USDC}; {@link #adaptInstrument(Instrument)} maps that pair to the wire {@code BTC-USD},
   * whose reverse adaptation resolves to the registered key. The wire key is preferred so a
   * currency-pair caller always receives the code that matches the {@code instId} sent on the wire
   * (a direct {@code BTC/USDC} registration, if present, belongs to a different instrument type and
   * would not be valid for the adapted {@code BTC-USD} request).
   *
   * @param instrument the instrument to resolve
   * @return the numeric instrument code, or {@code null} when unresolvable
   */
  static Long instrumentCode(Instrument instrument) {
    Long code = instrumentToInstrumentIdMap.get(adaptOkxInstrumentId(adaptInstrument(instrument)));
    if (code == null) {
      code = instrumentToInstrumentIdMap.get(instrument);
    }
    return code;
  }

  /**
   * Resolves instrument metadata for a caller-supplied instrument, preferring the direct key and
   * falling back to the adapted wire instrument. After the unified USD orderbook revamp, remote
   * init registers metadata under the server's instrument (e.g. {@code BTC/USD}) while legacy
   * callers still request the quote alias (e.g. {@code BTC/USDC}); the direct lookup would miss and
   * dereference null. Mirrors {@link #instrumentCode(Instrument)}.
   *
   * @param instrument the instrument whose metadata to resolve
   * @param exchangeMetaData the metadata to resolve against
   * @return the instrument metadata, or {@code null} when unresolvable
   */
  public static InstrumentMetaData instrumentMetaData(
      Instrument instrument, ExchangeMetaData exchangeMetaData) {
    InstrumentMetaData metaData = exchangeMetaData.getInstruments().get(instrument);
    if (metaData == null && instrument instanceof CurrencyPair) {
      CurrencyPair pair = (CurrencyPair) instrument;
      if (pair.getCounter().equals(Currency.USDC)) {
        metaData = exchangeMetaData.getInstruments().get(new CurrencyPair(pair.getBase(), Currency.USD));
      }
    }
    return metaData;
  }

  public static UserTrades adaptUserTrades(
      List<OkxOrderDetails> okxTradeHistory, ExchangeMetaData exchangeMetaData) {
    List<UserTrade> userTradeList = new ArrayList<>();

    okxTradeHistory.forEach(
        okxOrderDetails -> {
          Instrument instrument = adaptOkxInstrumentId(okxOrderDetails.getInstrumentId());
          userTradeList.add(
              UserTrade.builder()
                  .originalAmount(
                      convertContractSizeToVolume(
                          new BigDecimal(okxOrderDetails.getAmount()),
                          instrument,
                          exchangeMetaData.getInstruments().get(instrument).getContractValue(),
                          orderPrice(okxOrderDetails)))
                  .instrument(instrument)
                  .price(new BigDecimal(okxOrderDetails.getAverageFilledPrice()))
                  .type(adaptOkxOrderSideToOrderType(okxOrderDetails.getSide()))
                  .id(okxOrderDetails.getOrderId())
                  .orderId(okxOrderDetails.getOrderId())
                  .timestamp(
                      Date.from(
                          Instant.ofEpochMilli(Long.parseLong(okxOrderDetails.getUpdateTime()))))
                  .feeAmount(new BigDecimal(okxOrderDetails.getFee()))
                  .feeCurrency(new Currency(okxOrderDetails.getFeeCurrency()))
                  .orderUserReference(okxOrderDetails.getClientOrderId())
                  .build());
        });

    return new UserTrades(userTradeList, TradeSortType.SortByTimestamp);
  }

  public static LimitOrder adaptOrder(OkxOrderDetails order, ExchangeMetaData exchangeMetaData) {
    Instrument instrument = adaptOkxInstrumentId(order.getInstrumentId());
    return new LimitOrder(
        "buy".equals(order.getSide()) ? OrderType.BID : OrderType.ASK,
        convertContractSizeToVolume(
            new BigDecimal(order.getAmount()),
            instrument,
            exchangeMetaData.getInstruments().get(instrument).getContractValue(),
            orderPrice(order)),
        instrument,
        order.getOrderId(),
        new Date(Long.parseLong(order.getCreationTime())),
        new BigDecimal(order.getPrice()),
        order.getAverageFilledPrice().isEmpty()
            ? BigDecimal.ZERO
            : new BigDecimal(order.getAverageFilledPrice()),
        new BigDecimal(order.getAccumulatedFill()),
        new BigDecimal(order.getFee()),
        "live".equals(order.getState())
            ? OrderStatus.OPEN
            : OrderStatus.valueOf(order.getState().toUpperCase(Locale.ENGLISH)),
        order.getClientOrderId());
  }

  private static Order adaptOrderChange(
      OkxOrderDetails okxOrder, ExchangeMetaData exchangeMetaData) {
    Instrument instrument = adaptOkxInstrumentId(okxOrder.getInstrumentId());
    OrderType orderType = "buy".equals(okxOrder.getSide()) ? OrderType.BID : OrderType.ASK;
    Order order;
    if (okxOrder.getOrderType().equals(OkxOrderType.market.name())) {
      order =
          new MarketOrder.Builder(orderType, instrument)
              .originalAmount(
                  convertContractSizeToVolume(
                      new BigDecimal(okxOrder.getAmount()),
                      instrument,
                      exchangeMetaData.getInstruments().get(instrument).getContractValue(),
                      orderPrice(okxOrder)))
              .cumulativeAmount(
                  convertContractSizeToVolume(
                      new BigDecimal(okxOrder.getAccumulatedFill()),
                      instrument,
                      exchangeMetaData.getInstruments().get(instrument).getContractValue(),
                      orderPrice(okxOrder)))
              .id(okxOrder.getOrderId())
              .timestamp(new Date(Long.parseLong(okxOrder.getUpdateTime())))
              .averagePrice(new BigDecimal(okxOrder.getAverageFilledPrice()))
              .fee(new BigDecimal(okxOrder.getFee()).negate())
              .userReference(okxOrder.getClientOrderId())
              .orderStatus(
                  "live".equals(okxOrder.getState())
                      ? OrderStatus.OPEN
                      : OrderStatus.valueOf(okxOrder.getState().toUpperCase(Locale.ENGLISH)))
              .build();
    } else {
      order =
          new Builder(orderType, instrument)
              .originalAmount(
                  convertContractSizeToVolume(
                      new BigDecimal(okxOrder.getAmount()),
                      instrument,
                      exchangeMetaData.getInstruments().get(instrument).getContractValue(),
                      orderPrice(okxOrder)))
              .cumulativeAmount(
                  convertContractSizeToVolume(
                      new BigDecimal(okxOrder.getAccumulatedFill()),
                      instrument,
                      exchangeMetaData.getInstruments().get(instrument).getContractValue(),
                      orderPrice(okxOrder)))
              .id(okxOrder.getOrderId())
              .timestamp(new Date(Long.parseLong(okxOrder.getUpdateTime())))
              .limitPrice(
                  okxOrder.getLastFilledPrice().isEmpty()
                          || okxOrder.getLastFilledPrice().equals("0")
                      ? new BigDecimal(okxOrder.getPrice())
                      : new BigDecimal(okxOrder.getLastFilledPrice()))
              .averagePrice(new BigDecimal(okxOrder.getAverageFilledPrice()))
              .fee(new BigDecimal(okxOrder.getFee()).negate())
              .userReference(okxOrder.getClientOrderId())
              .orderStatus(
                  "live".equals(okxOrder.getState())
                      ? OrderStatus.OPEN
                      : OrderStatus.valueOf(okxOrder.getState().toUpperCase(Locale.ENGLISH)))
              .build();
    }
    return order;
  }

  public static OpenOrders adaptOpenOrders(
      List<OkxOrderDetails> orders, ExchangeMetaData exchangeMetaData) {
    List<LimitOrder> openOrders =
        orders.stream()
            .map(order -> OkxAdapters.adaptOrder(order, exchangeMetaData))
            .collect(Collectors.toList());
    return new OpenOrders(openOrders);
  }

  public static List<Order> adaptOrdersChanges(
      List<OkxOrderDetails> okxOrderDetailsList, ExchangeMetaData exchangeMetaData) {
    List<Order> orders =
        okxOrderDetailsList.stream()
            .map(order -> OkxAdapters.adaptOrderChange(order, exchangeMetaData))
            .collect(Collectors.toList());
    return orders;
  }

  public static OkxAmendOrderRequest adaptAmendOrder(
      LimitOrder order, ExchangeMetaData exchangeMetaData) {
    return OkxAmendOrderRequest.builder()
        .instrumentId(adaptInstrument(order.getInstrument()))
        .instIdCode(instrumentCode(order.getInstrument()).toString())
        .orderId(order.getId())
        .clientOrderId(order.getUserReference())
        .amendedAmount(convertVolumeToContractSize(order, exchangeMetaData))
        .amendedPrice(order.getLimitPrice().toString())
        .build();
  }

  public static OkxOrderRequest adaptOrder(
      MarketOrder order, ExchangeMetaData exchangeMetaData, String accountLevel) {
    return OkxOrderRequest.builder()
        .instrumentId(adaptInstrument(order.getInstrument()))
        .instIdCode(instrumentCode(order.getInstrument()).toString())
        .tradeMode(adaptTradeMode(order.getInstrument(), accountLevel))
        .side(getSide(order))
        .posSide(null) // PosSide should come as a input from an extended LimitOrder class to
        // support Futures/Swap capabilities of Okx, till then it should be null to
        // perform "net" orders
        .reducePosition(order.hasFlag(OkxOrderFlags.REDUCE_ONLY))
        .clientOrderId(order.getUserReference())
        .orderType(OkxOrderType.market.name())
        .amount(convertVolumeToContractSize(order, exchangeMetaData))
        .tradeQuoteCcy(order.getInstrument().getCounter().getCurrencyCode())
        .build();
  }

  /**
   * contract_size to volume: crypto-margined contracts：contract_size,volume(contract_size to
   * volume:volume = sz*ctVal/price) USDT-margined contracts:sz,volume,USDT(contract_size to
   * volume:volume = contract_size*ctVal;contract_size to USDT:volume = contract_size*ctVal*price)
   * OPTION:volume = sz*ctMult volume to contract_size: crypto-margined
   * contracts：contract_size,volume(coin to contract_size:contract_size = volume*price/ctVal)
   * USDT-margined contracts:contract_size,volume,USDT(coin to contract_size:contract_size =
   * volume/ctVal;USDT to contract_size:contract_size = volume/ctVal/price)
   */
  private static String convertVolumeToContractSize(
      Order order, ExchangeMetaData exchangeMetaData) {
    InstrumentMetaData metaData = exchangeMetaData.getInstruments().get(order.getInstrument());
    if (!(order.getInstrument() instanceof FuturesContract
        || order.getInstrument() instanceof OptionsContract)) {
      return order.getOriginalAmount().toString();
    }
    BigDecimal size = order.getOriginalAmount();
    if (isInverseContract(order.getInstrument())) {
      BigDecimal price = order instanceof LimitOrder ? ((LimitOrder) order).getLimitPrice() : null;
      if (price != null && price.signum() > 0) {
        size = size.multiply(price).divide(metaData.getContractValue(), 20, RoundingMode.HALF_DOWN);
      } else {
        // Market orders carry no price, so the price-dependent inverse conversion cannot run; keep
        // the plain contract divide as the documented fallback.
        size = size.divide(metaData.getContractValue(), 20, RoundingMode.HALF_DOWN);
      }
    } else {
      size = size.divide(metaData.getContractValue(), 20, RoundingMode.HALF_DOWN);
    }
    return size.stripTrailingZeros().toPlainString();
  }

  private static BigDecimal convertContractSizeToVolume(
      BigDecimal okxSize, Instrument instrument, BigDecimal contractValue, BigDecimal price) {
    if (!(instrument instanceof FuturesContract || instrument instanceof OptionsContract)) {
      return okxSize.stripTrailingZeros();
    }
    BigDecimal volume = okxSize.multiply(contractValue);
    if (isInverseContract(instrument) && price != null && price.signum() > 0) {
      volume = volume.divide(price, 20, RoundingMode.HALF_DOWN);
    }
    return volume.stripTrailingZeros();
  }

  /**
   * Returns the price to use when converting an OKX order-details record between contract size and
   * base volume: the average fill price when filled, otherwise the order price, or {@code null}
   * when neither is present.
   */
  private static BigDecimal orderPrice(OkxOrderDetails order) {
    String averageFilledPrice = order.getAverageFilledPrice();
    String price = !averageFilledPrice.isEmpty() ? averageFilledPrice : order.getPrice();
    return (price == null || price.isEmpty()) ? null : new BigDecimal(price);
  }

  /** True for inverse (crypto-margined) contracts, quoted against USD rather than USDT/USDC. */
  private static boolean isInverseContract(Instrument instrument) {
    return instrument instanceof FuturesContract && instrument.getCounter().equals(Currency.USD);
  }

  public static String adaptTradeMode(Instrument instrument, String accountLevel) {
    if (accountLevel.equals("3") || accountLevel.equals("4")) {
      return "cross";
    } else {
      return (instrument instanceof CurrencyPair) ? "cash" : "cross";
    }
  }

  public static OkxOrderRequest adaptOrder(
      LimitOrder order, ExchangeMetaData exchangeMetaData, String accountLevel) {
    return OkxOrderRequest.builder()
        .instrumentId(adaptInstrument(order.getInstrument()))
        .instIdCode(instrumentCode(order.getInstrument()).toString())
        .tradeMode(adaptTradeMode(order.getInstrument(), accountLevel))
        .side(getSide(order))
        .posSide(null) // PosSide should come as a input from an extended LimitOrder class to
        // support Futures/Swap capabilities of Okx, till then it should be null to
        // perform "net" orders
        .clientOrderId(order.getUserReference())
        .reducePosition(order.hasFlag(OkxOrderFlags.REDUCE_ONLY))
        .orderType(
            (order.hasFlag(OkxOrderFlags.POST_ONLY))
                ? OkxOrderType.post_only.name()
                : (order.hasFlag(OkxOrderFlags.OPTIMAL_LIMIT_IOC)
                        && order.getInstrument() instanceof FuturesContract)
                    ? OkxOrderType.optimal_limit_ioc.name()
                    : OkxOrderType.limit.name())
        .amount(convertVolumeToContractSize(order, exchangeMetaData))
        .price(order.getLimitPrice().toPlainString())
        .tradeQuoteCcy(order.getInstrument().getCounter().getCurrencyCode())
        .build();
  }

  private static String getSide(Order order) {
    String side = "";
    switch (order.getType()) {
      case BID:
        side = "buy";
        break;
      case ASK:
        side = "sell";
        break;
      case EXIT_ASK:
        side = "buy";
        order.getOrderFlags().add(OkxOrderFlags.REDUCE_ONLY);
        break;
      case EXIT_BID:
        side = "sell";
        order.getOrderFlags().add(OkxOrderFlags.REDUCE_ONLY);
        break;
    }
    return side;
  }

  public static LimitOrder adaptLimitOrder(
      OkxPublicOrder okxPublicOrder,
      Instrument instrument,
      OrderType orderType,
      Date timestamp,
      BigDecimal contractValue) {
    return adaptOrderbookOrder(
        convertContractSizeToVolume(
            okxPublicOrder.getVolume(), instrument, contractValue, okxPublicOrder.getPrice()),
        okxPublicOrder.getPrice(),
        instrument,
        orderType,
        timestamp);
  }

  public static OrderBook adaptOrderBook(
      List<OkxOrderbook> okxOrderbooks, Instrument instrument, ExchangeMetaData exchangeMetaData) {
    List<LimitOrder> asks = new ArrayList<>();
    List<LimitOrder> bids = new ArrayList<>();
    Date timeStamp = new Date(Long.parseLong(okxOrderbooks.get(0).getTs()));

    okxOrderbooks
        .get(0)
        .getAsks()
        .forEach(
            okxAsk ->
                asks.add(
                    adaptLimitOrder(
                        okxAsk,
                        instrument,
                        OrderType.ASK,
                        timeStamp,
                        instrumentMetaData(instrument, exchangeMetaData).getContractValue())));

    okxOrderbooks
        .get(0)
        .getBids()
        .forEach(
            okxBid ->
                bids.add(
                    adaptLimitOrder(
                        okxBid,
                        instrument,
                        OrderType.BID,
                        timeStamp,
                        instrumentMetaData(instrument, exchangeMetaData).getContractValue())));

    return new OrderBook(timeStamp, asks, bids);
  }

  public static OrderBook adaptOrderBook(
      OkxResponse<List<OkxOrderbook>> okxOrderbook,
      Instrument instrument,
      ExchangeMetaData exchangeMetaData) {
    return adaptOrderBook(okxOrderbook.getData(), instrument, exchangeMetaData);
  }

  public static LimitOrder adaptOrderbookOrder(
      BigDecimal amount,
      BigDecimal price,
      Instrument instrument,
      OrderType orderType,
      Date timestamp) {

    return new LimitOrder(orderType, amount, instrument, "", timestamp, price);
  }

  public static Ticker adaptTicker(OkxTicker okxTicker) {
    BigDecimal quoteVolume = BigDecimal.ZERO;
    // for new coins 24h volume can be zero and getLast null
    if ((okxTicker.getInstrumentType().equals("SWAP")
        || okxTicker.getInstrumentType().equals("FUTURES"))) {
      if (okxTicker.getLast() != null) {
        quoteVolume = okxTicker.getVolumeCurrency24h().multiply(okxTicker.getLast());
      }
    } else {
      quoteVolume = okxTicker.getVolumeCurrency24h();
    }
    return new Ticker.Builder()
        .instrument(adaptOkxInstrumentId(okxTicker.getInstrumentId()))
        .open(okxTicker.getOpen24h())
        .last(okxTicker.getLast())
        .bid(okxTicker.getBidPrice())
        .ask(okxTicker.getAskPrice())
        .high(okxTicker.getHigh24h())
        .low(okxTicker.getLow24h())
        // .vwap(null)
        .volume(
            (okxTicker.getInstrumentType().equals("SWAP")
                    || okxTicker.getInstrumentType().equals("FUTURES"))
                ? okxTicker.getVolumeCurrency24h()
                : okxTicker.getVolume24h())
        .quoteVolume(quoteVolume)
        .timestamp(okxTicker.getTimestamp())
        .bidSize(okxTicker.getBidSize())
        .askSize(okxTicker.getAskSize())
        .percentageChange(null)
        .build();
  }

  public static Instrument adaptOkxInstrumentId(String instrumentId) {
    String[] tokens = instrumentId.split("-");
    if (tokens.length == 2) {
      // SPOT or Margin
      return new CurrencyPair(tokens[0], tokens[1]);
    } else if (tokens.length == 3) {
      // Future Or Swap
      return new FuturesContract(instrumentId.replace("-", "/"));
    } else if (tokens.length == 5) {
      // Option
      return new OptionsContract(instrumentId.replace("-", "/"));
    }
    return null;
  }

  public static String adaptInstrument(Instrument instrument) {
    if (instrument instanceof CurrencyPair) {
      CurrencyPair pair = (CurrencyPair) instrument;
      String base = pair.getBase().getCurrencyCode();
      String counter = pair.getCounter().getCurrencyCode();
      // Adapt for USDC after delist:
      // https://www.okx.com/docs-v5/log_en/#2025-08-20-unified-usd-orderbook-revamp
      if ("USDC".equals(counter)) {
        counter = "USD";
      }

      return base + "-" + counter;
    } else {
      // OKX expects DASH, not slash
      return instrument.toString().replace("/", "-");
    }
  }

  /**
   * Adapts an OKX instrument DTO to the XChange {@link Instrument} using only native OKX fields.
   *
   * <p>SPOT and MARGIN instruments map to a {@link CurrencyPair} from {@code baseCcy}/{@code
   * quoteCcy}; SWAP instruments map to a perpetual {@link FuturesContract}; FUTURES instruments map
   * to a {@link FuturesContract} whose prompt is derived from the native {@code expTime}; OPTION
   * instruments map to an {@link OptionsContract} built from the native {@code expTime}, {@code
   * stk} and {@code optType}. Derivatives carry their base/quote pair in the native {@code uly}
   * field ({@code baseCcy}/{@code quoteCcy} are empty for them), so the pair is always derived from
   * {@code uly} for derivatives.
   *
   * <p>The result equals {@link #adaptOkxInstrumentId(String)} for SPOT, MARGIN, SWAP and FUTURES
   * instruments. For OPTION the two builders agree on pair, strike and type; the expiry date of the
   * string-parsed variant depends on the JVM default time zone while this builder uses the exact
   * {@code expTime} timestamp, so callers that need a consistent key across both paths must derive
   * it from a single builder.
   *
   * @param instrument the OKX instrument DTO
   * @return the adapted instrument, or {@code null} when the instrument type cannot be represented
   */
  public static Instrument adaptOkxInstrument(OkxInstrument instrument) {
    String instType = instrument.getInstrumentType();
    if (OkxInstType.SPOT.name().equals(instType) || OkxInstType.MARGIN.name().equals(instType)) {
      return new CurrencyPair(instrument.getBaseCurrency(), instrument.getQuoteCurrency());
    }
    CurrencyPair pair = underlyingToCurrencyPair(instrument.getUnderlying());
    if (pair == null) {
      return null;
    }
    if (OkxInstType.SWAP.name().equals(instType)) {
      return new FuturesContract(pair, "SWAP");
    }
    if (OkxInstType.FUTURES.name().equals(instType)) {
      return new FuturesContract(pair, formatExpiry(instrument.getExpiryTime()));
    }
    if (OkxInstType.OPTION.name().equals(instType)) {
      if (instrument.getExpiryTime() == null
          || instrument.getStrikePrice() == null
          || instrument.getOptionType() == null) {
        return null;
      }
      return new OptionsContract(
          pair,
          new Date(Long.parseLong(instrument.getExpiryTime())),
          new BigDecimal(instrument.getStrikePrice()),
          OptionsContract.OptionType.fromString(instrument.getOptionType()));
    }
    return null;
  }

  /** Formats an OKX expiry timestamp as the {@code yyMMdd} prompt used by XChange futures. */
  private static String formatExpiry(String expiryTime) {
    return Instant.ofEpochMilli(Long.parseLong(expiryTime))
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyMMdd"));
  }

  /** Maps an OKX underlying (e.g. {@code BTC-USDT}) to the corresponding currency pair. */
  private static CurrencyPair underlyingToCurrencyPair(String underlying) {
    if (underlying == null) {
      return null;
    }
    String[] tokens = underlying.split("-");
    if (tokens.length != 2) {
      return null;
    }
    return new CurrencyPair(tokens[0], tokens[1]);
  }

  /** True for instruments whose size is expressed in contracts (SWAP, FUTURES and OPTION). */
  private static boolean isContractDerivative(OkxInstrument instrument) {
    String instType = instrument.getInstrumentType();
    return OkxInstType.SWAP.name().equals(instType)
        || OkxInstType.FUTURES.name().equals(instType)
        || OkxInstType.OPTION.name().equals(instType);
  }

  public static Trades adaptTrades(
      List<OkxTrade> okxTrades, Instrument instrument, ExchangeMetaData exchangeMetaData) {
    List<Trade> trades = new ArrayList<>();

    okxTrades.forEach(
        okxTrade ->
            trades.add(
                Trade.builder()
                    .id(okxTrade.getTradeId())
                    .instrument(instrument)
                    .originalAmount(
                        convertContractSizeToVolume(
                            okxTrade.getSz(),
                            instrument,
                            instrumentMetaData(instrument, exchangeMetaData).getContractValue(),
                            okxTrade.getPx()))
                    .price(okxTrade.getPx())
                    .timestamp(okxTrade.getTs())
                    .type(adaptOkxOrderSideToOrderType(okxTrade.getSide()))
                    .build()));

    return new Trades(trades);
  }

  public static OrderType adaptOkxOrderSideToOrderType(String okxOrderSide) {

    return okxOrderSide.equals("buy") ? OrderType.BID : OrderType.ASK;
  }

  private static Currency adaptCurrency(OkxCurrency currency) {
    return new Currency(currency.getCurrency());
  }

  private static int numberOfDecimals(BigDecimal value) {
    double d = value.doubleValue();
    return -(int) Math.round(Math.log10(d));
  }

  public static ExchangeMetaData adaptToExchangeMetaData(
      List<OkxInstrument> instruments, List<OkxCurrency> currs) {

    Map<Instrument, InstrumentMetaData> instrumentMetaData = new HashMap<>();
    Map<Currency, CurrencyMetaData> currencies = new HashMap<>();

    for (OkxInstrument instrument : instruments) {
      if (!"live".equals(instrument.getState())) {
        continue;
      }

      Instrument pair = adaptOkxInstrumentId(instrument.getInstrumentId());
      /*
       The price-dependent inverse-contract conversion (sz*ctVal/price) needs a price, which does
       not exist at metadata time and not for MarketOrders; conversion call sites with a price
       divide by it, while metadata minimums and market-order placement keep the plain ctVal
       multiply/divide. USD- or USDC-counter perpetual swaps are skipped entirely because their
       minimum amounts cannot be expressed in base volume without a price.
      */
      if (pair instanceof FuturesContract
          && ((FuturesContract) pair).isPerpetual()
          && !pair.getCounter().equals(Currency.USDT)) {
        continue;
      }
      // Contract sizes convert by ctVal (SWAP/FUTURES) or ctMult (OPTION); spot/margin leave
      // those fields empty so only build the multiplier for contract derivatives.
      BigDecimal contractValue =
          isContractDerivative(instrument)
              ? (OkxInstType.OPTION.name().equals(instrument.getInstrumentType())
                  ? new BigDecimal(instrument.getContractMultiplier())
                  : new BigDecimal(instrument.getContractValue()))
              : null;
      instrumentMetaData.put(
          pair,
          InstrumentMetaData.builder()
              .minimumAmount(
                  (isContractDerivative(instrument))
                      ? convertContractSizeToVolume(
                          new BigDecimal(instrument.getMinSize()), pair, contractValue, null)
                      : new BigDecimal(instrument.getMinSize()))
              .volumeScale(
                  (isContractDerivative(instrument))
                      ? convertContractSizeToVolume(
                              new BigDecimal(instrument.getMinSize()), pair, contractValue, null)
                          .scale()
                      : Math.max(numberOfDecimals(new BigDecimal(instrument.getMinSize())), 0))
              .amountStepSize(
                  BigDecimal.ONE.movePointLeft(
                      (isContractDerivative(instrument))
                          ? convertContractSizeToVolume(
                                  new BigDecimal(instrument.getLotSize()),
                                  pair,
                                  contractValue,
                                  null)
                              .scale()
                          : Math.max(numberOfDecimals(new BigDecimal(instrument.getLotSize())), 0)))
              .contractValue((isContractDerivative(instrument)) ? contractValue : null)
              .priceScale(numberOfDecimals(new BigDecimal(instrument.getTickSize())))
              .priceStepSize(
                  BigDecimal.ONE.movePointLeft(
                      numberOfDecimals(new BigDecimal(instrument.getTickSize()))))
              .tradingFeeCurrency(Objects.requireNonNull(pair).getCounter())
              .marketOrderEnabled(true)
              .build());
    }

    if (currs != null) {
      currs.forEach(
          currency ->
              currencies.put(
                  adaptCurrency(currency),
                  new CurrencyMetaData(
                      null,
                      new BigDecimal(currency.getMaxFee()),
                      new BigDecimal(currency.getMinWd()),
                      currency.isCanWd() && currency.isCanDep()
                          ? WalletHealth.ONLINE
                          : WalletHealth.OFFLINE)));
    }

    return new ExchangeMetaData(instrumentMetaData, currencies, null, null, true);
  }

  public static Wallet adaptOkxBalances(List<OkxWalletBalance> okxWalletBalanceList) {
    List<Balance> balances = new ArrayList<>();
    if (!okxWalletBalanceList.isEmpty()) {
      OkxWalletBalance okxWalletBalance = okxWalletBalanceList.get(0);
      balances =
          Arrays.stream(okxWalletBalance.getDetails())
              .map(
                  detail ->
                      new Balance.Builder()
                          .currency(new Currency(detail.getCurrency()))
                          .total(new BigDecimal(detail.getCashBalance()))
                          .available(checkForEmpty(detail.getAvailableBalance()))
                          .timestamp(new Date())
                          .build())
              .collect(Collectors.toList());
    }

    return Wallet.Builder.from(balances)
        .id(TRADING_WALLET_ID)
        .features(new HashSet<>(Collections.singletonList(WalletFeature.TRADING)))
        .build();
  }

  public static Wallet adaptOkxAssetBalances(List<OkxAssetBalance> okxAssetBalanceList) {
    List<Balance> balances;
    balances =
        okxAssetBalanceList.stream()
            .map(
                detail ->
                    new Balance.Builder()
                        .currency(new Currency(detail.getCurrency()))
                        .total(new BigDecimal(detail.getBalance()))
                        .available(checkForEmpty(detail.getAvailableBalance()))
                        .timestamp(new Date())
                        .build())
            .collect(Collectors.toList());

    return Wallet.Builder.from(balances)
        .id(FOUNDING_WALLET_ID)
        .features(new HashSet<>(Collections.singletonList(WalletFeature.FUNDING)))
        .build();
  }

  private static BigDecimal checkForEmpty(String value) {
    return StringUtils.isEmpty(value) ? null : new BigDecimal(value);
  }

  public static CandleStickData adaptCandleStickData(
      List<OkxCandleStick> okxCandleStickList, Instrument instrument) {
    CandleStickData candleStickData = null;
    if (!okxCandleStickList.isEmpty()) {
      List<CandleStick> candleStickList = new ArrayList<>();
      for (OkxCandleStick okxCandleStick : okxCandleStickList) {
        BigDecimal volume =
            instrument instanceof CurrencyPair
                ? new BigDecimal(okxCandleStick.getVolume())
                : new BigDecimal(okxCandleStick.getVolumeCcy());
        BigDecimal quotaVolume = new BigDecimal(okxCandleStick.getVolCcyQuote());
        candleStickList.add(
            new CandleStick.Builder()
                .timestamp(Instant.ofEpochMilli(okxCandleStick.getTimestamp()))
                .open(new BigDecimal(okxCandleStick.getOpenPrice()))
                .high(new BigDecimal(okxCandleStick.getHighPrice()))
                .low(new BigDecimal(okxCandleStick.getLowPrice()))
                .close(new BigDecimal(okxCandleStick.getClosePrice()))
                .volume(volume)
                .quotaVolume(quotaVolume)
                .completed(!okxCandleStick.getConfirm().equals("0"))
                .build());
      }
      candleStickList.sort(Comparator.comparing(CandleStick::getTimestamp));
      candleStickData = new CandleStickData(instrument, candleStickList);
    }
    return candleStickData;
  }

  public static OpenPositions adaptOpenPositions(
      List<OkxPosition> positions, ExchangeMetaData exchangeMetaData) {
    List<OpenPosition> openPositions = new ArrayList<>();

    positions.forEach(
        okxPosition ->
            openPositions.add(
                OpenPosition.builder()
                    .instrument(adaptOkxInstrumentId(okxPosition.getInstrumentId()))
                    .liquidationPrice(okxPosition.getLiquidationPrice())
                    .price(okxPosition.getAverageOpenPrice())
                    .type(adaptOpenPositionType(okxPosition))
                    .size(
                        convertContractSizeToVolume(
                            okxPosition.getPosition().abs(),
                            adaptOkxInstrumentId(okxPosition.getInstrumentId()),
                            exchangeMetaData
                                .getInstruments()
                                .get(adaptOkxInstrumentId(okxPosition.getInstrumentId()))
                                .getContractValue(),
                            okxPosition.getAverageOpenPrice()))
                    .unRealisedPnl(okxPosition.getUnrealizedPnL())
                    .build()));
    return new OpenPositions(openPositions);
  }

  public static Type adaptOpenPositionType(OkxPosition okxPosition) {
    switch (okxPosition.getPositionSide()) {
      case "long":
        return Type.LONG;
      case "short":
        return Type.SHORT;
      case "net":
        return (okxPosition.getPosition().compareTo(BigDecimal.ZERO) >= 0) ? Type.LONG : Type.SHORT;
      default:
        throw new UnsupportedOperationException();
    }
  }

  public static FundingRate adaptFundingRate(List<OkxFundingRate> okxFundingRate) {
    int interval =
        ((int)
                (okxFundingRate.get(0).getNextFundingTime().getTime()
                    - okxFundingRate.get(0).getFundingTime().getTime())
            / 3600000);
    BigDecimal fundingRate = okxFundingRate.get(0).getFundingRate();
    FundingRateInterval rateInterval = FundingRateInterval.H8;
    BigDecimal fundingRate1h = BigDecimal.ZERO;
    switch (interval) {
      case 1:
        {
          rateInterval = FundingRateInterval.H1;
          fundingRate1h = fundingRate;
          break;
        }
      case 2:
        {
          rateInterval = FundingRateInterval.H2;
          fundingRate1h =
              fundingRate.divide(
                  BigDecimal.valueOf(2), fundingRate.scale(), RoundingMode.HALF_EVEN);
          break;
        }
      case 4:
        {
          rateInterval = FundingRateInterval.H4;
          fundingRate1h =
              fundingRate.divide(
                  BigDecimal.valueOf(4), fundingRate.scale(), RoundingMode.HALF_EVEN);
          break;
        }
      case 6:
        {
          rateInterval = FundingRateInterval.H6;
          fundingRate1h =
              fundingRate.divide(
                  BigDecimal.valueOf(6), fundingRate.scale(), RoundingMode.HALF_EVEN);
          break;
        }
      case 8:
        {
          fundingRate1h =
              fundingRate.divide(
                  BigDecimal.valueOf(8), fundingRate.scale(), RoundingMode.HALF_EVEN);
          break;
        }
    }
    return new FundingRate.Builder()
        .instrument(adaptOkxInstrumentId(okxFundingRate.get(0).getInstId()))
        .fundingRate(fundingRate)
        .fundingRate1h(fundingRate1h)
        .fundingRateDate(okxFundingRate.get(0).getFundingTime())
        .fundingRateInterval(rateInterval)
        .build();
  }

  public static Wallet adaptOkxAccountPositionRisk(
      List<OkxAccountPositionRisk> accountPositionRiskData) {
    BigDecimal totalPositionValueInUsd = BigDecimal.ZERO;

    for (PositionData positionData : accountPositionRiskData.get(0).getPositionData()) {
      totalPositionValueInUsd = totalPositionValueInUsd.add(positionData.getNotionalUsdValue());
    }

    return new Wallet.Builder()
        .balances(
            Collections.singletonList(
                new Balance.Builder()
                    .currency(Currency.USD)
                    .total(accountPositionRiskData.get(0).getAdjustEquity())
                    .build()))
        .id(FUTURES_WALLET_ID)
        .currentLeverage(
            (totalPositionValueInUsd.compareTo(BigDecimal.ZERO) != 0)
                ? totalPositionValueInUsd.divide(
                    accountPositionRiskData.get(0).getAdjustEquity(), 3, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO)
        .features(new HashSet<>(Collections.singletonList(WalletFeature.FUTURES_TRADING)))
        .build();
  }

  public static Fee adaptTradingFee(
      OkxTradeFee okxTradeFee, OkxInstType okxInstType, Instrument instrument) {
    switch (okxInstType) {
      case SPOT:
        return adaptTradingFeeSPOT(okxTradeFee, instrument);
      case SWAP:
        return adaptTradingFeeSWAP(okxTradeFee, instrument);
    }
    return null;
  }

  private static Fee adaptTradingFeeSWAP(OkxTradeFee okxTradeFee, Instrument instrument) {
    if (instrument.getCounter().toString().equals("USDT")) {
      return new Fee(
          new BigDecimal(okxTradeFee.getMakerU()).negate(),
          new BigDecimal(okxTradeFee.getTakerU()).negate());
    } else {
      if (instrument.getCounter().toString().equals("USDC")) {
        return new Fee(
            new BigDecimal(okxTradeFee.getMakerUSDC()).negate(),
            new BigDecimal(okxTradeFee.getTakerUSDC()).negate());
      } else
        return new Fee(
            new BigDecimal(okxTradeFee.getMaker()).negate(),
            new BigDecimal(okxTradeFee.getTaker()).negate());
    }
  }

  private static Fee adaptTradingFeeSPOT(OkxTradeFee okxTradeFee, Instrument instrument) {
    // https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-fee-rates
    if (instrument.getCounter().toString().equals("USDT")) {
      return new Fee(
          new BigDecimal(okxTradeFee.getMaker()).negate(),
          new BigDecimal(okxTradeFee.getTaker()).negate());
    } else {
      Fee tempFee = isContainsFiat(okxTradeFee.getFiatList(), instrument);
      if (tempFee != null) {
        return tempFee;
      } else {
        // represent the stablecoin besides USDT and USDC
        return new Fee(
            new BigDecimal(okxTradeFee.getMakerUSDC()).negate(),
            new BigDecimal(okxTradeFee.getTakerUSDC()).negate());
      }
    }
  }

  private static Fee isContainsFiat(List<FiatList> fiatList, Instrument instrument) {
    for (FiatList fiat : fiatList) {
      if (fiat.getCcy().equals(instrument.getCounter().toString())) {
        return new Fee(
            new BigDecimal(fiat.getMaker()).negate(), new BigDecimal(fiat.getTaker()).negate());
      }
    }
    return null;
  }

  public static OkxCandleStickInterval adaptCandleStickInterval(CandleStickInterval interval) {
    switch (interval) {
      case s1:
        return OkxCandleStickInterval.candle1s;
      case m1:
        return OkxCandleStickInterval.candle1m;
      case m3:
        return OkxCandleStickInterval.candle3m;
      case m5:
        return OkxCandleStickInterval.candle5m;
      case m15:
        return OkxCandleStickInterval.candle15m;
      case m30:
        return OkxCandleStickInterval.candle30m;
      case h1:
        return OkxCandleStickInterval.candle1H;
      case h2:
        return OkxCandleStickInterval.candle2H;
      case h4:
        return OkxCandleStickInterval.candle4H;
      case h6:
        return OkxCandleStickInterval.candle6H;
      case h12:
        return OkxCandleStickInterval.candle12H;
      case d1:
        return OkxCandleStickInterval.candle1D;
      case d2:
        return OkxCandleStickInterval.candle2D;
      case d3:
        return OkxCandleStickInterval.candle3D;
      case d5:
        return OkxCandleStickInterval.candle5D;
      case w1:
        return OkxCandleStickInterval.candle1W;
      case M1:
        return OkxCandleStickInterval.candle1M;
      case M3:
        return OkxCandleStickInterval.candle3M;
      default:
        throw new IllegalArgumentException("Unsupported interval: " + interval);
    }
  }

  public static List<OrderBookUpdate> adaptOrderBookUpdates(
      Instrument instrument,
      List<OkxPublicOrder> asks,
      List<OkxPublicOrder> bids,
      BigDecimal contractValue,
      Date date) {
    List<OrderBookUpdate> orderBookUpdates = new ArrayList<>();
    for (OkxPublicOrder ask : asks) {
      BigDecimal volume =
          convertContractSizeToVolume(ask.getVolume(), instrument, contractValue, ask.getPrice());
      OrderBookUpdate o =
          new OrderBookUpdate(OrderType.ASK, volume, instrument, ask.getPrice(), date, volume);
      orderBookUpdates.add(o);
    }
    for (OkxPublicOrder bid : bids) {
      BigDecimal volume =
          convertContractSizeToVolume(bid.getVolume(), instrument, contractValue, bid.getPrice());
      OrderBookUpdate o =
          new OrderBookUpdate(OrderType.BID, volume, instrument, bid.getPrice(), date, volume);
      orderBookUpdates.add(o);
    }
    return orderBookUpdates;
  }

  public static String instrumentToInstrumentCode(Instrument instrument) {
    return instrumentCode(instrument).toString();
  }
}
