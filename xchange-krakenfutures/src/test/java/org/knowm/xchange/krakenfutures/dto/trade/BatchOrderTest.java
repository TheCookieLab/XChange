package org.knowm.xchange.krakenfutures.dto.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.Test;

public class BatchOrderTest {

  @Test
  public void testToString() {
    BatchOrder batchOrder = new BatchOrder(Collections.emptyList());
    assertThat(batchOrder.toString()).isEqualTo("{\"batchOrder\":[]}");
  }

  @Test
  public void testGetBatchOrder() {
    BatchOrder batchOrder = new BatchOrder(Collections.emptyList());
    assertThat(batchOrder.getBatchOrder()).isEmpty();
  }
}
