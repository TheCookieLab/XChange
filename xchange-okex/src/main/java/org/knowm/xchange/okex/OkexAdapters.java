package org.knowm.xchange.okex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.OpenPosition.Type;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexAccountPositionRisk;
import org.knowm.xchange.okex.dto.account.OkexAssetBalance;
import org.knowm.xchange.okex.dto.account.OkexPosition;
import org.knowm.xchange.okex.dto.account.OkexTradeFee;
import org.knowm.xchange.okex.dto.account.OkexWalletBalance;
import org.knowm.xchange.okex.dto.marketdata.OkexCandleStick;
import org.knowm.xchange.okex.dto.marketdata.OkexCandleStickInterval;
import org.knowm.xchange.okex.dto.marketdata.OkexCurrency;
import org.knowm.xchange.okex.dto.marketdata.OkexFundingRate;
import org.knowm.xchange.okex.dto.marketdata.OkexInstrument;
import org.knowm.xchange.okex.dto.marketdata.OkexOrderbook;
import org.knowm.xchange.okex.dto.marketdata.OkexPublicOrder;
import org.knowm.xchange.okex.dto.marketdata.OkexTicker;
import org.knowm.xchange.okex.dto.marketdata.OkexTrade;
import org.knowm.xchange.okex.dto.trade.OkexAmendOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;

/**
 * @deprecated use {@link org.knowm.xchange.okx.OkxAdapters} instead.
 */
@Deprecated
public class OkexAdapters {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public static UserTrades adaptUserTrades(
      List<OkexOrderDetails> okexTradeHistory, ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptUserTrades(
        okexTradeHistory.stream().map(OkexOrderDetails::to).collect(Collectors.toList()),
        exchangeMetaData);
  }

  public static LimitOrder adaptOrder(OkexOrderDetails order, ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptOrder(order.to(), exchangeMetaData);
  }

  public static OpenOrders adaptOpenOrders(
      List<OkexOrderDetails> orders, ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptOpenOrders(
        orders.stream().map(OkexOrderDetails::to).collect(Collectors.toList()), exchangeMetaData);
  }

  public static List<Order> adaptOrdersChanges(
      List<OkexOrderDetails> okexOrderDetailsList, ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptOrdersChanges(
        okexOrderDetailsList.stream().map(OkexOrderDetails::to).collect(Collectors.toList()),
        exchangeMetaData);
  }

  public static OkexAmendOrderRequest adaptAmendOrder(
      LimitOrder order, ExchangeMetaData exchangeMetaData) {
    return new OkexAmendOrderRequest(OkxAdapters.adaptAmendOrder(order, exchangeMetaData));
  }

  public static OkexOrderRequest adaptOrder(
      MarketOrder order, ExchangeMetaData exchangeMetaData, String accountLevel) {
    return new OkexOrderRequest(OkxAdapters.adaptOrder(order, exchangeMetaData, accountLevel));
  }

  public static String adaptTradeMode(Instrument instrument, String accountLevel) {
    return OkxAdapters.adaptTradeMode(instrument, accountLevel);
  }

  public static OkexOrderRequest adaptOrder(
      LimitOrder order, ExchangeMetaData exchangeMetaData, String accountLevel) {
    return new OkexOrderRequest(OkxAdapters.adaptOrder(order, exchangeMetaData, accountLevel));
  }

  public static LimitOrder adaptLimitOrder(
      OkexPublicOrder okexPublicOrder,
      Instrument instrument,
      OrderType orderType,
      Date timestamp,
      BigDecimal contractValue) {
    return OkxAdapters.adaptLimitOrder(
        okexPublicOrder.to(), instrument, orderType, timestamp, contractValue);
  }

  public static OrderBook adaptOrderBook(
      List<OkexOrderbook> okexOrderbooks,
      Instrument instrument,
      ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptOrderBook(
        okexOrderbooks.stream().map(OkexOrderbook::to).collect(Collectors.toList()),
        instrument,
        exchangeMetaData);
  }

  public static OrderBook adaptOrderBook(
      OkexResponse<List<OkexOrderbook>> okexOrderbook,
      Instrument instrument,
      ExchangeMetaData exchangeMetaData) {
    OkxResponse<List<org.knowm.xchange.okx.dto.marketdata.OkxOrderbook>> canonical =
        new OkxResponse<>(
            okexOrderbook.getId(),
            okexOrderbook.getCode(),
            okexOrderbook.getMsg(),
            okexOrderbook.getData().stream().map(OkexOrderbook::to).collect(Collectors.toList()));
    return OkxAdapters.adaptOrderBook(canonical, instrument, exchangeMetaData);
  }

  public static LimitOrder adaptOrderbookOrder(
      BigDecimal amount,
      BigDecimal price,
      Instrument instrument,
      OrderType orderType,
      Date timestamp) {
    return OkxAdapters.adaptOrderbookOrder(amount, price, instrument, orderType, timestamp);
  }

  public static Ticker adaptTicker(OkexTicker okexTicker) {
    return OkxAdapters.adaptTicker(MAPPER.convertValue(okexTicker, OkxTicker.class));
  }

  public static Instrument adaptOkexInstrumentId(String instrumentId) {
    return OkxAdapters.adaptOkxInstrumentId(instrumentId);
  }

  public static String adaptInstrument(Instrument instrument) {
    return OkxAdapters.adaptInstrument(instrument);
  }

  public static Trades adaptTrades(
      List<OkexTrade> okexTrades, Instrument instrument, ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptTrades(
        okexTrades.stream().map(OkexTrade::to).collect(Collectors.toList()),
        instrument,
        exchangeMetaData);
  }

  public static OrderType adaptOkexOrderSideToOrderType(String okexOrderSide) {
    return OkxAdapters.adaptOkxOrderSideToOrderType(okexOrderSide);
  }

  public static ExchangeMetaData adaptToExchangeMetaData(
      List<OkexInstrument> instruments, List<OkexCurrency> currs) {
    return OkxAdapters.adaptToExchangeMetaData(
        instruments.stream().map(OkexInstrument::to).collect(Collectors.toList()),
        currs == null ? null : currs.stream().map(OkexCurrency::to).collect(Collectors.toList()));
  }

  public static Wallet adaptOkexBalances(List<OkexWalletBalance> okexWalletBalanceList) {
    return OkxAdapters.adaptOkxBalances(
        okexWalletBalanceList.stream().map(OkexWalletBalance::to).collect(Collectors.toList()));
  }

  public static Wallet adaptOkexAssetBalances(List<OkexAssetBalance> okexAssetBalanceList) {
    return OkxAdapters.adaptOkxAssetBalances(
        okexAssetBalanceList.stream().map(OkexAssetBalance::to).collect(Collectors.toList()));
  }

  public static CandleStickData adaptCandleStickData(
      List<OkexCandleStick> okexCandleStickList, Instrument instrument) {
    return OkxAdapters.adaptCandleStickData(
        okexCandleStickList.stream().map(OkexCandleStick::to).collect(Collectors.toList()),
        instrument);
  }

  public static OpenPositions adaptOpenPositions(
      List<OkexPosition> positions, ExchangeMetaData exchangeMetaData) {
    return OkxAdapters.adaptOpenPositions(
        positions.stream().map(OkexPosition::to).collect(Collectors.toList()), exchangeMetaData);
  }

  public static Type adaptOpenPositionType(OkexPosition okexPosition) {
    return OkxAdapters.adaptOpenPositionType(okexPosition.to());
  }

  public static FundingRate adaptFundingRate(List<OkexFundingRate> okexFundingRate) {
    return OkxAdapters.adaptFundingRate(
        okexFundingRate.stream().map(OkexFundingRate::to).collect(Collectors.toList()));
  }

  public static Wallet adaptOkexAccountPositionRisk(
      List<OkexAccountPositionRisk> okexAccountPositionRiskList) {
    return OkxAdapters.adaptOkxAccountPositionRisk(
        okexAccountPositionRiskList.stream()
            .map(OkexAccountPositionRisk::to)
            .collect(Collectors.toList()));
  }

  public static Fee adaptTradingFee(
      OkexTradeFee okexTradeFee, OkexInstType okexInstType, Instrument instrument) {
    return OkxAdapters.adaptTradingFee(okexTradeFee.to(), okexInstType.to(), instrument);
  }

  public static OkexCandleStickInterval adaptCandleStickInterval(CandleStickInterval interval) {
    return OkexCandleStickInterval.from(OkxAdapters.adaptCandleStickInterval(interval));
  }

  public static List<OrderBookUpdate> adaptOrderBookUpdates(
      Instrument instrument,
      List<OkexPublicOrder> asks,
      List<OkexPublicOrder> bids,
      BigDecimal contractValue,
      Date date) {
    return OkxAdapters.adaptOrderBookUpdates(
        instrument,
        asks.stream().map(OkexPublicOrder::to).collect(Collectors.toList()),
        bids.stream().map(OkexPublicOrder::to).collect(Collectors.toList()),
        contractValue,
        date);
  }

  public static String instrumentToInstrumentCode(Instrument instrument) {
    return OkxAdapters.instrumentToInstrumentCode(instrument);
  }
}
