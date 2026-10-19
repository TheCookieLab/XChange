package org.knowm.xchange.utils;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

/**
 * Utilities to provide standard date and time handling.
 *
 * <p>Uses thread-safe, pre-compiled {@link DateTimeFormatter} constants to avoid allocation and
 * thread synchronization overhead associated with {@code SimpleDateFormat}.
 */
public class DateUtils {

  // Thread-safe formatters for strict string output formatting
  private static final DateTimeFormatter UTC_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("GMT"));

  private static final DateTimeFormatter ISO_UTC_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter ISO_8601_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter ISO_DEFAULT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.systemDefault());

  // Thread-safe parsers for flexible string parsing
  private static final DateTimeFormatter ISO_UTC_PARSER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .appendLiteral('Z')
          .toFormatter()
          .withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter RFC_1123_PARSER =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .optionalStart()
          .appendPattern("EEE, ")
          .optionalEnd()
          .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
          .appendLiteral(' ')
          .appendPattern("MMM ")
          .optionalStart()
          .appendValue(ChronoField.YEAR, 4)
          .optionalEnd()
          .optionalStart()
          .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
          .optionalEnd()
          .appendPattern(" HH:mm:ss ")
          .optionalStart()
          .appendZoneText(TextStyle.SHORT)
          .optionalEnd()
          .optionalStart()
          .appendOffset("+HHMM", "GMT")
          .optionalEnd()
          .optionalStart()
          .appendOffsetId()
          .optionalEnd()
          .toFormatter()
          .withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter RFC_3339_PARSER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .toFormatter()
          .withZone(ZoneOffset.UTC);

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

    return UTC_FORMATTER.format(date.toInstant());
  }

  public static String toUTCISODateString(Date date) {

    return ISO_UTC_FORMATTER.format(date.toInstant());
  }

  public static String toISO8601DateString(Date date) {

    return ISO_8601_FORMATTER.format(date.toInstant());
  }

  public static String toISODateString(Date date) {

    return ISO_DEFAULT_FORMATTER.format(date.toInstant());
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

    if (isoFormattedDate == null) {
      return null;
    }
    try {
      return Date.from(ISO_UTC_PARSER.parse(isoFormattedDate, Instant::from));
    } catch (DateTimeParseException | IllegalArgumentException e) {
      throw new InvalidFormatException(null, "Error parsing as date", isoFormattedDate, Date.class);
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

    if (iso8601FormattedDate == null) {
      return null;
    }
    try {
      String dateStr = iso8601FormattedDate;
      if (dateStr.length() > 19) {
        dateStr = dateStr.substring(0, 19);
      }
      return Date.from(ISO_8601_FORMATTER.parse(dateStr, Instant::from));
    } catch (DateTimeParseException | IllegalArgumentException e) {
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

    if (rfc1123FormattedDate == null) {
      return null;
    }
    try {
      String normalized = rfc1123FormattedDate.trim().replaceAll("\\s+", " ");
      DateTimeFormatter formatter = RFC_1123_PARSER;
      if (locale != null) {
        formatter = formatter.withLocale(locale);
      }
      return Date.from(formatter.parse(normalized, Instant::from));
    } catch (DateTimeParseException | IllegalArgumentException e) {
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

    if (rfc3339FormattedDate == null) {
      return null;
    }
    try {
      return Date.from(RFC_3339_PARSER.parse(rfc3339FormattedDate, Instant::from));
    } catch (DateTimeParseException | IllegalArgumentException e) {
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
