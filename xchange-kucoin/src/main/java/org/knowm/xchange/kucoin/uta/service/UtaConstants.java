package org.knowm.xchange.kucoin.uta.service;

/** UTA API header names and shared constants. */
public final class UtaConstants {

  public static final String API_HEADER_KEY = "KC-API-KEY";
  public static final String API_HEADER_SIGN = "KC-API-SIGN";
  public static final String API_HEADER_PASSPHRASE = "KC-API-PASSPHRASE";
  public static final String API_HEADER_TIMESTAMP = "KC-API-TIMESTAMP";
  public static final String API_HEADER_KEY_VERSION = "KC-API-KEY-VERSION";
  public static final String API_HEADER_SITE_TYPE = "X-SITE-TYPE";

  /** HMAC key-version value for the {@code KC-API-KEY-VERSION} header. */
  public static final String KEY_VERSION = "1";

  /** Success code returned in the response envelope for every successful UTA call. */
  public static final String SUCCESS_CODE = "200000";

  /** Server-time endpoint shared by every KuCoin API generation. */
  public static final String SERVER_TIME_PATH = "/api/v1/timestamp";

  private UtaConstants() {}
}
