package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** Countdown-cancel-all result (POST /spot/countdown_cancel_all). */
@Data
@Builder
@Jacksonized
public class GateioCountdownCancelResult {

  /** Whether the countdown was armed for the account. */
  @JsonProperty("triggered")
  Boolean triggered;

  /** Order ids that would be cancelled when the countdown expires. */
  @JsonProperty("order_ids")
  List<String> orderIds;
}
