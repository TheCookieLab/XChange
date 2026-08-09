package org.knowm.xchange.krakenfutures.dto.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** Parses realistic v3 payloads for the extended positions, account log, and funding history. */
public class KrakenFuturesAccountJSONTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .disable(
              com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private static <T> T parse(String resource, Class<T> type) throws IOException {
    try (InputStream in = KrakenFuturesAccountJSONTest.class.getResourceAsStream(resource)) {
      return MAPPER.readValue(in, type);
    }
  }

  @Test
  void open_positions_carry_full_risk_detail() throws IOException {
    org.knowm.xchange.krakenfutures.dto.trade.KrakenFuturesOpenPositions positions =
        parse(
            "/org/knowm/xchange/krakenfutures/dto/account/openpositions.json",
            org.knowm.xchange.krakenfutures.dto.trade.KrakenFuturesOpenPositions.class);

    assertThat(positions.isSuccess()).isTrue();
    assertThat(positions.getOpenPositions()).hasSize(1);
    org.knowm.xchange.krakenfutures.dto.trade.KrakenFuturesOpenPosition position =
        positions.getOpenPositions().get(0);
    assertThat(position.getInstrument()).isEqualTo("PI_XBTUSD");
    assertThat(position.getSide()).isEqualTo("long");
    assertThat(position.getSize()).isEqualByComparingTo("1");
    assertThat(position.getMarkPrice()).isEqualByComparingTo("50000");
    assertThat(position.getLiqPrice()).isEqualByComparingTo("12345");
    assertThat(position.getLeverage()).isEqualByComparingTo("50");
    assertThat(position.getMargin()).isEqualByComparingTo("20");
    assertThat(position.getInitialMargin()).isEqualByComparingTo("10");
    assertThat(position.getMaintMargin()).isEqualByComparingTo("5");
    assertThat(position.getCollateral()).isEqualByComparingTo("1000");
    assertThat(position.getUnrealizedPnl()).isEqualByComparingTo("123");
    assertThat(position.getRealizedPnl()).isEqualByComparingTo("45");
    assertThat(position.getUnrealizedFunding()).isEqualByComparingTo("-0.0001");
    assertThat(position.getRealizedFunding()).isEqualByComparingTo("0.0001");
    assertThat(position.getIndexPrice()).isEqualByComparingTo("49999");
    assertThat(position.getValue()).isEqualByComparingTo("50000");
  }

  @Test
  void account_log_parses_entries_with_cursor_ids() throws IOException {
    KrakenFuturesAccountLog log =
        parse(
            "/org/knowm/xchange/krakenfutures/dto/account/accountlog.json",
            KrakenFuturesAccountLog.class);

    assertThat(log.isSuccess()).isTrue();
    assertThat(log.getAccountLog()).hasSize(2);
    KrakenFuturesAccountLog.KrakenFuturesAccountLogEntry first = log.getAccountLog().get(0);
    assertThat(first.getId()).isEqualTo("e74f1a1a-1b2b-4a5e-9f6d-000000000001");
    assertThat(first.getType()).isEqualTo("deposit");
    assertThat(first.getAmount()).isEqualByComparingTo("1000");
    assertThat(first.getWallet()).isEqualTo("flexible");
    assertThat(first.getBalance()).isEqualByComparingTo("1500");
    KrakenFuturesAccountLog.KrakenFuturesAccountLogEntry second = log.getAccountLog().get(1);
    assertThat(second.getType()).isEqualTo("funding");
    assertThat(second.getInstrument()).isEqualTo("PI_XBTUSD");
    assertThat(second.getChange()).isEqualByComparingTo("-0.00015");
  }

  @Test
  void funding_history_parses_payments() throws IOException {
    KrakenFuturesFundingHistory history =
        parse(
            "/org/knowm/xchange/krakenfutures/dto/account/fundinghistory.json",
            KrakenFuturesFundingHistory.class);

    assertThat(history.isSuccess()).isTrue();
    assertThat(history.getHistory()).hasSize(1);
    KrakenFuturesFundingHistory.KrakenFuturesFundingPayment payment = history.getHistory().get(0);
    assertThat(payment.getInstrument()).isEqualTo("PI_XBTUSD");
    assertThat(payment.getFundingRate()).isEqualByComparingTo("0.0001");
    assertThat(payment.getMarkPrice()).isEqualByComparingTo("50000");
    assertThat(payment.getFundingAmount()).isEqualByComparingTo("-0.005");
  }
}
