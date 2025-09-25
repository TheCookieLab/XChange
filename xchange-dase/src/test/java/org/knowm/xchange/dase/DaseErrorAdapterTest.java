package org.knowm.xchange.dase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

public class DaseErrorAdapterTest {

  @Test
  public void unauthorized_maps_to_security() {
    ExchangeException ex = DaseErrorAdapter.adapt("Unauthorized", "nope");
    assertThat(ex).isInstanceOf(ExchangeSecurityException.class);
  }

  @Test
  public void insufficientFunds_maps_to_fundsExceeded() {
    ExchangeException ex = DaseErrorAdapter.adapt("InsufficientFunds", "insufficient");
    assertThat(ex).isInstanceOf(FundsExceededException.class);
  }

  @Test
  public void tooManyRequests_maps_to_rateLimit() {
    ExchangeException ex = DaseErrorAdapter.adapt("TooManyRequests", "slow down");
    assertThat(ex).isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  public void unknown_type_maps_to_generic() {
    ExchangeException ex = DaseErrorAdapter.adapt("Whatever", "msg");
    assertThat(ex).isInstanceOf(ExchangeException.class);
  }
}


