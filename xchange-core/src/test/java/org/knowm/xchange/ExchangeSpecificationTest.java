/*
 * The MIT License
 *
 * Copyright 2019 Knowm Inc..
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.knowm.xchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.Test;

public class ExchangeSpecificationTest {

  @Test
  public void toStringOmitsCredentialsAndUnstructuredConfiguration() {
    ExchangeSpecification specification = new ExchangeSpecification(Exchange.class);
    specification.setExchangeName("Synthetic Exchange");
    specification.setUserName("synthetic-user");
    specification.setPassword("synthetic-password");
    specification.setSecretKey("synthetic-secret");
    specification.setApiKey("synthetic-api-key");
    specification.setSslUri("https://synthetic-user:synthetic-password@example.invalid");
    specification.setPlainTextUri("http://example.invalid?token=synthetic-token");
    specification.setOverrideWebsocketApiUri("wss://example.invalid/synthetic-listen-key");
    specification.setExchangeSpecificParametersItem(
        "nested", Map.of("passphrase", "synthetic-passphrase"));
    specification.setExchangeSpecificParametersItem(
        "opaque",
        new Object() {
          @Override
          public String toString() {
            throw new AssertionError("Configuration values must not be rendered");
          }
        });

    assertThat(specification.toString())
        .contains("exchangeClass=interface org.knowm.xchange.Exchange", "Synthetic Exchange")
        .doesNotContain(
            "synthetic-user",
            "synthetic-password",
            "synthetic-secret",
            "synthetic-api-key",
            "synthetic-token",
            "synthetic-listen-key",
            "synthetic-passphrase",
            "exchangeSpecificParameters");
    assertThat(specification.getApiKey()).isEqualTo("synthetic-api-key");
    assertThat(specification.getSecretKey()).isEqualTo("synthetic-secret");
    assertThat(specification.getExchangeSpecificParametersItem("nested"))
        .isEqualTo(Map.of("passphrase", "synthetic-passphrase"));
  }
}
