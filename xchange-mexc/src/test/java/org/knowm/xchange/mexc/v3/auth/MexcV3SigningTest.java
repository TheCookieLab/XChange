package org.knowm.xchange.mexc.v3.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.QueryParam;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

/**
 * Canonical payload assembly rules for MEXC Spot v3 request signing.
 *
 * <p>An authenticated request whose parameters are entirely in the query string (for example
 * {@code GET /api/v3/account} or {@code POST /api/v3/order}) has no request body: rescu leaves the
 * entity {@code null}, and the payload must sign the empty body rather than the literal string
 * {@code "null"} — MEXC rejects the latter with an invalid-signature response.
 */
public class MexcV3SigningTest {

  @Test
  public void absentBodySignsAsEmptyString() {
    String query = "symbol=BTCUSDT&side=BUY&timestamp=1723200000000";

    assertThat(MexcV3Signing.signingPayload(invocation("POST", query, null)))
        .isEqualTo("symbol=BTCUSDT&side=BUY&timestamp=1723200000000");
  }

  @Test
  public void bodyIsAppendedWithoutSeparator() {
    String query = "symbol=BTCUSDT&side=BUY&timestamp=1723200000000";
    String body = "quantity=0.001";

    assertThat(MexcV3Signing.signingPayload(invocation("POST", query, body)))
        .isEqualTo("symbol=BTCUSDT&side=BUY&timestamp=1723200000000quantity=0.001");
  }

  @Test
  public void signatureParameterIsExcluded() {
    String query = "symbol=BTCUSDT&signature=abc123&timestamp=1723200000000";

    assertThat(MexcV3Signing.signingPayload(invocation("GET", query, null)))
        .isEqualTo("symbol=BTCUSDT&timestamp=1723200000000");
  }

  private static RestInvocation invocation(String method, String queryString, String body) {
    RestInvocation invocation = org.mockito.Mockito.mock(RestInvocation.class);
    Params queryParams = org.mockito.Mockito.mock(Params.class);
    Map<String, String> headers = new LinkedHashMap<>();
    for (String pair : queryString.split("&")) {
      String[] kv = pair.split("=", 2);
      headers.put(kv[0], kv.length > 1 ? kv[1] : "");
    }
    org.mockito.Mockito.when(queryParams.asHttpHeaders()).thenReturn(headers);
    org.mockito.Mockito.when(invocation.getParamsMap())
        .thenReturn(Map.of(QueryParam.class, queryParams));
    org.mockito.Mockito.when(invocation.getHttpMethod()).thenReturn(method);
    org.mockito.Mockito.when(invocation.getRequestBody()).thenReturn(body);
    return invocation;
  }
}
