package org.knowm.xchange.gateio.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateioPublicApiJavadocTest {

  private static final Path AUTHENTICATED_API =
      Path.of("src/main/java/org/knowm/xchange/gateio/GateioV4Authenticated.java");
  private static final List<String> NEW_METHODS =
      List.of(
          "getOpenOrders(",
          "amendOrder(",
          "cancelAllOrders(",
          "createBatchOrders(",
          "cancelBatchOrders(",
          "countdownCancelAll(");

  @Test
  void newAuthenticatedMethods_haveJavadocs() throws IOException {
    List<String> lines = Files.readAllLines(AUTHENTICATED_API);
    for (String method : NEW_METHODS) {
      int declaration = -1;
      for (int line = 0; line < lines.size(); line++) {
        if (lines.get(line).contains(" " + method)) {
          declaration = line;
          break;
        }
      }
      assertThat(declaration).as("missing method %s", method).isGreaterThanOrEqualTo(0);

      boolean documented = false;
      for (int line = declaration - 1; line >= Math.max(0, declaration - 15); line--) {
        if (lines.get(line).trim().startsWith("/**")) {
          documented = true;
          break;
        }
      }
      assertThat(documented)
          .as("new public method %s must document its provider contract", method)
          .isTrue();
    }
  }
}
