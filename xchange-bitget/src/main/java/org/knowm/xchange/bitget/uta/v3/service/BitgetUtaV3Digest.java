package org.knowm.xchange.bitget.uta.v3.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * HMAC-SHA256 request signer for the Bitget UTA v3 API.
 *
 * <p>Preimage: {@code timestamp + METHOD + "/" + path + ("?" + sortedQuery if non-empty) + body},
 * where {@code timestamp} is the {@code ACCESS-TIMESTAMP} header (Unix milliseconds as a string)
 * and the query string is sorted ascending by key. The result is Base64-encoded.
 *
 * <p>rescu 3.x builds query strings in insertion order and URL-encodes values, so this digest
 * re-sorts the pairs by key before signing. Bitget v3 requires the sorted form; classic v2 (see
 * {@code BitgetDigest}) does not sort. Values seen in practice are plain alphanumerics, so the
 * URL-encoded form equals the raw form for signing purposes.
 */
public final class BitgetUtaV3Digest extends BaseParamsDigest {

  private BitgetUtaV3Digest(String secretKey) {
    super(secretKey, HMAC_SHA_256);
  }

  public static BitgetUtaV3Digest createInstance(String secretKey) {
    return secretKey == null ? null : new BitgetUtaV3Digest(secretKey);
  }

  @SneakyThrows
  @Override
  public String digestParams(RestInvocation restInvocation) {
    String method = restInvocation.getHttpMethod().toUpperCase(Locale.ROOT);
    String path = restInvocation.getPath();

    String query = restInvocation.getQueryString();
    String sortedQuery = sortQueryParams(query);

    String body = StringUtils.defaultIfEmpty(restInvocation.getRequestBody(), "");
    String timestamp = restInvocation.getHttpHeadersFromParams().get("ACCESS-TIMESTAMP");

    String preimage = timestamp + method + "/" + path + sortedQuery + body;

    Mac mac = getMac();
    mac.update(preimage.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(mac.doFinal());
  }

  /**
   * Sorts {@code key=value} pairs ascending by key, preserving URL-encoded values, or returns an
   * empty string when the query is empty/blank. The v3 preimage only contains the {@code ?} and
   * query when a query is present.
   */
  private static String sortQueryParams(String query) {
    if (StringUtils.isBlank(query)) {
      return "";
    }
    String[] pairs = query.split("&");
    Arrays.sort(pairs);
    return "?" + String.join("&", pairs);
  }
}
