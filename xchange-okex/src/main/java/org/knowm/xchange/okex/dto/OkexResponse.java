package org.knowm.xchange.okex.dto;

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
}
