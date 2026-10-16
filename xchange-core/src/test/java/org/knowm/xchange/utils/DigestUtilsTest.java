package org.knowm.xchange.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DigestUtilsTest {

  @Test
  public void testBytesToHex() {
    byte[] bytes = new byte[] {0, 15, 16, (byte) 255, (byte) 0x80, (byte) 0x7f};
    String expected = "000f10ff807f";
    assertEquals(expected, DigestUtils.bytesToHex(bytes));
  }

  @Test
  public void testHexToBytes() {
    String hex = "000f10ff807f";
    byte[] expected = new byte[] {0, 15, 16, (byte) 255, (byte) 0x80, (byte) 0x7f};
    assertArrayEquals(expected, DigestUtils.hexToBytes(hex));
  }

  @Test
  public void testHexToBytesUppercase() {
    String hex = "000F10FF807F";
    byte[] expected = new byte[] {0, 15, 16, (byte) 255, (byte) 0x80, (byte) 0x7f};
    assertArrayEquals(expected, DigestUtils.hexToBytes(hex));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testHexToBytesOddLength() {
    DigestUtils.hexToBytes("000f10ff807");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testHexToBytesInvalidChar() {
    DigestUtils.hexToBytes("000f10ff80zz");
  }
}
