package org.knowm.xchange.utils;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;

public class CertHelperTest {

  @Test
  public void testTrustAllCerts() throws Exception {
    CertHelper.trustAllCerts();
    SSLSocketFactory factory = HttpsURLConnection.getDefaultSSLSocketFactory();
    assertThat(factory).isNotNull();
  }
}
