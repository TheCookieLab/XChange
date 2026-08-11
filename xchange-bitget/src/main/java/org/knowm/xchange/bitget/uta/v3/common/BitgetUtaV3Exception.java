package org.knowm.xchange.bitget.uta.v3.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 error body, parsed by rescu when the response {@code code} is not {@code "00000"}.
 *
 * <p>Carries the provider code/message verbatim; {@link BitgetUtaV3ErrorAdapter} converts it into a
 * structured {@code ExchangeException} subclass carrying API mode, endpoint context, sanitized
 * order identity and retry class.
 */
@Value
@Builder
@Jacksonized
public class BitgetUtaV3Exception extends RuntimeException {

  @JsonProperty("code")
  String code;

  @JsonProperty("msg")
  String message;

  @JsonProperty("requestTime")
  Instant requestTime;

  @JsonProperty("data")
  Object data;
}
