package org.knowm.xchange.dase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dase.dto.account.DaseBalanceItem;
import org.knowm.xchange.dase.dto.account.DaseBalancesResponse;
import org.knowm.xchange.dase.dto.marketdata.DaseOrderBookSnapshot;
import org.knowm.xchange.dase.dto.marketdata.DaseTicker;
import org.knowm.xchange.dase.dto.marketdata.DaseTrade;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;

public final class DaseAdapters {

  private DaseAdapters() {
  }

  public static String toMarketString(CurrencyPair pair) {
    return pair.getBase().getCurrencyCode() + "-" + pair.getCounter().getCurrencyCode();
  }

  public static AccountInfo adaptAccountInfo(
      String portfolioId, DaseBalancesResponse balancesResponse) {
    List<Balance> balances = new ArrayList<>();
    if (balancesResponse != null && balancesResponse.getBalances() != null) {
      for (DaseBalanceItem b : balancesResponse.getBalances()) {
        Currency currency = Currency.getInstance(b.getCurrency());
        Balance xchgBalance = new Balance(currency, b.getTotal(), b.getAvailable(), b.getBlocked());
        balances.add(xchgBalance);
      }
    }
    Wallet wallet = Wallet.Builder.from(balances).build();
    return new AccountInfo(portfolioId, null, List.of(wallet));
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
}
