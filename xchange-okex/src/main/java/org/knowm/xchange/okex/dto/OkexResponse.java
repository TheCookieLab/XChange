package org.knowm.xchange.okex.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.okx.dto.OkxResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.OkxResponse} instead.
 */
@Deprecated
public class OkexResponse<T> {

  private final OkxResponse<T> delegate;

  public OkexResponse(OkxResponse<T> delegate) {
    this.delegate = delegate;
  }

  /**
   * Legacy constructor retained for binary and source compatibility with pre-rename clients.
   *
   * @param id the response id
   * @param code the response code ({@code "0"} signals success)
   * @param msg the response message
   * @param data the payload
   */
  @JsonCreator
  public OkexResponse(
      @JsonProperty("id") String id,
      @JsonProperty("code") String code,
      @JsonProperty("msg") String msg,
      @JsonProperty("data") T data) {
    this.delegate = new OkxResponse<>(id, code, msg, data);
  }

  public static <T> OkexResponse<T> of(OkxResponse<T> response) {
    return new OkexResponse<>(response);
  }

  public String getId() {
    return delegate.getId();
  }

  public String getCode() {
    return delegate.getCode();
  }

  public String getMsg() {
    return delegate.getMsg();
  }

  public T getData() {
    return delegate.getData();
  }

  public boolean isSuccess() {
    return delegate.isSuccess();
  }

  @Override
  public String toString() {
    return "OkexResponse{" + "code=" + getCode() + ", msg=" + getMsg() + '}';
  }
}
