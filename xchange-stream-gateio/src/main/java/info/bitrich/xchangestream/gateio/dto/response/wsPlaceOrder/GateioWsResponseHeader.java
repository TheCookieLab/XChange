package info.bitrich.xchangestream.gateio.dto.response.wsPlaceOrder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class GateioWsResponseHeader {

  @JsonProperty("response_time")
  private String responseTime;

  @JsonProperty("status")
  private String status;

  @JsonProperty("channel")
  private String channel;

  @JsonProperty("event")
  private String event;

  @JsonProperty("client_id")
  private String clientId;

  @JsonProperty("x_in_time")
  private Long xInTime;

  @JsonProperty("x_out_time")
  private Long xOutTime;

  @JsonProperty("conn_trace_id")
  private String connTraceId;

  @JsonProperty("trace_id")
  private String traceId;

  @JsonProperty("x_gate_ratelimit_requests_remain")
  private Integer xGateRatelimitRequestsRemain;

  @JsonProperty("x_gate_ratelimit_limit")
  private Integer xGateRatelimitLimit;

  @JsonProperty("x_gat_ratelimit_reset_timestamp")
  private Long xGatRatelimitResetTimestamp;

  @JsonProperty("conn_id")
  private String connId;

  /** @return the exchange's request receipt timestamp, or {@code null} if omitted */
  @JsonProperty("x_in_time")
  public Long getXInTime() {
    return xInTime;
  }

  /** @param xInTime the exchange's request receipt timestamp, or {@code null} if omitted */
  @JsonProperty("x_in_time")
  public void setXInTime(Long xInTime) {
    this.xInTime = xInTime;
  }

  /** @return the exchange's response send timestamp, or {@code null} if omitted */
  @JsonProperty("x_out_time")
  public Long getXOutTime() {
    return xOutTime;
  }

  /** @param xOutTime the exchange's response send timestamp, or {@code null} if omitted */
  @JsonProperty("x_out_time")
  public void setXOutTime(Long xOutTime) {
    this.xOutTime = xOutTime;
  }

  /** @return the remaining requests in the rate-limit window, or {@code null} if omitted */
  @JsonProperty("x_gate_ratelimit_requests_remain")
  public Integer getXGateRatelimitRequestsRemain() {
    return xGateRatelimitRequestsRemain;
  }

  /** @param xGateRatelimitRequestsRemain the remaining requests, or {@code null} if omitted */
  @JsonProperty("x_gate_ratelimit_requests_remain")
  public void setXGateRatelimitRequestsRemain(Integer xGateRatelimitRequestsRemain) {
    this.xGateRatelimitRequestsRemain = xGateRatelimitRequestsRemain;
  }

  /** @return the request limit for the rate-limit window, or {@code null} if omitted */
  @JsonProperty("x_gate_ratelimit_limit")
  public Integer getXGateRatelimitLimit() {
    return xGateRatelimitLimit;
  }

  /** @param xGateRatelimitLimit the request limit, or {@code null} if omitted */
  @JsonProperty("x_gate_ratelimit_limit")
  public void setXGateRatelimitLimit(Integer xGateRatelimitLimit) {
    this.xGateRatelimitLimit = xGateRatelimitLimit;
  }

  /** @return the rate-limit reset time in epoch milliseconds, or {@code null} if omitted */
  @JsonProperty("x_gat_ratelimit_reset_timestamp")
  public Long getXGatRatelimitResetTimestamp() {
    return xGatRatelimitResetTimestamp;
  }

  /** @param xGatRatelimitResetTimestamp the reset time in epoch milliseconds, or {@code null} */
  @JsonProperty("x_gat_ratelimit_reset_timestamp")
  public void setXGatRatelimitResetTimestamp(Long xGatRatelimitResetTimestamp) {
    this.xGatRatelimitResetTimestamp = xGatRatelimitResetTimestamp;
  }
}
