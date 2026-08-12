package org.knowm.xchange.bitget.uta.v3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.mazi.rescu.RestInvocation;

@ExtendWith(MockitoExtension.class)
class BitgetUtaV3DigestTest {

  @Mock RestInvocation restInvocation;

  @Test
  void signature_without_query_or_body() {
    BitgetUtaV3Digest digest = BitgetUtaV3Digest.createInstance("secret");

    when(restInvocation.getHttpMethod()).thenReturn("get");
    when(restInvocation.getPath()).thenReturn("api/v3/account/assets");
    when(restInvocation.getQueryString()).thenReturn("");
    when(restInvocation.getRequestBody()).thenReturn(null);
    Map<String, String> headers = Map.of("ACCESS-TIMESTAMP", "1725040472073");
    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    String expected = "6I+NWpsZcP4obENIIG0j893n5BRobdOqX04SGEOcgRY=";

    assertThat(digest.digestParams(restInvocation)).isEqualTo(expected);
  }

  @Test
  void signature_sorts_query_params_by_key() {
    BitgetUtaV3Digest digest = BitgetUtaV3Digest.createInstance("secret");

    // rescu hands over query in insertion order; v3 requires ascending-key order
    when(restInvocation.getHttpMethod()).thenReturn("get");
    when(restInvocation.getPath()).thenReturn("api/v3/trade/unfilled-orders");
    when(restInvocation.getQueryString()).thenReturn("symbol=BTCUSDT&limit=10");
    when(restInvocation.getRequestBody()).thenReturn(null);
    Map<String, String> headers = Map.of("ACCESS-TIMESTAMP", "1725040472073");
    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    // preimage must be ...?limit=10&symbol=BTCUSDT (sorted), not the insertion order
    String expected = "SZndOQA76B0v71yrbi2p9u0b2hz1laXRrfmzcmtNzmA=";

    assertThat(digest.digestParams(restInvocation)).isEqualTo(expected);
  }

  @Test
  void signature_with_body() {
    BitgetUtaV3Digest digest = BitgetUtaV3Digest.createInstance("secret");

    when(restInvocation.getHttpMethod()).thenReturn("post");
    when(restInvocation.getPath()).thenReturn("api/v3/trade/order");
    when(restInvocation.getQueryString()).thenReturn("symbol=BTCUSDT&category=usdt-futures");
    when(restInvocation.getRequestBody()).thenReturn("{\"price\":\"100\"}");
    Map<String, String> headers = Map.of("ACCESS-TIMESTAMP", "1725040472073");
    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    // sorted query: category=usdt-futures&symbol=BTCUSDT, method uppercased
    String expected = "gZ9mVBqdKWzE+3a5ApDK55CdojE4N1iUpxlfUXSS4Iw=";

    assertThat(digest.digestParams(restInvocation)).isEqualTo(expected);
  }
}
