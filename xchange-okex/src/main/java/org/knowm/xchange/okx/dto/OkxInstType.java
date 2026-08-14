package org.knowm.xchange.okx.dto;

import org.knowm.xchange.service.marketdata.params.Params;

public enum OkxInstType implements Params {
  SPOT,
  MARGIN,
  SWAP,
  FUTURES,
  OPTION,
  ANY
}
