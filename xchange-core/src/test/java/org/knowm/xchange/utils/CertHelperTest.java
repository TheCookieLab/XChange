package org.knowm.xchange.utils;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

public class CertHelperTest {

  @Test
  public void testTrustAllCerts() throws Exception {
    SSLSocketFactory originalFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    HostnameVerifier originalVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
    try {
      CertHelper.trustAllCerts();
      SSLSocketFactory factory = HttpsURLConnection.getDefaultSSLSocketFactory();
      assertThat(factory).isNotNull();
    } finally {
      HttpsURLConnection.setDefaultSSLSocketFactory(originalFactory);
      HttpsURLConnection.setDefaultHostnameVerifier(originalVerifier);
    }
  }
}
