package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/** Offline tests for {@link OkxRedaction}. */
public class OkxRedactionTest {

  @Test
  public void testMaskReplacesKnownSecrets() {
    assertThat(OkxRedaction.mask("hello api-key-12345678 world", "api-key-12345678"))
        .isEqualTo("hello *** world");
  }

  @Test
  public void testMaskReplacesMultipleSecrets() {
    assertThat(OkxRedaction.mask("a secret-abcdefgh and passphrase-12345678", "secret-abcdefgh", "passphrase-12345678"))
        .isEqualTo("a *** and ***");
  }

  @Test
  public void testMaskReplacesAllOccurrences() {
    assertThat(OkxRedaction.mask("x-abcdefgh y-abcdefgh", "abcdefgh"))
        .isEqualTo("x-*** y-***");
  }

  @Test
  public void testMaskIgnoresShortSecrets() {
    // secrets shorter than MIN_SECRET_LENGTH are ignored to avoid mangling innocent text
    assertThat(OkxRedaction.mask("an api key of 'ab' is short", "ab"))
        .isEqualTo("an api key of 'ab' is short");
  }

  @Test
  public void testMaskIgnoresNullAndBlankSecrets() {
    assertThat(OkxRedaction.mask("keep me", (String) null)).isEqualTo("keep me");
    assertThat(OkxRedaction.mask("keep me", "")).isEqualTo("keep me");
  }

  @Test
  public void testMaskNullValueReturnsNull() {
    assertThat(OkxRedaction.mask(null, "some-secret-1234")).isNull();
  }

  @Test
  public void testMaskEmptyValueReturnsEmpty() {
    assertThat(OkxRedaction.mask("", "some-secret-1234")).isEmpty();
  }

  @Test
  public void testMaskNormalizesOkxAccessHeaders() {
    assertThat(OkxRedaction.mask("OK-ACCESS-KEY: realApiKeyValue OK-ACCESS-SIGN=abc123 OK-ACCESS-PASSPHRASE: hunter2"))
        .isEqualTo("OK-ACCESS-KEY: *** OK-ACCESS-SIGN: *** OK-ACCESS-PASSPHRASE: ***");
  }

  @Test
  public void testMaskHeaderNormalizationIsCaseInsensitive() {
    assertThat(OkxRedaction.mask("ok-access-key: realApiKeyValue"))
        .isEqualTo("ok-access-key: ***");
  }
}
