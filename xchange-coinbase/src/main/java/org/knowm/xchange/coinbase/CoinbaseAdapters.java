package org.knowm.xchange.coinbase;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAmount;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummary;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPosition;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderConfiguration;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseStopPriceDirection;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPosition;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBookEntry;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseMarketTrade;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandle;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseUserTrade;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Ticker.Builder;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.instrument.Instrument;

/** jamespedwards42 */
public final class CoinbaseAdapters {

  private CoinbaseAdapters() {}

  /** Extract newly created order id from Coinbase createOrder response. */
  public static String adaptCreatedOrderId(CoinbaseCreateOrderResponse response) {
    return response == null ? null : response.getOrderId();
  }

  public static OrderBook adaptOrderBook(CoinbasePriceBook priceBook) {
    requireAdaptableProduct(priceBook.getProductId(), "order book");
    Instrument instrument = CoinbaseAdapters.adaptInstrument(priceBook.getProductId());

    List<LimitOrder> asks =
        priceBook.getAsks().stream()
            .map(
                priceBookEntry ->
                    CoinbaseAdapters.adaptOrderBookEntry(priceBookEntry, OrderType.ASK, instrument))
            .collect(Collectors.toList());

    List<LimitOrder> bids =
        priceBook.getBids().stream()
            .map(
                priceBookEntry ->
                    CoinbaseAdapters.adaptOrderBookEntry(priceBookEntry, OrderType.BID, instrument))
            .collect(Collectors.toList());

    return new OrderBook(
        Date.from(DateTimeFormatter.ISO_INSTANT.parse(priceBook.getTime(), Instant::from)),
        asks,
        bids);
  }

  public static LimitOrder adaptOrderBookEntry(
      CoinbasePriceBookEntry priceBookEntry, Order.OrderType orderType, Instrument instrument) {
    return new LimitOrder(
        orderType, priceBookEntry.getSize(), instrument, null, null, priceBookEntry.getPrice());
  }

  public static Trade adaptTrade(CoinbaseMarketTrade marketTrade) {
    requireAdaptableProduct(marketTrade.getProductId(), "market trade");
    Instrument instrument = adaptInstrument(marketTrade.getProductId());
    return UserTrade.builder()
        .id(marketTrade.getTradeId())
        .instrument(instrument)
        .price(marketTrade.getPrice())
        .originalAmount(marketTrade.getSize())
        .timestamp(
            Date.from(DateTimeFormatter.ISO_INSTANT.parse(marketTrade.getTime(), Instant::from)))
        .type(adaptOrderType(marketTrade.getSide()))
        .build();
  }

  /**
   * Adapts a Coinbase Advanced Trade fill to a generic XChange user trade.
   *
   * <p>Coinbase may report the fill size in quote currency. XChange's {@code originalAmount} is
   * base/instrument quantity, so quote-sized fills are converted using the execution price. A
   * non-positive or missing price is not authoritative and therefore fails explicitly instead of
   * publishing a mislabeled amount.
   *
   * @param fill the Coinbase fill
   * @return a lossless generic trade for a representable product
   * @throws ExchangeException when the product is opaque, the side/amount is unusable, or a
   *     quote-sized fill has no positive execution price
   */
  public static UserTrade adaptFill(CoinbaseFill fill) {
    return adaptFill(fill, null);
  }

  /**
   * Adapts a fill, retaining a catalog-resolved instrument when the exchange's native product id
   * cannot round-trip through the generic parser.
   *
   * @param fill the Coinbase fill
   * @param instrument catalog-resolved instrument, or {@code null} to parse the native product id
   * @return a lossless generic trade
   */
  public static UserTrade adaptFill(CoinbaseFill fill, Instrument instrument) {
    Objects.requireNonNull(fill, "Cannot adapt a null fill");
    if (fill.getEntryId() == null
        || fill.getEntryId().isBlank()
        || fill.getTradeId() == null
        || fill.getTradeId().isBlank()
        || fill.getOrderId() == null
        || fill.getOrderId().isBlank()) {
      throw new ExchangeException(
          "Cannot adapt Coinbase fill: missing entry, trade, or order identity");
    }
    if (fill.getProductId() == null
        || fill.getProductId().isBlank()
        || fill.getSide() == null
        || fill.getSize() == null
        || fill.getPrice() == null) {
      throw new ExchangeException(
          "Cannot adapt Coinbase fill "
              + fill.getEntryId()
              + ": missing product, side, quantity, or price");
    }
    if (fill.getSize().signum() <= 0 || fill.getPrice().signum() <= 0) {
      throw new ExchangeException(
          "Cannot adapt Coinbase fill "
              + fill.getEntryId()
              + ": quantity and execution price must be positive");
    }
    requireAdaptableProduct(fill.getProductId(), "fill");
    OrderType orderType = adaptOrderType(fill.getSide());
    if (orderType == null) {
      throw new ExchangeException(
          "Cannot adapt Coinbase fill "
              + fill.getEntryId()
              + ": unsupported side "
              + fill.getSide()
              + " for product "
              + fill.getProductId());
    }
    BigDecimal amount = fill.getSize();
    if (fill.isSizeInQuote()) {
      amount = amount.divide(fill.getPrice(), MathContext.DECIMAL128);
    }
    return CoinbaseUserTrade.builder()
        .entryId(fill.getEntryId())
        .id(fill.getTradeId())
        .orderId(fill.getOrderId())
        .instrument(instrument == null ? adaptInstrument(fill.getProductId()) : instrument)
        .price(fill.getPrice())
        .originalAmount(amount)
        .timestamp(fill.getTradeTime())
        .type(orderType)
        .feeAmount(fill.getCommission())
        .feeCurrency(fill.getFeeCurrency())
        .build();
  }

  public static OrderType adaptOrderType(String side) {
    switch (side) {
      case "SELL":
        return OrderType.ASK;
      case "BUY":
        return OrderType.BID;
    }
    return null;
  }

  /**
   * Adapt a Coinbase Advanced Trade order detail to an XChange order.
   *
   * <p>Opaque CDE product identifiers cannot be represented by XChange's generic instrument model
   * and therefore fail explicitly. Quote-sized orders are only representable after Coinbase reports
   * a positive authoritative {@code filled_size}; an unfilled quote order is never labeled as a
   * base quantity. Stop-limit configurations preserve both trigger direction and stop price.
   */
  public static Order adaptOrder(CoinbaseOrderDetail detail) {
    if (detail == null) return null;
    requireAdaptableProduct(detail.getProductId(), "order");
    Order.OrderStatus status = adaptOrderStatus(detail.getStatus());
    Order.OrderType orderType = adaptOrderType(detail.getSide());
    Instrument instrument = adaptInstrument(detail.getProductId());
    CoinbaseOrderConfiguration configuration = detail.getOrderConfiguration();
    String unsupportedConfiguration = unsupportedConfiguration(configuration);
    if (unsupportedConfiguration != null) {
      throw new NotAvailableFromExchangeException(
          "Cannot adapt Coinbase "
              + unsupportedConfiguration
              + " order "
              + detail.getOrderId()
              + ": XChange cannot preserve its execution semantics");
    }
    BigDecimal size = configuredSize(detail);
    BigDecimal price = configuredPrice(detail);
    if (isQuoteSized(detail)) {
      if (status != Order.OrderStatus.FILLED) {
        throw new NotAvailableFromExchangeException(
            "Cannot adapt unfilled quote-sized Coinbase order "
                + detail.getOrderId()
                + " for product "
                + detail.getProductId()
                + ": XChange originalAmount requires base quantity");
      }
      BigDecimal filledSize = detail.getFilledSize();
      if (filledSize == null || filledSize.signum() <= 0) {
        throw new ExchangeException(
            "Cannot adapt filled quote-sized Coinbase order "
                + detail.getOrderId()
                + " for product "
                + detail.getProductId()
                + ": missing positive authoritative filled_size");
      }
      size = filledSize;
    }
    boolean stopLimit = false;
    BigDecimal stopPrice = null;
    CoinbaseStopPriceDirection stopDirection = null;
    if (configuration != null && configuration.getStopLimitStopLimitGtc() != null) {
      stopLimit = true;
      stopPrice = configuration.getStopLimitStopLimitGtc().getStopPrice();
      stopDirection = configuration.getStopLimitStopLimitGtc().getStopDirection();
    } else if (configuration != null && configuration.getStopLimitStopLimitGtd() != null) {
      stopLimit = true;
      stopPrice = configuration.getStopLimitStopLimitGtd().getStopPrice();
      stopDirection = configuration.getStopLimitStopLimitGtd().getStopDirection();
    }
    if (orderType == null || instrument == null || size == null) {
      return null;
    }
    Order order;
    if (stopLimit) {
      if (stopPrice == null
          || stopPrice.signum() <= 0
          || price == null
          || price.signum() <= 0
          || stopDirection == null) {
        throw new ExchangeException(
            "Cannot adapt Coinbase stop-limit order "
                + detail.getOrderId()
                + ": missing positive stop/limit price or trigger direction");
      }
      StopOrder.Intention intention;
      switch (stopDirection) {
        case STOP_DIRECTION_STOP_UP:
          intention =
              orderType == OrderType.BID
                  ? StopOrder.Intention.STOP_LOSS
                  : StopOrder.Intention.TAKE_PROFIT;
          break;
        case STOP_DIRECTION_STOP_DOWN:
          intention =
              orderType == OrderType.BID
                  ? StopOrder.Intention.TAKE_PROFIT
                  : StopOrder.Intention.STOP_LOSS;
          break;
        default:
          throw new ExchangeException(
              "Cannot adapt Coinbase stop-limit order "
                  + detail.getOrderId()
                  + ": unsupported trigger direction "
                  + stopDirection);
      }
      order =
          new StopOrder(
              orderType,
              size,
              instrument,
              detail.getOrderId(),
              detail.getCreatedTime(),
              stopPrice,
              price,
              detail.getAverageFilledPrice(),
              detail.getFilledSize(),
              detail.getTotalFees(),
              status,
              null,
              intention,
              null);
    } else if (price != null) {
      order =
          new LimitOrder(
              orderType,
              size,
              instrument,
              detail.getOrderId(),
              detail.getCreatedTime(),
              price,
              detail.getAverageFilledPrice(),
              detail.getFilledSize(),
              detail.getTotalFees(),
              status);
    } else {
      order =
          new org.knowm.xchange.dto.trade.MarketOrder(
              orderType,
              size,
              instrument,
              detail.getOrderId(),
              detail.getCreatedTime(),
              detail.getAverageFilledPrice(),
              detail.getFilledSize(),
              detail.getTotalFees(),
              status);
    }
    order.setLeverage(detail.getLeverage());
    return order;
  }

  private static String unsupportedConfiguration(CoinbaseOrderConfiguration configuration) {
    if (configuration == null) return null;
    if (configuration.getScaledLimitGtc() != null) return "scaled_limit_gtc";
    if (configuration.getTwapLimitGtd() != null) return "twap_limit_gtd";
    if (configuration.getTriggerBracketGtc() != null) return "trigger_bracket_gtc";
    if (configuration.getTriggerBracketGtd() != null) return "trigger_bracket_gtd";
    return null;
  }

  private static boolean isQuoteSized(CoinbaseOrderDetail detail) {
    return detail.isSizeInQuote() || configuredQuoteSize(detail) != null;
  }

  private static BigDecimal configuredQuoteSize(CoinbaseOrderDetail detail) {
    CoinbaseOrderConfiguration config = detail.getOrderConfiguration();
    if (config == null) return detail.isSizeInQuote() ? detail.getSize() : null;
    if (config.getMarketMarketIoc() != null && config.getMarketMarketIoc().getQuoteSize() != null) {
      return config.getMarketMarketIoc().getQuoteSize();
    }
    if (config.getMarketMarketFok() != null && config.getMarketMarketFok().getQuoteSize() != null) {
      return config.getMarketMarketFok().getQuoteSize();
    }
    if (config.getSorLimitIoc() != null && config.getSorLimitIoc().getQuoteSize() != null) {
      return config.getSorLimitIoc().getQuoteSize();
    }
    if (config.getLimitLimitGtc() != null && config.getLimitLimitGtc().getQuoteSize() != null) {
      return config.getLimitLimitGtc().getQuoteSize();
    }
    if (config.getLimitLimitGtd() != null && config.getLimitLimitGtd().getQuoteSize() != null) {
      return config.getLimitLimitGtd().getQuoteSize();
    }
    if (config.getLimitLimitFok() != null && config.getLimitLimitFok().getQuoteSize() != null) {
      return config.getLimitLimitFok().getQuoteSize();
    }
    if (config.getTwapLimitGtd() != null && config.getTwapLimitGtd().getQuoteSize() != null) {
      return config.getTwapLimitGtd().getQuoteSize();
    }
    return null;
  }

  private static BigDecimal configuredSize(CoinbaseOrderDetail detail) {
    CoinbaseOrderConfiguration config = detail.getOrderConfiguration();
    if (config == null) return detail.isSizeInQuote() ? null : detail.getSize();
    if (config.getMarketMarketIoc() != null) {
      return config.getMarketMarketIoc().getBaseSize();
    }
    if (config.getMarketMarketFok() != null) {
      return config.getMarketMarketFok().getBaseSize();
    }
    if (config.getSorLimitIoc() != null) {
      return config.getSorLimitIoc().getBaseSize();
    }
    if (config.getLimitLimitGtc() != null) {
      return config.getLimitLimitGtc().getBaseSize();
    }
    if (config.getLimitLimitGtd() != null) {
      return config.getLimitLimitGtd().getBaseSize();
    }
    if (config.getLimitLimitFok() != null) {
      return config.getLimitLimitFok().getBaseSize();
    }
    if (config.getStopLimitStopLimitGtc() != null) {
      return config.getStopLimitStopLimitGtc().getBaseSize();
    }
    if (config.getStopLimitStopLimitGtd() != null) {
      return config.getStopLimitStopLimitGtd().getBaseSize();
    }
    if (config.getTwapLimitGtd() != null) {
      return config.getTwapLimitGtd().getBaseSize();
    }
    if (config.getTriggerBracketGtc() != null) {
      return config.getTriggerBracketGtc().getBaseSize();
    }
    if (config.getTriggerBracketGtd() != null) {
      return config.getTriggerBracketGtd().getBaseSize();
    }
    return null;
  }

  private static BigDecimal configuredPrice(CoinbaseOrderDetail detail) {
    CoinbaseOrderConfiguration config = detail.getOrderConfiguration();
    if (config == null) return detail.getPrice();
    if (config.getSorLimitIoc() != null) return config.getSorLimitIoc().getLimitPrice();
    if (config.getLimitLimitGtc() != null) return config.getLimitLimitGtc().getLimitPrice();
    if (config.getLimitLimitGtd() != null) return config.getLimitLimitGtd().getLimitPrice();
    if (config.getLimitLimitFok() != null) return config.getLimitLimitFok().getLimitPrice();
    if (config.getStopLimitStopLimitGtc() != null) {
      return config.getStopLimitStopLimitGtc().getLimitPrice();
    }
    if (config.getStopLimitStopLimitGtd() != null) {
      return config.getStopLimitStopLimitGtd().getLimitPrice();
    }
    if (config.getTwapLimitGtd() != null) return config.getTwapLimitGtd().getLimitPrice();
    if (config.getTriggerBracketGtc() != null) {
      return config.getTriggerBracketGtc().getLimitPrice();
    }
    if (config.getTriggerBracketGtd() != null) {
      return config.getTriggerBracketGtd().getLimitPrice();
    }
    return null;
  }

  private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
    return first == null ? second : first;
  }

  private static Order.OrderStatus adaptOrderStatus(String status) {
    if (status == null) return Order.OrderStatus.UNKNOWN;
    switch (status.toUpperCase()) {
      case "OPEN":
      case "PENDING":
      case "NEW":
      case "QUEUED":
      case "CANCEL_QUEUED":
      case "EDIT_QUEUED":
        return Order.OrderStatus.OPEN;
      case "FILLED":
      case "DONE":
        return Order.OrderStatus.FILLED;
      case "CANCELLED":
      case "CANCELED":
        return Order.OrderStatus.CANCELED;
      case "EXPIRED":
        return Order.OrderStatus.EXPIRED;
      case "REJECTED":
      case "FAILED":
        return Order.OrderStatus.REJECTED;
      case "PARTIALLY_FILLED":
        return Order.OrderStatus.PARTIALLY_FILLED;
      default:
        return Order.OrderStatus.UNKNOWN;
    }
  }

  /**
   * Adapt Coinbase order details into XChange open orders, filtering to orders in an open state.
   *
   * <p>Every response detail is validated before filtering so opaque CDE state cannot disappear
   * merely because its status is not open.
   */
  public static OpenOrders adaptOpenOrders(List<CoinbaseOrderDetail> orders) {
    List<LimitOrder> visible = new ArrayList<>();
    List<Order> hidden = new ArrayList<>();
    for (CoinbaseOrderDetail detail : orders) {
      if (detail == null) {
        throw new NotAvailableFromExchangeException("Cannot adapt null Coinbase open-order detail");
      }
      requireAdaptableProduct(detail.getProductId(), "open order");
      Order.OrderStatus status = adaptOrderStatus(detail.getStatus());
      if (status == Order.OrderStatus.UNKNOWN) {
        throw new NotAvailableFromExchangeException(
            "Cannot represent Coinbase open-order status "
                + detail.getStatus()
                + " for order "
                + detail.getOrderId());
      }
      if (status.isOpen()) {
        Order order = adaptOrder(detail);
        if (order == null) {
          throw new NotAvailableFromExchangeException(
              "Cannot represent open Coinbase order " + detail.getOrderId());
        }
        if (order instanceof LimitOrder) {
          visible.add((LimitOrder) order);
        } else {
          hidden.add(order);
        }
      }
    }
    return new OpenOrders(visible, hidden);
  }

  /**
   * Adapt a Coinbase list-orders response into generic open orders.
   *
   * @param response Coinbase list-orders response
   * @return adapted open orders
   */
  public static OpenOrders adaptOpenOrders(CoinbaseListOrdersResponse response) {
    return adaptOpenOrders(response.getOrders());
  }

  /**
   * Adapts the given financial instrument to a product ID string suitable for Coinbase API by
   * replacing any forward slashes in its string representation with hyphens.
   *
   * @param instrument the financial instrument to adapt
   * @return a product ID string with forward slashes replaced by hyphens
   */
  public static String adaptProductId(Instrument instrument) {
    Objects.requireNonNull(instrument, "Cannot format productId from a null instrument");
    return instrument.toString().replace("/", "-");
  }

  /**
   * Adapts a product ID string into a financial instrument (e.g., CurrencyPair) by splitting the
   * string on hyphens. For spot products, expects the product ID to represent a currency pair in
   * the format "base-counter". For derivatives whose first two components are a canonical price
   * pair, maps the remaining prompt suffix to {@link FuturesContract}. Opaque CDE identifiers end
   * in {@code -CDE} but do not encode their price pair; they fail closed rather than fabricating an
   * instrument from product-code segments.
   *
   * @param productId the product ID string to adapt, must not be null
   * @return the corresponding instrument, or {@code null} when the identifier is invalid or does
   *     not carry enough metadata to derive one
   */
  public static Instrument adaptInstrument(String productId) {
    Objects.requireNonNull(productId, "Cannot create instrument from a null productId");

    String[] tokens = productId.split("-");
    if (tokens.length == 2) {
      return new CurrencyPair(tokens[0], tokens[1]);
    }
    if (tokens.length >= 3 && !"CDE".equals(tokens[tokens.length - 1])) {
      String prompt = String.join("-", Arrays.copyOfRange(tokens, 2, tokens.length));
      return new FuturesContract(new CurrencyPair(tokens[0], tokens[1]), prompt);
    }

    return null;
  }

  private static void requireAdaptableProduct(String productId, String context) {
    if (productId != null && isOpaqueCdeProductId(productId)) {
      throw new NotAvailableFromExchangeException(
          "Coinbase "
              + context
              + " for opaque CDE product "
              + productId
              + " cannot be represented by the generic XChange model; use the raw CDE API");
    }
  }

  private static boolean isOpaqueCdeProductId(String productId) {
    return productId.toUpperCase(Locale.ROOT).endsWith("-CDE");
  }

  public static OpenPositions adaptFuturesOpenPositions(List<CoinbaseFuturesPosition> positions) {
    if (positions == null) {
      return new OpenPositions(Collections.emptyList());
    }
    for (CoinbaseFuturesPosition position : positions) {
      if (position != null) {
        requireAdaptableProduct(position.getProductId(), "futures position");
      }
    }
    List<OpenPosition> openPositions =
        positions.stream()
            .map(CoinbaseAdapters::adaptFuturesOpenPosition)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    return new OpenPositions(openPositions);
  }

  public static OpenPositions adaptPerpetualsOpenPositions(
      List<CoinbasePerpetualsPosition> positions) {
    if (positions == null) {
      return new OpenPositions(Collections.emptyList());
    }
    for (CoinbasePerpetualsPosition position : positions) {
      if (position != null) {
        requireAdaptableProduct(position.getSymbol(), "perpetual position");
      }
    }
    List<OpenPosition> openPositions =
        positions.stream()
            .map(CoinbaseAdapters::adaptPerpetualsOpenPosition)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    return new OpenPositions(openPositions);
  }

  /** Adapt a futures balance summary response to a futures wallet. */
  public static Wallet adaptFuturesWallet(CoinbaseFuturesBalanceSummaryResponse response) {
    if (response == null || response.getBalanceSummary() == null) {
      return null;
    }
    CoinbaseFuturesBalanceSummary summary = response.getBalanceSummary();
    CoinbaseAmount total = summary.getTotalUsdBalance();
    CoinbaseAmount available = summary.getAvailableMargin();
    if (total == null
        || available == null
        || total.getCurrency() == null
        || !total.getCurrency().equals(available.getCurrency())) {
      return null;
    }
    Balance balance =
        buildBalance(
            Currency.getInstance(total.getCurrency()), total.getValue(), available.getValue());
    if (balance == null) {
      return null;
    }
    return new Wallet.Builder()
        .balances(Collections.singletonList(balance))
        .id("futures")
        .name("futures")
        .features(Collections.singleton(Wallet.WalletFeature.FUTURES_TRADING))
        .build();
  }

  /** Adapt a perpetuals balances response to a futures wallet. */
  public static Wallet adaptPerpetualsWallet(CoinbasePerpetualsBalancesResponse response) {
    if (response == null || response.getBalances() == null) {
      return null;
    }
    CoinbasePerpetualsBalancesResponse.CoinbasePerpetualsBalances balances = response.getBalances();
    String currencyCode = balances.getCollateralCurrency();
    Currency currency = currencyCode == null ? null : Currency.getInstance(currencyCode);
    Balance balance =
        buildBalance(currency, balances.getCollateralValue(), balances.getAvailableCollateral());
    if (balance == null) {
      return null;
    }
    return new Wallet.Builder()
        .balances(Collections.singletonList(balance))
        .id(balances.getPortfolioUuid())
        .name("perpetuals")
        .features(Collections.singleton(Wallet.WalletFeature.FUTURES_TRADING))
        .build();
  }

  private static OpenPosition adaptFuturesOpenPosition(CoinbaseFuturesPosition position) {
    if (position == null) {
      return null;
    }
    Instrument instrument = adaptInstrument(position.getProductId());
    if (instrument == null) {
      return null;
    }
    OpenPosition.Type type = adaptPositionType(position.getSide());
    BigDecimal size = firstNonNull(position.getNumberOfContracts(), position.getAmount());
    BigDecimal price = firstNonNull(position.getAvgEntryPrice(), position.getEntryPrice());
    return OpenPosition.builder()
        .id(position.getProductId())
        .instrument(instrument)
        .type(type)
        .size(size)
        .price(price)
        .unRealisedPnl(position.getUnrealizedPnl())
        .build();
  }

  private static OpenPosition adaptPerpetualsOpenPosition(CoinbasePerpetualsPosition position) {
    if (position == null) {
      return null;
    }
    String instrumentId =
        position.getProductId() != null ? position.getProductId() : position.getSymbol();
    Instrument instrument = instrumentId == null ? null : adaptInstrument(instrumentId);
    OpenPosition.Type type = adaptPositionType(position.getSide());
    BigDecimal size = position.getNetSize();
    if (size != null) {
      size = size.abs();
    }
    BigDecimal entryPrice =
        parseBigDecimal(position.getEntryVwap()) != null
            ? parseBigDecimal(position.getEntryVwap())
            : parseBigDecimal(position.getVwap());
    return OpenPosition.builder()
        .id(instrumentId)
        .instrument(instrument)
        .type(type)
        .size(size)
        .price(entryPrice)
        .unRealisedPnl(position.getUnrealizedPnl())
        .build();
  }

  private static OpenPosition.Type adaptPositionType(String side) {
    if (side == null) {
      return null;
    }
    switch (side.toUpperCase(Locale.ROOT)) {
      case "BUY":
      case "BID":
      case "LONG":
        return OpenPosition.Type.LONG;
      case "SELL":
      case "ASK":
      case "SHORT":
        return OpenPosition.Type.SHORT;
      default:
        return null;
    }
  }

  private static BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Balance buildBalance(Currency currency, BigDecimal total, BigDecimal available) {
    if (currency == null) {
      return null;
    }
    if (total != null && available != null) {
      return new Balance(currency, total, available);
    }
    if (total != null) {
      return new Balance(currency, total);
    }
    if (available != null) {
      return new Balance(currency, available);
    }
    return null;
  }

  public static String adaptProductCandleGranularity(Long candleIntervalSeconds) {
    if (candleIntervalSeconds == null) {
      return null;
    }

    switch (candleIntervalSeconds.intValue()) {
      case 60:
        return "ONE_MINUTE";
      case 300:
        return "FIVE_MINUTE";
      case 900:
        return "FIFTEEN_MINUTE";
      case 1800:
        return "THIRTY_MINUTE";
      case 3600:
        return "ONE_HOUR";
      case 7200:
        return "TWO_HOUR";
      case 21_600:
        return "SIX_HOUR";
      case 86_400:
        return "ONE_DAY";
      default:
        return null;
    }
  }

  public static CandleStick adaptProductCandle(CoinbaseProductCandle productCandle) {
    return new CandleStick.Builder()
        .open(productCandle.getOpen())
        .high(productCandle.getHigh())
        .low(productCandle.getLow())
        .close(productCandle.getClose())
        .volume(productCandle.getVolume())
        .timestamp(Instant.ofEpochSecond(Long.parseLong(productCandle.getStart())))
        .build();
  }

  public static Ticker adaptTicker(
      CoinbaseProductResponse product,
      CoinbaseProductCandlesResponse candle,
      CoinbasePriceBook priceBook) {
    if (priceBook != null) {
      requireAdaptableProduct(priceBook.getProductId(), "ticker");
    } else if (product != null) {
      requireAdaptableProduct(product.getProductId(), "ticker");
    }
    Builder builder = new Ticker.Builder();

    if (product != null) {
      if (product.getPricePercentageChange24H() != null) {
        builder =
            builder.percentageChange(
                product
                    .getPricePercentageChange24H()
                    .round(new MathContext(2, RoundingMode.HALF_EVEN)));
      }
      if (product.getVolume24H() != null) {
        builder = builder.volume(product.getVolume24H());
      }
      if (product.getApproximateQuoteVolume24H() != null) {
        builder = builder.quoteVolume(product.getApproximateQuoteVolume24H());
      }
    }

    if (priceBook != null && !priceBook.getAsks().isEmpty() && !priceBook.getBids().isEmpty()) {
      Instrument instrument = adaptInstrument(priceBook.getProductId());
      if (instrument == null) {
        return null;
      }
      builder =
          builder
              .ask(priceBook.getAsks().get(0).getPrice())
              .askSize(priceBook.getAsks().get(0).getSize())
              .bid(priceBook.getBids().get(0).getPrice())
              .bidSize(priceBook.getBids().get(0).getSize())
              .instrument(instrument)
              .timestamp(
                  Date.from(
                      DateTimeFormatter.ISO_INSTANT.parse(priceBook.getTime(), Instant::from)));
    }

    if (candle != null && !candle.getCandles().isEmpty()) {
      builder =
          builder
              .low(candle.getCandles().get(0).getLow())
              .high(candle.getCandles().get(0).getHigh())
              .open(candle.getCandles().get(0).getOpen())
              .last(candle.getCandles().get(0).getClose());
    }

    return builder.build();
  }
}
