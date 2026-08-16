package org.knowm.xchange.cryptocom.dto;

import lombok.Getter;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Structured exchange exception carrying the full provider/request context of a failed Crypto.com
 * call: envelope request id, API method, transport, provider code/message, HTTP status and the
 * retry classification. Identities embedded are safe (order id, client reference, instrument,
 * account) — secrets or full parameter payloads are never part of the exception state.
 */
@Getter
public class CryptoComRequestException extends ExchangeException {

  private static final long serialVersionUID = 1L;

  /** Request id of the failed envelope; 0 when the failure happened before an id was assigned. */
  private final long requestId;

  /** Crypto.com Exchange v1 method, e.g. {@code private/create-order}. */
  private final String method;

  /** REST or WebSocket transport. */
  private final CryptoComTransport transport;

  /** Provider numeric error code; {@code null} when the provider sent no code. */
  private final Integer providerCode;

  /** Provider error message, sanitized (no credentials/keys). */
  private final String providerMessage;

  /** HTTP status of the failure; {@code null} for WebSocket or unknown status. */
  private final Integer httpStatus;

  /** Retry classification derived from the failure. */
  private final CryptoComRetryClass retryClass;

  /** Safe identities known at failure time: order id, client reference, instrument, account. */
  private final String orderId;

  private final String clientOid;

  private final String instrumentName;

  private final String accountId;

  public CryptoComRequestException(Builder builder) {
    super(builder.message, builder.cause);
    this.requestId = builder.requestId;
    this.method = builder.method;
    this.transport = builder.transport;
    this.providerCode = builder.providerCode;
    this.providerMessage = builder.providerMessage;
    this.httpStatus = builder.httpStatus;
    this.retryClass = builder.retryClass;
    this.orderId = builder.orderId;
    this.clientOid = builder.clientOid;
    this.instrumentName = builder.instrumentName;
    this.accountId = builder.accountId;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder; message is assembled from the structured fields when not set explicitly. */
  public static final class Builder {
    private long requestId;
    private String method;
    private CryptoComTransport transport = CryptoComTransport.REST;
    private Integer providerCode;
    private String providerMessage;
    private Integer httpStatus;
    private CryptoComRetryClass retryClass = CryptoComRetryClass.NONE;
    private String orderId;
    private String clientOid;
    private String instrumentName;
    private String accountId;
    private String message;
    private Throwable cause;

    public Builder requestId(long requestId) {
      this.requestId = requestId;
      return this;
    }

    public Builder method(String method) {
      this.method = method;
      return this;
    }

    public Builder transport(CryptoComTransport transport) {
      this.transport = transport;
      return this;
    }

    public Builder providerCode(Integer providerCode) {
      this.providerCode = providerCode;
      return this;
    }

    public Builder providerMessage(String providerMessage) {
      this.providerMessage = providerMessage;
      return this;
    }

    public Builder httpStatus(Integer httpStatus) {
      this.httpStatus = httpStatus;
      return this;
    }

    public Builder retryClass(CryptoComRetryClass retryClass) {
      this.retryClass = retryClass;
      return this;
    }

    public Builder orderId(String orderId) {
      this.orderId = orderId;
      return this;
    }

    public Builder clientOid(String clientOid) {
      this.clientOid = clientOid;
      return this;
    }

    public Builder instrumentName(String instrumentName) {
      this.instrumentName = instrumentName;
      return this;
    }

    public Builder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder cause(Throwable cause) {
      this.cause = cause;
      return this;
    }

    private String buildMessage() {
      if (message != null) {
        return message;
      }
      StringBuilder sb = new StringBuilder("Crypto.com request failed");
      if (method != null) {
        sb.append(" [").append(method).append(']');
      }
      sb.append(": requestId=").append(requestId);
      if (providerCode != null) {
        sb.append(", providerCode=").append(providerCode);
      }
      if (providerMessage != null && !providerMessage.isEmpty()) {
        sb.append(", message=").append(providerMessage);
      }
      if (retryClass != null) {
        sb.append(", retryClass=").append(retryClass);
      }
      return sb.toString();
    }

    public CryptoComRequestException build() {
      return new CryptoComRequestException(this);
    }
  }
}