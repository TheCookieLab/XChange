package org.knowm.xchange.deribit.v2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.deribit.v2.dto.DeribitError;
import org.knowm.xchange.deribit.v2.dto.DeribitException;
import org.knowm.xchange.exceptions.ExchangeException;

class DeribitAdaptersTest {

  @Test
  void adaptHandlesDeribitExceptionWithoutErrorPayload() {
    ExchangeException exchangeException = DeribitAdapters.adapt(new DeribitException(null));

    assertThat(exchangeException)
        .hasMessage("Operation failed without any error message")
        .hasCauseInstanceOf(DeribitException.class);
  }

  @Test
  void adaptHandlesDeribitErrorWithoutData() {
    DeribitError error = new DeribitError();
    error.setCode(-32000);
    error.setMessage("Gateway unavailable");

    ExchangeException exchangeException = DeribitAdapters.adapt(new DeribitException(error));

    assertThat(exchangeException)
        .hasMessage("Gateway unavailable")
        .hasCauseInstanceOf(DeribitException.class);
  }
}
