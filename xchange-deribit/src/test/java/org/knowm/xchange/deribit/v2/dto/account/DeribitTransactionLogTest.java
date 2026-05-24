package org.knowm.xchange.deribit.v2.dto.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DeribitTransactionLogTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesInfoMapWithNullValues() throws Exception {
    DeribitTransactionLog transactionLog =
        objectMapper.readValue(
            """
            {
              "info": {
                "addr": null,
                "note": "memo-123",
                "transaction": "tx-456"
              }
            }
            """,
            DeribitTransactionLog.class);

    assertThat(transactionLog.getInfoMap())
        .containsEntry("addr", null)
        .containsEntry("note", "memo-123")
        .containsEntry("transaction", "tx-456");
    assertThat(transactionLog.getAddress()).isNull();
    assertThat(transactionLog.getAddressTag()).isEqualTo("memo-123");
    assertThat(transactionLog.getBlockchainTransactionHash()).isEqualTo("tx-456");
  }
}
