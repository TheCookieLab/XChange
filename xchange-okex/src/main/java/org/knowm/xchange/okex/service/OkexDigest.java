package org.knowm.xchange.okex.service;

/**
 * Legacy signature digest kept for source and binary compatibility.
 *
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxDigest} instead.
 */
@Deprecated
public class OkexDigest extends org.knowm.xchange.okx.service.OkxDigest {
  private OkexDigest(String secretKeyBase64) {
    super(secretKeyBase64);
  }

  /**
   * @deprecated use {@link org.knowm.xchange.okx.service.OkxDigest#createInstance(String)} instead.
   */
  @Deprecated
  public static OkexDigest createInstance(String secretKeyBase64) {
    return secretKeyBase64 == null ? null : new OkexDigest(secretKeyBase64);
  }
}
