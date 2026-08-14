package org.knowm.xchange.mexc.v3.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code GET /api/v3/userDataStream} response: all currently valid listen keys. */
public class MexcV3ListenKeyList {

  private final List<String> listenKey;

  public MexcV3ListenKeyList(@JsonProperty("listenKey") List<String> listenKey) {
    this.listenKey = listenKey;
  }

  public List<String> getListenKey() {
    return listenKey;
  }
}
