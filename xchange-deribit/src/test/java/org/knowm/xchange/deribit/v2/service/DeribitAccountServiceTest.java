package org.knowm.xchange.deribit.v2.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.deribit.DeribitExchangeWiremock;
import org.knowm.xchange.deribit.v2.service.params.DeribitFundingHistoryParams;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.FundingRecord.Status;
import org.knowm.xchange.dto.account.FundingRecord.Type;

class DeribitAccountServiceTest extends DeribitExchangeWiremock {

  @Test
  void funding_history() throws IOException {
    List<FundingRecord> actual =
        exchange
            .getAccountService()
            .getFundingHistory(
                DeribitFundingHistoryParams.builder()
                    .currency(Currency.USDT)
                    .build());

    FundingRecord expected =
        FundingRecord.builder()
            .internalId("0xa5aee397bc7d0005519f8cd24b29779b0637dfa298567a8a5b8b38ac14293e03")
            .date(Date.from(Instant.parse("2025-11-16T22:47:15.280Z")))
            .address("0x1c5a37bc3670026367d38108777c94e5fdaf7a7c")
            .addressTag("")
            .currency(Currency.USDT)
            .type(Type.DEPOSIT)
            .amount(new BigDecimal("49.54"))
            .status(Status.COMPLETE)
            .build();

    assertThat(actual).hasSize(3);
    assertThat(actual).first().usingRecursiveComparison().isEqualTo(expected);
  }


}