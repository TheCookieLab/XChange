package org.knowm.xchange.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

public class CertHelperTest {

  @Test
  public void doesNotOfferJvmWideTrustAllOverride() {
    assertThrows(NoSuchMethodException.class, () -> CertHelper.class.getMethod("trustAllCerts"));
  }

  @Test
  public void restrictsOnlyEnabledCiphersWithoutChangingProtocolPolicy() throws IOException {
    try (SSLSocket defaultSocket =
            (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      String[] defaultCiphers = defaultSocket.getEnabledCipherSuites();
      assumeTrue(defaultCiphers.length > 1);
      SSLSocketFactory restricted =
          CertHelper.createRestrictedSSLSocketFactory(defaultCiphers[0]);

      try (SSLSocket socket =
          (SSLSocket) restricted.createSocket(server.getInetAddress(), server.getLocalPort())) {
        assertThat(socket.getEnabledCipherSuites())
            .containsExactly(Arrays.copyOfRange(defaultCiphers, 1, defaultCiphers.length));
        assertThat(socket.getEnabledProtocols())
            .containsExactly(defaultSocket.getEnabledProtocols());
      }
    }
  }
}
