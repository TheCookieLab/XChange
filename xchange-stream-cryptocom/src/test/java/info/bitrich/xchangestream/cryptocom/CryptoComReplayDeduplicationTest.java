package info.bitrich.xchangestream.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.cryptocom.dto.CryptoComUserTradeUpdate;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.account.CryptoComBalance;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrder;

/**
 * Behavioral tests for the stable-identity replay deduplication of private events: fills dedupe on
 * {@code trade_id}, order updates on {@code order_id + update_time}, and balance pushes on the
 * full state snapshot. Deterministic fixtures only - no network or live calls.
 */
public class CryptoComReplayDeduplicationTest {

  private static final String ORDER_CHANNEL = "user.order.BTC_USDT";
  private static final String TRADE_CHANNEL = "user.trade.BTC_USDT";

  @Test
  public void testUserTradesDedupeOnTradeId() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();

    CryptoComUserTradeUpdate fill = new CryptoComUserTradeUpdate();
    fill.setTradeId("5533001");

    assertThat(CryptoComStreamingTradeService.isDuplicateTrade(deduplicator, TRADE_CHANNEL, fill))
        .isFalse();
    // replayed fill of the same trade (same trade_id) is a duplicate
    CryptoComUserTradeUpdate replay = new CryptoComUserTradeUpdate();
    replay.setTradeId("5533001");
    assertThat(CryptoComStreamingTradeService.isDuplicateTrade(deduplicator, TRADE_CHANNEL, replay))
        .isTrue();
    // a genuinely new fill passes
    CryptoComUserTradeUpdate next = new CryptoComUserTradeUpdate();
    next.setTradeId("5533002");
    assertThat(CryptoComStreamingTradeService.isDuplicateTrade(deduplicator, TRADE_CHANNEL, next))
        .isFalse();
  }

  @Test
  public void testFillsWithoutTradeIdAreNeverDropped() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();
    CryptoComUserTradeUpdate fill = new CryptoComUserTradeUpdate();

    assertThat(CryptoComStreamingTradeService.isDuplicateTrade(deduplicator, TRADE_CHANNEL, fill))
        .isFalse();
    assertThat(CryptoComStreamingTradeService.isDuplicateTrade(deduplicator, TRADE_CHANNEL, fill))
        .isFalse();
  }

  @Test
  public void testOrderUpdatesDedupeOnOrderIdAndUpdateTime() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();

    CryptoComOrder order = new CryptoComOrder();
    order.setOrderId("18342311");
    order.setUpdateTime(1785085695512L);
    CryptoComOrder replay = new CryptoComOrder();
    replay.setOrderId("18342311");
    replay.setUpdateTime(1785085695512L);
    CryptoComOrder later = new CryptoComOrder();
    later.setOrderId("18342311");
    later.setUpdateTime(1785085695513L);

    assertThat(CryptoComStreamingTradeService.isDuplicateOrder(deduplicator, ORDER_CHANNEL, order))
        .isFalse();
    assertThat(CryptoComStreamingTradeService.isDuplicateOrder(deduplicator, ORDER_CHANNEL, replay))
        .isTrue();
    // a later update of the same order is a new event
    assertThat(CryptoComStreamingTradeService.isDuplicateOrder(deduplicator, ORDER_CHANNEL, later))
        .isFalse();
  }

  @Test
  public void testBalanceSnapshotsDedupeOnFullState() {
    CryptoComStreamingEventDeduplicator deduplicator = new CryptoComStreamingEventDeduplicator();

    CryptoComBalance.PositionBalance position = new CryptoComBalance.PositionBalance();
    position.setInstrumentName("BTC");
    position.setQuantity("0.015");
    CryptoComBalance balance = new CryptoComBalance();
    balance.setInstrumentName("CRO");
    balance.setPositionBalances(java.util.Collections.singletonList(position));

    CryptoComBalance replay = new CryptoComBalance();
    replay.setInstrumentName("CRO");
    replay.setPositionBalances(java.util.Collections.singletonList(position));

    assertThat(CryptoComStreamingAccountService.isDuplicateBalance(deduplicator, balance)).isFalse();
    // same state snapshot replayed on reconnect is a duplicate
    assertThat(CryptoComStreamingAccountService.isDuplicateBalance(deduplicator, replay)).isTrue();

    // a changed snapshot (new quantity) is a new event
    CryptoComBalance.PositionBalance changed = new CryptoComBalance.PositionBalance();
    changed.setInstrumentName("BTC");
    changed.setQuantity("0.016");
    CryptoComBalance changedBalance = new CryptoComBalance();
    changedBalance.setInstrumentName("CRO");
    changedBalance.setPositionBalances(java.util.Collections.singletonList(changed));
    assertThat(CryptoComStreamingAccountService.isDuplicateBalance(deduplicator, changedBalance))
        .isFalse();
  }
}