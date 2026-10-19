package org.knowm.xchange.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class DateUtilsTest {

  private final DateFormat isoDateFormat;
  private final DateFormat rfc3339DateFormat;

  public DateUtilsTest() {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    isoDateFormat.setTimeZone(tz);

    rfc3339DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    rfc3339DateFormat.setTimeZone(tz);
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
    DateUtils.fromISODateString(input);
  }

  @Test
  public void testFromISO8601DateString() throws Exception {
    String input = "2016-06-10T12:16:11";
    Date expectedOutput = rfc3339DateFormat.parse("2016-06-10 12:16:11");

    assertEquals(expectedOutput, DateUtils.fromISO8601DateString(input));
  }

  @Test(expected = InvalidFormatException.class)
  public void testFromISO8601DateStringInvalid() throws Exception {
    DateUtils.fromISO8601DateString("invalid-date");
  }

  @Test
  public void testFromRfc1123DateString() throws Exception {
    String input = "Fri, 10 Jun 2016 12:16:11 GMT";
    Date expectedOutput = rfc3339DateFormat.parse("2016-06-10 12:16:11");

    assertEquals(expectedOutput, DateUtils.fromRfc1123DateString(input, Locale.US));
  }

  @Test(expected = InvalidFormatException.class)
  public void testFromRfc1123DateStringInvalid() throws Exception {
    DateUtils.fromRfc1123DateString("invalid-date", Locale.US);
  }

  @Test
  public void testFromRFC3339DateString() throws Exception {
    String input = "2018-01-15 12:16:11";
    Date expectedOutput = rfc3339DateFormat.parse("2018-01-15 12:16:11");

    assertEquals(expectedOutput, DateUtils.fromRfc3339DateString(input));
  }

  @Test(expected = InvalidFormatException.class)
  public void testFromRFC3339DateStringInvalid() throws Exception {
    DateUtils.fromRfc3339DateString("invalid-date");
  }

  @Test
  public void testFormattingMethods() throws Exception {
    Date date = isoDateFormat.parse("2016-06-10T12:16:11.717");

    assertEquals("2016-06-10 12:16:11 GMT", DateUtils.toUTCString(date));
    assertEquals("2016-06-10T12:16:11.717Z", DateUtils.toUTCISODateString(date));
    assertEquals("2016-06-10T12:16:11", DateUtils.toISO8601DateString(date));
  }

  @Test
  public void testNullInputs() throws Exception {
    assertNull(DateUtils.fromISODateString(null));
    assertNull(DateUtils.fromISO8601DateString(null));
    assertNull(DateUtils.fromRfc1123DateString(null, Locale.US));
    assertNull(DateUtils.fromRfc3339DateString(null));
  }

  @Test
  public void testUnixTimeConversions() {
    Date date = new Date(1465560971000L);
    assertEquals(1465560971L, DateUtils.toUnixTime(1465560971000L));
    assertEquals(1465560971L, DateUtils.toUnixTime(date));
    assertEquals(Long.valueOf(1465560971L), DateUtils.toUnixTimeNullSafe(date));
    assertNull(DateUtils.toUnixTimeNullSafe(null));
    assertEquals(Long.valueOf(1465560971000L), DateUtils.toMillisNullSafe(date));
    assertNull(DateUtils.toMillisNullSafe(null));

    assertEquals(date, DateUtils.fromUnixTime(1465560971L));
    assertEquals(date, DateUtils.fromUnixTimeWithMilliseconds(1465560971000L));
  }
}
