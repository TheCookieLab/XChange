package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** Provider response group from GET /spot/open_orders. */
@Data
@Builder
@Jacksonized
public class GateioOpenOrders {

  @JsonProperty("currency_pair")
  String currencyPair;

  @JsonProperty("total")
  Integer total;

  @JsonProperty("orders")
  List<GateioOrder> orders;
}
