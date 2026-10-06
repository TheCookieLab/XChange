package org.knowm.xchange.exceptions;

import java.util.Objects;

/**
 * A rate-limited operation was terminated by the universal rate limiter instead of completing.
 *
 * <p>It is a {@link RateLimitExceededException}, so existing {@code catch
 * (RateLimitExceededException e)} sites keep working. The machine-readable {@link #getReason()}
 * says why the operation ended and {@link #getDispatch()} says whether anything reached the
 * exchange. The message never contains credentials, request bodies or scope identifiers.
 *
 * @since 1.0.3
 */
public class RateLimitTerminatedException extends RateLimitExceededException {

  private static final long serialVersionUID = 1L;

  /**
   * Why the operation was terminated.
   *
   * @since 1.0.3
   */
  public enum Reason {
    /** The pending queue of the operation's priority class is full. */
    QUEUE_SATURATED,
    /** The operation deadline expired before the request could be dispatched. */
    DEADLINE_EXCEEDED,
    /** The calling thread was interrupted before the request could be dispatched. */
    CANCELLED,
    /** The declared cost exceeds the capacity of a required budget and can never be admitted. */
    IMPOSSIBLE_COST,
    /** The rate-limit context was closed. */
    SHUTDOWN,
    /** The exchange keeps rejecting the request or banned the caller; no further replay is allowed. */
    REMOTE_PRESSURE_EXHAUSTED,
    /** The policy does not classify the operation, so it is rejected before it is sent. */
    UNCLASSIFIED_OPERATION
  }

  /**
   * Whether the request reached the exchange.
   *
   * @since 1.0.3
   */
  public enum Dispatch {
    /** Nothing was sent to the exchange. */
    NOT_SENT,
    /** The request was sent and the exchange definitively rejected it. */
    REJECTED,
    /** The request was sent and its outcome is unknown; it must not be replayed. */
    UNCERTAIN
  }

  private final Reason reason;
  private final Dispatch dispatch;

  /**
   * Creates a terminal outcome.
   *
   * @param reason why the operation was terminated
   * @param dispatch whether the request reached the exchange
   * @param message credential-free description
   * @param cause the original failure, or {@code null}
   */
  public RateLimitTerminatedException(
      Reason reason, Dispatch dispatch, String message, Throwable cause) {
    super(message, cause);
    this.reason = Objects.requireNonNull(reason, "reason");
    this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
  }

  /**
   * @return why the operation was terminated
   */
  public Reason getReason() {
    return reason;
  }

  /**
   * @return whether the request reached the exchange
   */
  public Dispatch getDispatch() {
    return dispatch;
  }
}
