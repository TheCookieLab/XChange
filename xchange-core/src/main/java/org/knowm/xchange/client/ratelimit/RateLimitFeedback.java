package org.knowm.xchange.client.ratelimit;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Module-normalized outcome of one wire attempt as far as rate limiting is concerned.
 *
 * <p>Feedback can only tighten admission (start a cooldown); it never adds capacity.
 *
 * @since 1.0.3
 */
public final class RateLimitFeedback {

  /**
   * Kind of feedback.
   *
   * @since 1.0.3
   */
  public enum Kind {
    /** Nothing rate related was observed. */
    NONE,
    /** The exchange definitively rejected the request because of rate pressure. */
    RATE_REJECTED,
    /** The exchange banned the caller; the request is not replayed. */
    BANNED
  }

  /** Feedback meaning "nothing rate related observed". */
  public static final RateLimitFeedback NONE = new RateLimitFeedback(Kind.NONE, null);

  private static final List<DateTimeFormatter> HTTP_DATE_FORMATS =
      List.of(
          // IMF-fixdate, e.g. "Sun, 06 Nov 1994 08:49:37 GMT"
          DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
              .withZone(ZoneOffset.UTC),
          // obsolete RFC 850, e.g. "Sunday, 06-Nov-94 08:49:37 GMT"
          DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.ENGLISH)
              .withZone(ZoneOffset.UTC),
          // obsolete asctime, e.g. "Sun Nov  6 08:49:37 1994"
          DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.ENGLISH)
              .withZone(ZoneOffset.UTC));

  private final Kind kind;
  private final Duration retryAfter;

  private RateLimitFeedback(Kind kind, Duration retryAfter) {
    this.kind = kind;
    this.retryAfter = retryAfter;
  }

  /**
   * Confirmed rate rejection.
   *
   * @param retryAfterOrNull provider-reported delay until the budget may be used again; {@code
   *     null}, zero or negative means no usable reset information, in which case the policy's
   *     finite fallback backoff applies
   * @return the feedback
   */
  public static RateLimitFeedback rejected(Duration retryAfterOrNull) {
    return new RateLimitFeedback(Kind.RATE_REJECTED, usable(retryAfterOrNull));
  }

  /**
   * Exchange ban. The request is never replayed.
   *
   * @param durationOrNull ban duration if the provider reports one; {@code null}, zero or negative
   *     means unknown, in which case the policy's fallback backoff cap is used as the cooldown
   * @return the feedback
   */
  public static RateLimitFeedback banned(Duration durationOrNull) {
    return new RateLimitFeedback(Kind.BANNED, usable(durationOrNull));
  }

  private static Duration usable(Duration duration) {
    return duration == null || duration.isZero() || duration.isNegative() ? null : duration;
  }

  /**
   * Parses an HTTP {@code Retry-After} header value: either delta-seconds or an HTTP-date
   * (IMF-fixdate, RFC 850 or asctime), the latter evaluated against the supplied wall-clock
   * instant.
   *
   * @param headerValue the raw header value, may be {@code null}
   * @param wallNow the current wall-clock instant
   * @return the delay, or {@code null} when the value is missing, malformed, or not in the future
   */
  public static Duration parseRetryAfter(String headerValue, Instant wallNow) {
    Objects.requireNonNull(wallNow, "wallNow");
    if (headerValue == null) {
      return null;
    }
    String value = headerValue.trim();
    if (value.isEmpty()) {
      return null;
    }
    if (isDigits(value)) {
      if (value.length() > 18) {
        return null;
      }
      long seconds = Long.parseLong(value);
      return seconds == 0 ? null : Duration.ofSeconds(seconds);
    }
    for (DateTimeFormatter format : HTTP_DATE_FORMATS) {
      try {
        Instant reset = ZonedDateTime.parse(value, format).toInstant();
        Duration delay = Duration.between(wallNow, reset);
        return delay.isNegative() || delay.isZero() ? null : delay;
      } catch (DateTimeException e) {
        // try the next accepted HTTP-date format
      }
    }
    return null;
  }

  private static boolean isDigits(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  /**
   * @return the feedback kind
   */
  public Kind getKind() {
    return kind;
  }

  /**
   * @return the provider-reported delay, or {@code null} when none was usable
   */
  public Duration getRetryAfter() {
    return retryAfter;
  }

  /** Combines two feedbacks of one attempt: BANNED beats RATE_REJECTED beats NONE; longer delay. */
  static RateLimitFeedback strongest(RateLimitFeedback a, RateLimitFeedback b) {
    if (a.kind.ordinal() != b.kind.ordinal()) {
      return a.kind.ordinal() > b.kind.ordinal() ? a : b;
    }
    if (a.retryAfter == null) {
      return b;
    }
    if (b.retryAfter == null) {
      return a;
    }
    return a.retryAfter.compareTo(b.retryAfter) >= 0 ? a : b;
  }

  @Override
  public String toString() {
    return "RateLimitFeedback{kind=" + kind + ", retryAfter=" + retryAfter + '}';
  }
}
