package org.knowm.xchange.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link DateUtils} to the behavior of the original per-call {@link SimpleDateFormat}
 * implementation: exact output strings, parse results (including lenient and legacy inputs),
 * following {@link TimeZone#setDefault(TimeZone)} at call time, and the exceptions thrown.
 */
public class DateUtilsTest {

  private static final String ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
  private static final String UTC_STRING_PATTERN = "yyyy-MM-dd HH:mm:ss z";
  private static final String RFC3339_PATTERN = "yyyy-MM-dd HH:mm:ss";

  /** 2019-06-08T13:20:00.123Z */
  private static final long FIXED_MILLIS = 1_560_000_000_123L;

  /** 2019-01-01T00:00:00.000Z */
  private static final long WINTER_MILLIS = 1_546_300_800_000L;

  /** 1582-10-04T23:59:59.999Z in the Julian calendar used by SimpleDateFormat. */
  private static final long JULIAN_LAST_MILLIS = -12_219_292_800_001L;

  /** 10000-01-01T00:00:00.000Z */
  private static final long YEAR_10000_MILLIS = 253_402_300_800_000L;

  /** 9999-01-01T00:00:00.000Z */
  private static final long YEAR_9999_MILLIS = 253_370_764_800_000L;

  private static final String[] ZONES = {
    "UTC",
    "America/New_York",
    "Asia/Kolkata",
    "Europe/Berlin",
    "Australia/Lord_Howe",
    "Pacific/Kiritimati",
    "America/St_Johns",
    "Africa/Monrovia"
  };

  private final DateFormat isoDateFormat;
  private final DateFormat rfc3339DateFormat;

  private TimeZone originalZone;
  private Locale originalLocale;

  public DateUtilsTest() {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    isoDateFormat.setTimeZone(tz);

    rfc3339DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  }

  @Before
  public void rememberDefaults() {
    originalZone = TimeZone.getDefault();
    originalLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @After
  public void restoreDefaults() {
    TimeZone.setDefault(originalZone);
    Locale.setDefault(originalLocale);
  }

  private static void useZone(String zoneId) {
    TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
  }

  private static String legacyFormat(String pattern, TimeZone zone, long millis) {
    SimpleDateFormat format = new SimpleDateFormat(pattern);
    format.setTimeZone(zone);
    return format.format(new Date(millis));
  }

  @Test
  public void testFromISODateString() throws Exception {
    String input = "2016-06-10T12:16:11.717Z";
    Date expectedOutput = isoDateFormat.parse("2016-06-10T12:16:11.717");

    assertEquals(expectedOutput, DateUtils.fromISODateString(input));
  }

  @Test(expected = InvalidFormatException.class)
  public void testFromISODateStringWrongTimezone() throws Exception {

    String input = "2016-06-10T12:16:11.717";
    Date expectedOutput = isoDateFormat.parse("2016-06-10T12:16:11.717");

    assertEquals(expectedOutput, DateUtils.fromISODateString(input));
  }

  @Test
  public void testFromRFC3339DateString() throws Exception {
    String input = "2018-01-15 12:16:11";
    Date expectedOutput = rfc3339DateFormat.parse("2018-01-15 12:16:11");

    assertEquals(expectedOutput, DateUtils.fromRfc3339DateString(input));
  }

  // ---------------------------------------------------------------------------------------------
  // Formatting: exact strings
  // ---------------------------------------------------------------------------------------------

  @Test
  public void fixedZoneFormattersProduceExactStringsRegardlessOfDefaultZone() {
    Date date = new Date(FIXED_MILLIS);
    for (String zone : ZONES) {
      useZone(zone);
      assertEquals(zone, "2019-06-08 13:20:00 GMT", DateUtils.toUTCString(date));
      assertEquals(zone, "2019-06-08T13:20:00.123Z", DateUtils.toUTCISODateString(date));
      assertEquals(zone, "2019-06-08T13:20:00", DateUtils.toISO8601DateString(date));
    }
  }

  @Test
  public void toUTCStringEndsWithGmtAndPadsFields() {
    assertEquals("1970-01-01 00:00:00 GMT", DateUtils.toUTCString(new Date(0L)));
    assertEquals("2019-01-01 00:00:00 GMT", DateUtils.toUTCString(new Date(WINTER_MILLIS)));
    assertEquals("1969-12-31 23:59:59 GMT", DateUtils.toUTCString(new Date(-1L)));
  }

  @Test
  public void toISO8601DateStringIsSecondsOnlyAndTruncatesMillis() {
    assertEquals("2019-06-08T13:20:00", DateUtils.toISO8601DateString(new Date(FIXED_MILLIS)));
    assertEquals(
        "2019-06-08T13:20:00", DateUtils.toISO8601DateString(new Date(FIXED_MILLIS + 876)));
    assertEquals("1969-12-31T23:59:59", DateUtils.toISO8601DateString(new Date(-1L)));
  }

  @Test
  public void toUTCISODateStringPadsMilliseconds() {
    assertEquals("1970-01-01T00:00:00.000Z", DateUtils.toUTCISODateString(new Date(0L)));
    assertEquals("1970-01-01T00:00:00.005Z", DateUtils.toUTCISODateString(new Date(5L)));
    assertEquals("1970-01-01T00:00:00.050Z", DateUtils.toUTCISODateString(new Date(50L)));
    assertEquals("1969-12-31T23:59:59.999Z", DateUtils.toUTCISODateString(new Date(-1L)));
  }

  @Test
  public void toISODateStringFormatsInDefaultZoneKeepingLiteralZ() {
    Date summer = new Date(FIXED_MILLIS);
    Date winter = new Date(WINTER_MILLIS);

    useZone("UTC");
    assertEquals("2019-06-08T13:20:00.123Z", DateUtils.toISODateString(summer));
    assertEquals("2019-01-01T00:00:00.000Z", DateUtils.toISODateString(winter));

    useZone("America/New_York");
    assertEquals("2019-06-08T09:20:00.123Z", DateUtils.toISODateString(summer));
    assertEquals("2018-12-31T19:00:00.000Z", DateUtils.toISODateString(winter));

    useZone("Asia/Kolkata");
    assertEquals("2019-06-08T18:50:00.123Z", DateUtils.toISODateString(summer));
    assertEquals("2019-01-01T05:30:00.000Z", DateUtils.toISODateString(winter));

    useZone("Pacific/Kiritimati");
    assertEquals("2019-06-09T03:20:00.123Z", DateUtils.toISODateString(summer));
  }

  @Test
  public void formattersMatchLegacySimpleDateFormatAcrossInstantsAndZones() {
    Random random = new Random(20190608L);
    long[] edges = {
      0L,
      1L,
      999L,
      -1L,
      -86_400_000L,
      FIXED_MILLIS,
      WINTER_MILLIS,
      1_552_208_399_999L,
      1_552_208_400_000L,
      1_572_760_799_999L,
      1_572_760_800_000L,
      4_102_444_800_000L,
      YEAR_9999_MILLIS - 1,
      YEAR_9999_MILLIS,
      YEAR_10000_MILLIS - 1,
      YEAR_10000_MILLIS,
      JULIAN_LAST_MILLIS,
      JULIAN_LAST_MILLIS + 1,
      -2_208_988_800_000L,
      -30_000_000_000_000L
    };
    TimeZone gmt = TimeZone.getTimeZone("GMT");
    for (String zoneId : ZONES) {
      useZone(zoneId);
      TimeZone zone = TimeZone.getDefault();
      for (int i = 0; i < 2_000 + edges.length; i++) {
        long millis =
            i < edges.length
                ? edges[i]
                : (i % 4 == 0
                    ? random.nextInt(Integer.MAX_VALUE) * 1_000L + random.nextInt(1_000)
                    : (long) (random.nextDouble() * YEAR_9999_MILLIS));
        Date date = new Date(millis);
        String label = zoneId + " @" + millis;
        assertEquals(
            label, legacyFormat(UTC_STRING_PATTERN, gmt, millis), DateUtils.toUTCString(date));
        assertEquals(
            label, legacyFormat(ISO_PATTERN, gmt, millis), DateUtils.toUTCISODateString(date));
        assertEquals(
            label,
            legacyFormat(ISO_8601_PATTERN, gmt, millis),
            DateUtils.toISO8601DateString(date));
        assertEquals(
            label, legacyFormat(ISO_PATTERN, zone, millis), DateUtils.toISODateString(date));
      }
    }
  }

  @Test
  public void instantsOutsideFastRangeKeepLegacyCalendarOutput() {
    useZone("UTC");
    // SimpleDateFormat follows the Julian calendar before 1582-10-15 and prints years above 9999
    // without a sign; these must stay as the historical output.
    assertEquals("1582-10-04 23:59:59 GMT", DateUtils.toUTCString(new Date(JULIAN_LAST_MILLIS)));
    assertEquals(
        "1582-10-04T23:59:59.999Z", DateUtils.toUTCISODateString(new Date(JULIAN_LAST_MILLIS)));
    assertEquals(
        "1582-10-04T23:59:59", DateUtils.toISO8601DateString(new Date(JULIAN_LAST_MILLIS)));
    assertEquals(
        "1582-10-04T23:59:59.999Z", DateUtils.toISODateString(new Date(JULIAN_LAST_MILLIS)));
    assertEquals("10000-01-01 00:00:00 GMT", DateUtils.toUTCString(new Date(YEAR_10000_MILLIS)));
    assertEquals(
        "10000-01-01T00:00:00.000Z", DateUtils.toUTCISODateString(new Date(YEAR_10000_MILLIS)));

    useZone("America/New_York");
    assertEquals(
        "1582-10-04T19:00:00.000Z", DateUtils.toISODateString(new Date(-12_219_292_800_000L)));
  }

  @Test
  public void fastRangeBoundariesFormatExactly() {
    useZone("UTC");
    assertEquals("1970-01-01 00:00:00 GMT", DateUtils.toUTCString(new Date(0L)));
    assertEquals("9998-12-31 23:59:59 GMT", DateUtils.toUTCString(new Date(YEAR_9999_MILLIS - 1)));
    assertEquals("9999-01-01 00:00:00 GMT", DateUtils.toUTCString(new Date(YEAR_9999_MILLIS)));
    assertEquals(
        "9999-12-31T23:59:59.999Z", DateUtils.toUTCISODateString(new Date(YEAR_10000_MILLIS - 1)));
  }

  @Test
  public void formattingDoesNotDependOnDefaultLocale() {
    Date date = new Date(FIXED_MILLIS);
    Locale.setDefault(Locale.GERMANY);
    assertEquals("2019-06-08 13:20:00 GMT", DateUtils.toUTCString(date));
    assertEquals("2019-06-08T13:20:00.123Z", DateUtils.toUTCISODateString(date));
    assertEquals("2019-06-08T13:20:00", DateUtils.toISO8601DateString(date));
    useZone("UTC");
    assertEquals("2019-06-08T13:20:00.123Z", DateUtils.toISODateString(date));
  }

  @Test
  public void formattingNullDateThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> DateUtils.toUTCString(null));
    assertThrows(NullPointerException.class, () -> DateUtils.toUTCISODateString(null));
    assertThrows(NullPointerException.class, () -> DateUtils.toISO8601DateString(null));
    assertThrows(NullPointerException.class, () -> DateUtils.toISODateString(null));
  }

  // ---------------------------------------------------------------------------------------------
  // Following the default time zone at call time (changed after DateUtils is loaded)
  // ---------------------------------------------------------------------------------------------

  @Test
  public void toISODateStringFollowsDefaultZoneChangedAfterClassLoad() {
    Date date = new Date(FIXED_MILLIS);
    useZone("UTC");
    String inUtc = DateUtils.toISODateString(date);

    useZone("America/New_York");
    String inNewYork = DateUtils.toISODateString(date);

    useZone("Asia/Kolkata");
    String inKolkata = DateUtils.toISODateString(date);

    useZone("UTC");
    String backInUtc = DateUtils.toISODateString(date);

    assertEquals("2019-06-08T13:20:00.123Z", inUtc);
    assertEquals("2019-06-08T09:20:00.123Z", inNewYork);
    assertEquals("2019-06-08T18:50:00.123Z", inKolkata);
    assertEquals(inUtc, backInUtc);
  }

  @Test
  public void fromRfc3339DateStringFollowsDefaultZoneChangedAfterClassLoad() throws Exception {
    String input = "2018-01-15 12:16:11";

    useZone("UTC");
    assertEquals(1_516_018_571_000L, DateUtils.fromRfc3339DateString(input).getTime());

    useZone("America/New_York");
    assertEquals(1_516_036_571_000L, DateUtils.fromRfc3339DateString(input).getTime());

    useZone("Asia/Kolkata");
    assertEquals(1_515_998_771_000L, DateUtils.fromRfc3339DateString(input).getTime());

    useZone("UTC");
    assertEquals(1_516_018_571_000L, DateUtils.fromRfc3339DateString(input).getTime());
  }

  @Test
  public void fromRfc3339DateStringFollowsDefaultZoneOnOtherThreadsToo() throws Exception {
    String input = "2018-01-15 12:16:11";
    useZone("UTC");
    assertEquals(1_516_018_571_000L, DateUtils.fromRfc3339DateString(input).getTime());

    useZone("America/New_York");
    AtomicReference<Long> parsed = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              try {
                parsed.set(DateUtils.fromRfc3339DateString(input).getTime());
              } catch (InvalidFormatException e) {
                throw new IllegalStateException(e);
              }
            });
    worker.start();
    worker.join();
    assertEquals(Long.valueOf(1_516_036_571_000L), parsed.get());
  }

  @Test
  public void fromRfc1123DateStringAbbreviationFollowsDefaultZoneChangedAfterClassLoad()
      throws Exception {
    // "IST" is India Standard Time (+05:30) when India is the default zone and otherwise resolves
    // to a different zone (+02:00 here); SimpleDateFormat consults the current default zone first.
    String input = "Sat, 08 Feb 2014 05:04:36 IST";

    useZone("UTC");
    assertEquals(1_391_828_676_000L, DateUtils.fromRfc1123DateString(input, Locale.US).getTime());

    useZone("Asia/Kolkata");
    assertEquals(1_391_816_076_000L, DateUtils.fromRfc1123DateString(input, Locale.US).getTime());

    useZone("America/New_York");
    assertEquals(1_391_828_676_000L, DateUtils.fromRfc1123DateString(input, Locale.US).getTime());
  }

  @Test
  public void fromRfc1123DateStringIsNotAffectedByZoneOfAPreviousInput() throws Exception {
    useZone("Asia/Kolkata");
    // Parsing a zone-qualified input must not leave that zone behind for later parses.
    DateUtils.fromRfc1123DateString("Sat, 08 Feb 2014 05:04:36 PST", Locale.US);
    DateUtils.fromRfc1123DateString("Sat, 08 Feb 2014 05:04:36 EST", Locale.US);

    assertEquals(
        1_391_816_076_000L,
        DateUtils.fromRfc1123DateString("Sat, 08 Feb 2014 05:04:36 IST", Locale.US).getTime());
  }

  @Test
  public void parsersMatchLegacySimpleDateFormatAcrossDefaultZones() throws Exception {
    String[] rfc3339Inputs = {
      "2018-01-15 12:16:11",
      "2018-03-11 02:30:00",
      "2018-11-04 01:30:00",
      "2018-01-15 12:16:11 +0000",
      "1970-01-01 00:00:00",
      "2019-02-30 25:61:61"
    };
    String[] rfc1123Inputs = {
      "Sat, 08 Feb 2014 05:04:36 GMT",
      "Sat, 08 Feb 2014 05:04:36 UTC",
      "Sat, 08 Feb 2014 05:04:36 +0000",
      "Sat, 08 Feb 2014 05:04:36 -0500",
      "Sat, 08 Feb 2014 05:04:36 +0530",
      "Sat, 08 Feb 2014 05:04:36 EST",
      "Sat, 08 Jul 2014 05:04:36 EST",
      "Sat, 08 Jul 2014 05:04:36 EDT",
      "Sat, 08 Feb 2014 05:04:36 EDT",
      "Sat, 08 Feb 2014 05:04:36 PST",
      "Sat, 08 Feb 2014 05:04:36 CET",
      "Sat, 08 Jul 2014 05:04:36 CEST",
      "Sat, 08 Feb 2014 05:04:36 IST",
      "Sat, 08 Feb 2014 05:04:36 BST",
      "Sat, 08 Feb 2014 05:04:36 GMT+03:00",
      "Mon, 08 Feb 2014 05:04:36 GMT",
      "Sat, 31 Feb 2014 05:04:36 GMT",
      "Sat,  8 Feb 14 05:04:36 +0000"
    };
    // Two passes so that reused per-thread formatters are exercised after parsing other inputs.
    for (int pass = 0; pass < 2; pass++) {
      for (String zoneId : ZONES) {
        useZone(zoneId);
        for (String input : rfc3339Inputs) {
          SimpleDateFormat legacy = new SimpleDateFormat(RFC3339_PATTERN);
          assertEquals(
              zoneId + " " + input,
              legacy.parse(input).getTime(),
              DateUtils.fromRfc3339DateString(input).getTime());
        }
        for (String input : rfc1123Inputs) {
          SimpleDateFormat legacy =
              new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
          assertEquals(
              zoneId + " " + input,
              legacy.parse(input).getTime(),
              DateUtils.fromRfc1123DateString(input, Locale.US).getTime());
        }
      }
    }
  }

  @Test
  public void utcParsersIgnoreDefaultZone() throws Exception {
    for (String zone : ZONES) {
      useZone(zone);
      assertEquals(
          zone,
          1_465_560_971_717L,
          DateUtils.fromISODateString("2016-06-10T12:16:11.717Z").getTime());
      assertEquals(
          zone,
          1_465_560_971_000L,
          DateUtils.fromISO8601DateString("2016-06-10T12:16:11").getTime());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Parsing: exact values, round trips and legacy inputs
  // ---------------------------------------------------------------------------------------------

  @Test
  public void fromISODateStringParsesMillisecondsAsUtc() throws Exception {
    assertEquals(
        1_465_560_971_717L, DateUtils.fromISODateString("2016-06-10T12:16:11.717Z").getTime());
    assertEquals(
        1_465_560_971_007L, DateUtils.fromISODateString("2016-06-10T12:16:11.7Z").getTime());
  }

  @Test
  public void fromISODateStringKeepsLenientSimpleDateFormatBehavior() throws Exception {
    // BTC Markets style 6-digit fraction: SSS reads 977000 as milliseconds and rolls the clock
    // forward by 977 seconds (16:01:02 + 00:16:17 = 16:17:19).
    Date btcMarkets = DateUtils.fromISODateString("2019-06-25T16:01:02.977000Z");
    assertEquals(1_561_479_439_000L, btcMarkets.getTime());
    assertEquals("2019-06-25T16:17:19.000Z", DateUtils.toUTCISODateString(btcMarkets));

    // Trailing text after the pattern is ignored, out-of-range fields roll over.
    assertEquals(
        1_465_560_971_717L, DateUtils.fromISODateString("2016-06-10T12:16:11.717Zjunk").getTime());
    assertEquals(
        1_487_124_121_999L, DateUtils.fromISODateString("2016-13-45T25:61:61.999Z").getTime());
  }

  @Test
  public void fromISODateStringRejectsInputWithoutZoneOrMilliseconds() {
    for (String input :
        new String[] {"2016-06-10T12:16:11.717", "2016-06-10T12:16:11Z", "", "garbage"}) {
      assertInvalidFormat(input, () -> DateUtils.fromISODateString(input));
    }
  }

  @Test
  public void fromISO8601DateStringParsesSecondsAsUtcAndIgnoresTrailingText() throws Exception {
    assertEquals(
        1_465_560_971_000L, DateUtils.fromISO8601DateString("2016-06-10T12:16:11").getTime());
    assertEquals(
        1_465_560_971_000L, DateUtils.fromISO8601DateString("2016-06-10T12:16:11Z").getTime());
    assertEquals(
        1_465_560_971_000L, DateUtils.fromISO8601DateString("2016-06-10T12:16:11.717").getTime());
  }

  @Test
  public void fromISO8601DateStringRejectsInvalidInput() {
    for (String input : new String[] {"2016-06-10 12:16:11", "", "x"}) {
      assertInvalidFormat(input, () -> DateUtils.fromISO8601DateString(input));
    }
  }

  @Test
  public void fromRfc3339DateStringParsesInDefaultZoneKeepingLenientBehavior() throws Exception {
    useZone("UTC");
    assertEquals(
        1_516_018_571_000L, DateUtils.fromRfc3339DateString("2018-01-15 12:16:11").getTime());
    // trailing text is ignored
    assertEquals(
        1_516_018_571_000L, DateUtils.fromRfc3339DateString("2018-01-15 12:16:11 +0000").getTime());

    useZone("America/New_York");
    // 02:30 on 2018-03-11 does not exist in New York; lenient parsing moves it forward an hour
    assertEquals(
        1_520_753_400_000L, DateUtils.fromRfc3339DateString("2018-03-11 02:30:00").getTime());
    // 01:30 on 2018-11-04 occurs twice; the first (EDT) occurrence is used
    assertEquals(
        1_541_313_000_000L, DateUtils.fromRfc3339DateString("2018-11-04 01:30:00").getTime());
  }

  @Test
  public void fromRfc3339DateStringRejectsInvalidInput() {
    for (String input : new String[] {"2018-01-15T12:16:11", "", "x"}) {
      assertInvalidFormat(input, () -> DateUtils.fromRfc3339DateString(input));
    }
  }

  @Test
  public void fromRfc1123DateStringParsesKrakenLegacyTwoDigitYear() throws Exception {
    // Kraken sends two spaces before a one-digit day and a two-digit year; yyyy keeps year "14"
    // literally (0014-02-08), exactly as the original SimpleDateFormat did.
    Date parsed = DateUtils.fromRfc1123DateString("Sat,  8 Feb 14 05:04:36 +0000", Locale.US);
    assertEquals(-61_722_240_924_000L, parsed.getTime());
  }

  @Test
  public void fromRfc1123DateStringParsesNumericAndNamedZones() throws Exception {
    assertEquals(1_391_835_876_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 GMT"));
    assertEquals(1_391_835_876_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 UTC"));
    assertEquals(1_391_835_876_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 +0000"));
    assertEquals(1_391_853_876_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 -0500"));
    assertEquals(1_391_816_076_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 +0530"));
    assertEquals(1_391_825_076_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 GMT+03:00"));
  }

  @Test
  public void fromRfc1123DateStringTreatsEstAndEdtAsFixedOffsets() throws Exception {
    for (String zone : new String[] {"UTC", "America/New_York", "Asia/Kolkata"}) {
      useZone(zone);
      // EST is always -05:00 and EDT always -04:00, whatever the date's own DST status is.
      assertEquals(zone, 1_391_853_876_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 EST"));
      assertEquals(zone, 1_404_813_876_000L, rfc1123("Sat, 08 Jul 2014 05:04:36 EST"));
      assertEquals(zone, 1_404_810_276_000L, rfc1123("Sat, 08 Jul 2014 05:04:36 EDT"));
      assertEquals(zone, 1_391_850_276_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 EDT"));
      assertEquals(zone, 1_391_864_676_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 PST"));
    }
  }

  @Test
  public void fromRfc1123DateStringIgnoresMismatchedWeekdayAndRollsOverDay() throws Exception {
    assertEquals(1_391_835_876_000L, rfc1123("Mon, 08 Feb 2014 05:04:36 GMT"));
    assertEquals(1_393_823_076_000L, rfc1123("Sat, 31 Feb 2014 05:04:36 GMT"));
  }

  @Test
  public void fromRfc1123DateStringUsesTheRequestedLocale() throws Exception {
    assertEquals(
        1_391_835_876_000L,
        DateUtils.fromRfc1123DateString("sam., 08 févr. 2014 05:04:36 GMT", Locale.FRANCE)
            .getTime());
    assertEquals(
        1_391_835_876_000L,
        DateUtils.fromRfc1123DateString("Sa., 08 Feb. 2014 05:04:36 GMT", Locale.GERMANY)
            .getTime());
    // English text is not valid for a German formatter, and the cached German formatter must not
    // leak into later English parses on the same thread.
    assertInvalidFormat(
        "Sat, 08 Feb 2014 05:04:36 GMT",
        () -> DateUtils.fromRfc1123DateString("Sat, 08 Feb 2014 05:04:36 GMT", Locale.GERMANY));
    assertEquals(1_391_835_876_000L, rfc1123("Sat, 08 Feb 2014 05:04:36 GMT"));
  }

  @Test
  public void fromRfc1123DateStringRejectsInvalidInput() {
    for (String input :
        new String[] {
          "Sat, 08 Feb 2014 05:04:36 Z",
          "Sat, 08 Feb 2014 05:04:36",
          "Sat, 08 Feb 2014 05:04:36 America/New_York",
          "Sat 08 Feb 2014 05:04:36 GMT",
          "",
          "x"
        }) {
      assertInvalidFormat(input, () -> DateUtils.fromRfc1123DateString(input, Locale.US));
    }
  }

  @Test
  public void formatThenParseRoundTrips() throws Exception {
    Date date = new Date(FIXED_MILLIS);
    assertEquals(date, DateUtils.fromISODateString(DateUtils.toUTCISODateString(date)));
    assertEquals(
        new Date(FIXED_MILLIS - 123),
        DateUtils.fromISO8601DateString(DateUtils.toISO8601DateString(date)));
    assertEquals(
        new Date(FIXED_MILLIS - 123),
        DateUtils.fromRfc1123DateString("Sat, 08 Jun 2019 13:20:00 GMT", Locale.US));
    assertTrue(DateUtils.toUTCString(date).endsWith(" GMT"));

    useZone("America/New_York");
    // toISODateString prints local wall-clock time with a literal Z, so the Z parser cannot
    // recover the instant; the RFC 3339 parser reads the same wall-clock back in the same zone.
    assertEquals("2019-06-08T09:20:00.123Z", DateUtils.toISODateString(date));
    assertEquals(
        new Date(FIXED_MILLIS - 123),
        DateUtils.fromRfc3339DateString(
            DateUtils.toISODateString(date).replace('T', ' ').substring(0, 19)));
    assertNotEquals(date, DateUtils.fromISODateString(DateUtils.toISODateString(date)));
  }

  @Test
  public void parsingNullInputThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> DateUtils.fromISODateString(null));
    assertThrows(NullPointerException.class, () -> DateUtils.fromISO8601DateString(null));
    assertThrows(NullPointerException.class, () -> DateUtils.fromRfc3339DateString(null));
    assertThrows(
        NullPointerException.class, () -> DateUtils.fromRfc1123DateString(null, Locale.US));
    assertThrows(
        NullPointerException.class,
        () -> DateUtils.fromRfc1123DateString("Sat, 08 Feb 2014 05:04:36 GMT", null));
  }

  // ---------------------------------------------------------------------------------------------
  // Epoch helpers
  // ---------------------------------------------------------------------------------------------

  @Test
  public void epochHelpersConvertBetweenMillisAndUnixTime() {
    Date date = new Date(FIXED_MILLIS);
    assertEquals(date, DateUtils.fromMillisUtc(FIXED_MILLIS));
    assertEquals(date, DateUtils.fromUnixTimeWithMilliseconds(FIXED_MILLIS));
    assertEquals(new Date(1_560_000_000_000L), DateUtils.fromUnixTime(1_560_000_000L));
    assertEquals(1_560_000_000L, DateUtils.toUnixTime(FIXED_MILLIS));
    assertEquals(1_560_000_000L, DateUtils.toUnixTime(date));
    assertEquals(Long.valueOf(1_560_000_000L), DateUtils.toUnixTimeNullSafe(date));
    assertEquals(Long.valueOf(FIXED_MILLIS), DateUtils.toMillisNullSafe(date));
    assertEquals(1_560_000_000L, DateUtils.toUnixTimeOptional(date).orElseThrow().longValue());
    assertNull(DateUtils.toUnixTimeNullSafe(null));
    assertNull(DateUtils.toMillisNullSafe(null));
    assertTrue(DateUtils.toUnixTimeOptional(null).isEmpty());
  }

  private static long rfc1123(String input) throws InvalidFormatException {
    return DateUtils.fromRfc1123DateString(input, Locale.US).getTime();
  }

  private interface ThrowingCall {
    void run() throws Exception;
  }

  private static void assertInvalidFormat(String input, ThrowingCall call) {
    InvalidFormatException thrown = assertThrows(InvalidFormatException.class, call::run);
    assertEquals(input, thrown.getValue());
    assertEquals(Date.class, thrown.getTargetType());
  }
}
