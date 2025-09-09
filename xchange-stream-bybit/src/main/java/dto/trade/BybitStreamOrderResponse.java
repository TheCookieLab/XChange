package dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.bybit.dto.trade.BybitOrderResponse;

@Getter
@Builder
@Jacksonized
@JsonInclude(Include.NON_NULL)
public class BybitStreamOrderResponse {
  private final String reqId;
  private final int retCode;
  private final String retMsg;
  private final String op;
  private final BybitOrderResponse data;
  private final Header header;
  private final String connId;

  @Getter
  @Builder
  @Jacksonized
  public static  class Header {
    @JsonProperty("Traceid")
    private final String traceId;
    @JsonProperty("Timenow")
    private final String timeNow;
    @JsonProperty("X-Bapi-Limit")
    private final String xBapiLimit;
    @JsonProperty("X-Bapi-Limit-Status")
    private final String xBapiLimitStatus;
    @JsonProperty("X-Bapi-Limit-Reset-Timestamp")
    private final String xBapiLimitResetTimestamp;
  }

}
