package org.knowm.xchange.dase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dase.dto.marketdata.DaseOrderBookSnapshot;
import org.knowm.xchange.dase.dto.marketdata.DaseTicker;
import org.knowm.xchange.dase.dto.marketdata.DaseTrade;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.account.FundingRecord;

public final class DaseAdapters {

  private DaseAdapters() {
  }

  public static String toMarketString(CurrencyPair pair) {
    return pair.getBase().getCurrencyCode() + "-" + pair.getCounter().getCurrencyCode();
  }

  public static CurrencyPair toCurrencyPair(String market) {
    if (market == null)
      return null;
    String[] parts = market.trim().split("-");
    if (parts.length != 2)
      return null;
    return new CurrencyPair(parts[0].toUpperCase(), parts[1].toUpperCase());
  }

  public static Ticker adaptTicker(DaseTicker t, CurrencyPair pair) {
    return new Ticker.Builder()
        .instrument(pair)
        .timestamp(new Date(t.getTime()))
        .ask(t.getAsk())
        .bid(t.getBid())
        .last(t.getPrice())
        .volume(t.getVolume())
        .build();
  }

  public static OrderBook adaptOrderBook(DaseOrderBookSnapshot s, CurrencyPair pair) {
    List<LimitOrder> bids = createOrders(pair, Order.OrderType.BID, s.getBids());
    List<LimitOrder> asks = createOrders(pair, Order.OrderType.ASK, s.getAsks());
    return new OrderBook(new Date(s.getTimestamp()), asks, bids);
  }

  public static Trades adaptTrades(List<DaseTrade> trades, CurrencyPair pair) {
    List<org.knowm.xchange.dto.marketdata.Trade> out = new ArrayList<>(trades == null ? 0 : trades.size());
    if (trades != null) {
      for (DaseTrade tr : trades) {
        out.add(
            new org.knowm.xchange.dto.marketdata.Trade.Builder()
                .type(
                    "buy".equalsIgnoreCase(tr.getSide()) ? Order.OrderType.BID : Order.OrderType.ASK)
                .originalAmount(tr.getSize())
                .price(tr.getPrice())
                .instrument(pair)
                .timestamp(new Date(tr.getTime()))
                .id(tr.getId())
                .build());
      }
    }
    return new Trades(out, Trades.TradeSortType.SortByTimestamp);
  }

  private static List<LimitOrder> createOrders(
      CurrencyPair pair, Order.OrderType side, List<List<BigDecimal>> levels) {
    List<LimitOrder> out = new ArrayList<>(levels == null ? 0 : levels.size());
    if (levels == null) {
      return out;
    }
    for (List<BigDecimal> l : levels) {
      if (l == null || l.size() != 2) {
        continue;
      }
      BigDecimal price = l.get(0);
      BigDecimal amount = l.get(1);
      if (price == null || amount == null) {
        continue;
      }
      out.add(new LimitOrder(side, amount, pair, null, null, price));
    }
    return out;
  }

  public static List<FundingRecord> adaptFundingRecords(List<ApiAccountTxn> txns) {
    List<FundingRecord> out = new ArrayList<>(txns == null ? 0 : txns.size());
    if (txns == null) {
      return out;
    }
    for (ApiAccountTxn t : txns) {
      if (t == null) {
        continue;
      }
      Currency currency = t.getCurrency() == null ? null : Currency.getInstance(t.getCurrency());
      Date date = new Date(t.getCreatedAt());

      FundingRecord.Type type = mapTxnTypeToFundingType(t.getTxnType());
      FundingRecord.Status status = FundingRecord.Status.COMPLETE;
      String description = t.getTxnType();

      FundingRecord fr =
          FundingRecord.builder()
              .date(date)
              .currency(currency)
              .amount(t.getAmount())
              .internalId(t.getId())
              .type(type)
              .status(status)
              .description(description)
              .build();
      out.add(fr);
    }
    return out;
  }

  private static FundingRecord.Type mapTxnTypeToFundingType(String txnType) {
    if (txnType == null) {
      return FundingRecord.Type.OTHER_OUTFLOW;
    }
    switch (txnType) {
      case "deposit":
        return FundingRecord.Type.DEPOSIT;
      case "withdrawal_commit":
        return FundingRecord.Type.WITHDRAWAL;
      case "withdrawal_block":
        return FundingRecord.Type.OTHER_OUTFLOW;
      case "withdrawal_unblock":
        return FundingRecord.Type.OTHER_INFLOW;
      case "trade_fill_fee_base":
      case "trade_fill_fee_quote":
        return FundingRecord.Type.OTHER_OUTFLOW;
      case "trade_fill_credit_base":
      case "trade_fill_credit_quote":
        return FundingRecord.Type.OTHER_INFLOW;
      case "trade_fill_debit_base":
      case "trade_fill_debit_quote":
        return FundingRecord.Type.OTHER_OUTFLOW;
      case "portfolio_transfer_credit":
        return FundingRecord.Type.INTERNAL_DEPOSIT;
      case "portfolio_transfer_debit":
        return FundingRecord.Type.INTERNAL_WITHDRAWAL;
      default:
        return FundingRecord.Type.OTHER_OUTFLOW;
    }
  }
}
