package org.knowm.xchange.utils;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CertHelperTest {

  @Test
  public void testTrustAllCertsDisabledByDefault() {
    String original = System.getProperty("xchange.allow.trust.all.certs");
    try {
      System.clearProperty("xchange.allow.trust.all.certs");
      assertThrows(SecurityException.class, CertHelper::trustAllCerts);
    } finally {
      if (original != null) {
        System.setProperty("xchange.allow.trust.all.certs", original);
      } else {
        System.clearProperty("xchange.allow.trust.all.certs");
      }
    }
  }

  @Test
  public void testTrustAllCertsEnabledWithProperty() throws Exception {
    String original = System.getProperty("xchange.allow.trust.all.certs");
    try {
      System.setProperty("xchange.allow.trust.all.certs", "true");
      CertHelper.trustAllCerts();
    } finally {
      if (original != null) {
        System.setProperty("xchange.allow.trust.all.certs", original);
      } else {
        System.clearProperty("xchange.allow.trust.all.certs");
      }
    }
  }
}
