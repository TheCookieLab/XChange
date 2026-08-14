package org.knowm.xchange.mexc.v3.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Listen-key payload of the user-data-stream endpoints. */
public class MexcV3ListenKey {

  private final String listenKey;

  public MexcV3ListenKey(@JsonProperty("listenKey") String listenKey) {
    this.listenKey = listenKey;
  }

  public String getListenKey() {
    return listenKey;
  }
}
