package org.knowm.xchange.binance.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.knowm.xchange.binance.dto.BinanceException;

public class BinanceErrorClassifierTest {

  private static BinanceRetryClassification classify(int code) {
    return BinanceErrorClassifier.classify(new BinanceException(code, "message"));
  }

  @Test
  public void testRateLimitCodes() {
    assertThat(classify(-1003)).isEqualTo(BinanceRetryClassification.RATE_LIMITED);
    assertThat(classify(-1015)).isEqualTo(BinanceRetryClassification.RATE_LIMITED);
    assertThat(classify(-2025)).isEqualTo(BinanceRetryClassification.RATE_LIMITED);
    assertThat(classify(-3002)).isEqualTo(BinanceRetryClassification.RATE_LIMITED);
  }

  @Test
  public void testAuthenticationCodes() {
    assertThat(classify(-1002)).isEqualTo(BinanceRetryClassification.AUTHENTICATION);
    assertThat(classify(-1022)).isEqualTo(BinanceRetryClassification.AUTHENTICATION);
    assertThat(classify(-2014)).isEqualTo(BinanceRetryClassification.AUTHENTICATION);
    assertThat(classify(-2015)).isEqualTo(BinanceRetryClassification.AUTHENTICATION);
  }

  @Test
  public void testTransientCodes() {
    assertThat(classify(-1000)).isEqualTo(BinanceRetryClassification.TRANSIENT);
    assertThat(classify(-1001)).isEqualTo(BinanceRetryClassification.TRANSIENT);
    assertThat(classify(-1007)).isEqualTo(BinanceRetryClassification.TRANSIENT);
    assertThat(classify(-1016)).isEqualTo(BinanceRetryClassification.TRANSIENT);
    assertThat(classify(-1021)).isEqualTo(BinanceRetryClassification.TRANSIENT);
  }

  @Test
  public void testOrderRejectionCodesAreNoRetry() {
    assertThat(classify(-1013)).isEqualTo(BinanceRetryClassification.NO_RETRY);
    assertThat(classify(-2010)).isEqualTo(BinanceRetryClassification.NO_RETRY);
    assertThat(classify(-2011)).isEqualTo(BinanceRetryClassification.NO_RETRY);
    assertThat(classify(-1121)).isEqualTo(BinanceRetryClassification.NO_RETRY);
  }

  @Test
  public void testUnknownCodeDefaultsToNoRetry() {
    assertThat(classify(-999999)).isEqualTo(BinanceRetryClassification.NO_RETRY);
    assertThat(BinanceErrorClassifier.classify(null))
        .isEqualTo(BinanceRetryClassification.NO_RETRY);
  }

  @Test
  public void testHttpStatusClassification() {
    assertThat(BinanceErrorClassifier.classifyHttpStatus(429))
        .isEqualTo(BinanceRetryClassification.RATE_LIMITED);
    assertThat(BinanceErrorClassifier.classifyHttpStatus(418))
        .isEqualTo(BinanceRetryClassification.RATE_LIMITED);
    assertThat(BinanceErrorClassifier.classifyHttpStatus(500))
        .isEqualTo(BinanceRetryClassification.TRANSIENT);
    assertThat(BinanceErrorClassifier.classifyHttpStatus(503))
        .isEqualTo(BinanceRetryClassification.TRANSIENT);
    assertThat(BinanceErrorClassifier.classifyHttpStatus(401))
        .isEqualTo(BinanceRetryClassification.AUTHENTICATION);
    assertThat(BinanceErrorClassifier.classifyHttpStatus(400))
        .isEqualTo(BinanceRetryClassification.NO_RETRY);
  }

  @Test
  public void testStructuredExceptionCarriesContextAndRedacts() {
    BinanceException cause = new BinanceException(-1003, "API-key format invalid");
    BinanceStructuredException exception =
        BinanceStructuredException.from(
            cause,
            org.knowm.xchange.binance.config.BinanceProductFamily.SPOT,
            "spot/orderPlacement",
            "myClientOrderId");

    assertThat(exception.getRetryClassification())
        .isEqualTo(BinanceRetryClassification.RATE_LIMITED);
    assertThat(exception.getProductFamily())
        .isEqualTo(org.knowm.xchange.binance.config.BinanceProductFamily.SPOT);
    assertThat(exception.getEndpoint()).isEqualTo("spot/orderPlacement");
    assertThat(exception.getClientOrderId()).isEqualTo("myClientOrderId");
    assertThat(exception.getCode()).isEqualTo(-1003);
    assertThat(exception).isInstanceOf(org.knowm.xchange.exceptions.ExchangeException.class);
  }
}
