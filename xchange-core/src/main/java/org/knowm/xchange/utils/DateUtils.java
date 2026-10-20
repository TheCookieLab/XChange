package org.knowm.xchange.utils;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Utilities to provide the following to application:
 *
 * <ul>
 *   <li>Provision of standard date and time handling
 * </ul>
 */
public class DateUtils {

  // Performance optimization: Thread-safe DateTimeFormatter constants for formatting and
  // ThreadLocal SimpleDateFormat instances for parsing to avoid expensive per-call object
  // allocations. Fixed-zone (GMT/UTC) formatters are constants; paths that follow the JVM default
  // time zone resolve it on every call so that later TimeZone.setDefault(...) calls are honored.
  private static final String UTC_STRING_PATTERN = "yyyy-MM-dd HH:mm:ss z";
  private static final String ISO_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

  /**
   * Instants in [1970-01-01, 9999-01-01) format identically through {@link DateTimeFormatter} and
   * {@link SimpleDateFormat}. Outside it the two diverge (SimpleDateFormat uses the Julian calendar
   * before 1582, local-mean-time offsets differ, years above 9999 gain a sign), so those instants
   * keep using {@link SimpleDateFormat} to preserve the historical output.
   */
  private static final long MIN_FAST_FORMAT_MILLIS = 0L;

  private static final long MAX_FAST_FORMAT_MILLIS = 253_370_764_800_000L;

  private static final ZoneId GMT_ZONE = ZoneId.of("GMT");

  private static final DateTimeFormatter UTC_STRING_FORMATTER =
      DateTimeFormatter.ofPattern(UTC_STRING_PATTERN, Locale.US).withZone(GMT_ZONE);

  private static final DateTimeFormatter UTC_ISO_DATE_FORMATTER =
      DateTimeFormatter.ofPattern(ISO_DATE_PATTERN, Locale.US).withZone(GMT_ZONE);

  private static final DateTimeFormatter ISO_8601_FORMATTER =
      DateTimeFormatter.ofPattern(ISO_8601_PATTERN, Locale.US).withZone(GMT_ZONE);

  /** Zone-less: formats the local date-time resolved against the current default zone. */
  private static final DateTimeFormatter ISO_DATE_FORMATTER_LOCAL =
      DateTimeFormatter.ofPattern(ISO_DATE_PATTERN, Locale.US);

  private static final ThreadLocal<SimpleDateFormat> FROM_ISO_DATE_FORMATTER_TL =
      ThreadLocal.withInitial(
          () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
          });

  private static final ThreadLocal<SimpleDateFormat> FROM_ISO_8601_FORMATTER_TL =
      ThreadLocal.withInitial(
          () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
          });

  private static final ThreadLocal<SimpleDateFormat> FROM_RFC3339_FORMATTER_TL =
      ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

  private static final ThreadLocal<Map<Locale, SimpleDateFormat>> FROM_RFC1123_FORMATTERS_TL =
      ThreadLocal.withInitial(HashMap::new);

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

    if (!isFastFormatRange(date)) {
      return formatLegacy(UTC_STRING_PATTERN, TimeZone.getTimeZone("GMT"), date);
    }
    return UTC_STRING_FORMATTER.format(date.toInstant());
  }

  public static String toUTCISODateString(Date date) {
    if (!isFastFormatRange(date)) {
      return formatLegacy(ISO_DATE_PATTERN, TimeZone.getTimeZone("GMT"), date);
    }
    return UTC_ISO_DATE_FORMATTER.format(date.toInstant());
  }

  public static String toISO8601DateString(Date date) {

    if (!isFastFormatRange(date)) {
      return formatLegacy(ISO_8601_PATTERN, TimeZone.getTimeZone("GMT"), date);
    }
    return ISO_8601_FORMATTER.format(date.toInstant());
  }

  public static String toISODateString(Date date) {
    if (!isFastFormatRange(date)) {
      return formatLegacy(ISO_DATE_PATTERN, TimeZone.getDefault(), date);
    }
    return ISO_DATE_FORMATTER_LOCAL.format(
        LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
  }

  private static boolean isFastFormatRange(Date date) {
    long millis = date.getTime();
    return millis >= MIN_FAST_FORMAT_MILLIS && millis < MAX_FAST_FORMAT_MILLIS;
  }

  private static String formatLegacy(String pattern, TimeZone zone, Date date) {
    SimpleDateFormat format = new SimpleDateFormat(pattern);
    format.setTimeZone(zone);
    return format.format(date);
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
      return FROM_ISO_DATE_FORMATTER_TL.get().parse(isoFormattedDate);
    } catch (ParseException e) {
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

    try {
      return FROM_ISO_8601_FORMATTER_TL.get().parse(iso8601FormattedDate);
    } catch (ParseException e) {
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

    SimpleDateFormat sdf =
        FROM_RFC1123_FORMATTERS_TL
            .get()
            .computeIfAbsent(
                locale, loc -> new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", loc));
    // A previous parse may have switched the formatter's zone to the one named in the input.
    sdf.setTimeZone(TimeZone.getDefault());
    try {
      return sdf.parse(rfc1123FormattedDate);
    } catch (ParseException e) {
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

    SimpleDateFormat sdf = FROM_RFC3339_FORMATTER_TL.get();
    sdf.setTimeZone(TimeZone.getDefault());
    try {
      return sdf.parse(rfc3339FormattedDate);
    } catch (ParseException e) {
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
