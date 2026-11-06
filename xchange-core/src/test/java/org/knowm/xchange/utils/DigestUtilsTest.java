package org.knowm.xchange.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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

  @Test
  public void testHexToBytesOddLength() {
    assertThrows(IllegalArgumentException.class, () -> DigestUtils.hexToBytes("000f10ff807"));
  }

  @Test
  public void testHexToBytesInvalidChar() {
    assertThrows(IllegalArgumentException.class, () -> DigestUtils.hexToBytes("000f10ff80zz"));
  }
}
