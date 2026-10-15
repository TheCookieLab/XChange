package org.knowm.xchange.utils;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilities to provide the following to application:
 *
 * <ul>
 *   <li>Provision of standard date and time handling
 * </ul>
 */
public class DateUtils {

  // Performance optimization: Thread-safe DateTimeFormatter constants to avoid expensive per-call
  // SimpleDateFormat object allocations and pattern compilation on high-frequency paths (e.g. JSON deserialization).
  private static final DateTimeFormatter UTC_STRING_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US).withZone(ZoneId.of("GMT"));

  private static final DateTimeFormatter UTC_ISO_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
          .withZone(ZoneId.of("GMT"));

  private static final DateTimeFormatter ISO_8601_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US).withZone(ZoneId.of("GMT"));

  private static final DateTimeFormatter ISO_DATE_FORMATTER_LOCAL =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
          .withZone(ZoneId.systemDefault());

  private static final DateTimeFormatter ISO_DATE_FORMATTER_UTC =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
          .optionalStart()
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .optionalEnd()
          .appendLiteral('Z')
          .toFormatter(Locale.US)
          .withZone(ZoneId.of("UTC"));

  private static final DateTimeFormatter ISO_8601_FORMATTER_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US).withZone(ZoneId.of("UTC"));

  private static final DateTimeFormatter RFC3339_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
          .withZone(ZoneId.systemDefault());

  private static final Map<Locale, DateTimeFormatter> RFC1123_FORMATTERS =
      new ConcurrentHashMap<>();

  /** private Constructor */
  private DateUtils() {}

  /**
   * Creates a date from a long representing milliseconds from epoch
   *
   * @param millisecondsFromEpoch
   * @return the Date object
   */
  public static Date fromMillisUtc(long millisecondsFromEpoch) {

    return new Date(millisecondsFromEpoch);
  }

  /**
   * Converts a date to a UTC String representation
   *
   * @param date
   * @return the formatted date
   */
  public static String toUTCString(Date date) {

    return UTC_STRING_FORMATTER.format(date.toInstant());
  }

  public static String toUTCISODateString(Date date) {
    return UTC_ISO_DATE_FORMATTER.format(date.toInstant());
  }

  public static String toISO8601DateString(Date date) {

    return ISO_8601_FORMATTER.format(date.toInstant());
  }

  public static String toISODateString(Date date) {
    return ISO_DATE_FORMATTER_LOCAL.format(date.toInstant());
  }

  /**
   * Converts an ISO formatted Date String to a Java Date ISO format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
   *
   * @param isoFormattedDate
   * @return Date
   * @throws com.fasterxml.jackson.databind.exc.InvalidFormatException
   */
  public static Date fromISODateString(String isoFormattedDate)
      throws com.fasterxml.jackson.databind.exc.InvalidFormatException {

    try {
      Instant instant = Instant.from(ISO_DATE_FORMATTER_UTC.parse(isoFormattedDate));
      return Date.from(instant);
    } catch (Exception e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", isoFormattedDate, Date.class);
    }
  }

  /**
   * Converts an ISO 8601 formatted Date String to a Java Date ISO 8601 format:
   * yyyy-MM-dd'T'HH:mm:ss
   *
   * @param iso8601FormattedDate
   * @return Date
   * @throws com.fasterxml.jackson.databind.exc.InvalidFormatException
   */
  public static Date fromISO8601DateString(String iso8601FormattedDate)
      throws com.fasterxml.jackson.databind.exc.InvalidFormatException {

    try {
      ParsePosition pos = new ParsePosition(0);
      TemporalAccessor ta = ISO_8601_FORMATTER_UTC.parse(iso8601FormattedDate, pos);
      if (ta == null || pos.getIndex() == 0) {
        throw new IllegalArgumentException("Failed to parse date: " + iso8601FormattedDate);
      }
      Instant instant = Instant.from(ta);
      return Date.from(instant);
    } catch (Exception e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", iso8601FormattedDate, Date.class);
    }
  }

  /**
   * Converts an rfc1123 formatted Date String to a Java Date rfc1123 format: EEE, dd MMM yyyy
   * HH:mm:ss zzz
   *
   * @param rfc1123FormattedDate
   * @return Date
   * @throws com.fasterxml.jackson.databind.exc.InvalidFormatException
   */
  public static Date fromRfc1123DateString(String rfc1123FormattedDate, Locale locale)
      throws com.fasterxml.jackson.databind.exc.InvalidFormatException {

    DateTimeFormatter formatter =
        RFC1123_FORMATTERS.computeIfAbsent(
            locale,
            loc -> DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", loc));
    try {
      Instant instant = Instant.from(formatter.parse(rfc1123FormattedDate));
      return Date.from(instant);
    } catch (Exception e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", rfc1123FormattedDate, Date.class);
    }
  }

  /**
   * Converts an RFC3339 formatted Date String to a Java Date RFC3339 format: yyyy-MM-dd HH:mm:ss
   *
   * @param rfc3339FormattedDate RFC3339 formatted Date
   * @return an {@link Date} object
   * @throws InvalidFormatException the RFC3339 formatted Date is invalid or cannot be parsed.
   * @see <a href="https://tools.ietf.org/html/rfc3339">The Internet Society - RFC 3339</a>
   */
  public static Date fromRfc3339DateString(String rfc3339FormattedDate)
      throws InvalidFormatException {

    try {
      Instant instant = Instant.from(RFC3339_FORMATTER.parse(rfc3339FormattedDate));
      return Date.from(instant);
    } catch (Exception e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", rfc3339FormattedDate, Date.class);
    }
  }

  /** Convert java time long to unix time long, simply by dividing by 1000 */
  public static long toUnixTime(long javaTime) {
    return javaTime / 1000;
  }

  /** Convert java time to unix time long, simply by dividing by the time 1000 */
  public static long toUnixTime(Date time) {
    return time.getTime() / 1000;
  }

  /** Convert java time to unix time long, simply by dividing by the time 1000. Null safe */
  public static Long toUnixTimeNullSafe(Date time) {

    return time == null ? null : time.getTime() / 1000;
  }

  public static Optional<Long> toUnixTimeOptional(Date time) {

    return Optional.ofNullable(time).map(it -> it.getTime() / 1000);
  }

  public static Long toMillisNullSafe(Date time) {

    return time == null ? null : time.getTime();
  }

  /** Convert unix time to Java Date */
  public static Date fromUnixTime(long unix) {
    return new Date(unix * 1000);
  }

  /** Convert unix time with milliseconds to Java Date */
  public static Date fromUnixTimeWithMilliseconds(long milliseconds) {
    return new Date(milliseconds);
  }
}
