package info.bitrich.xchangestream.deribit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;

@Slf4j
public class DeribitStreamingTradeServiceIntegration extends DeribitStreamingExchangeIT {

  @BeforeAll
  public static void credentialsPresent() {
    // skip if there are no credentials
    assumeThat(exchange.getExchangeSpecification().getApiKey()).isNotEmpty();
    assumeThat(exchange.getExchangeSpecification().getSecretKey()).isNotEmpty();
  }

  @Test
  void user_trades_single_instrument() {
    Observable<UserTrade> observable = exchange.getStreamingTradeService().getUserTrades(new CurrencyPair("BTC/USDC"));

    TestObserver<UserTrade> testObserver = observable.test();

    List<UserTrade> userTrades = testObserver
//        .awaitDone(50, TimeUnit.SECONDS)
        .awaitCount(1)
        .values();

    testObserver.dispose();

    log.info("Received usertrades: {}", userTrades);

    assumeThat(userTrades).overridingErrorMessage("No trades happened").isNotEmpty();

    assertThat(userTrades.get(0).getInstrument()).isEqualTo(new CurrencyPair("BTC/USDC"));
    assertThat(userTrades.get(0).getId()).isNotNull();
    assertThat(userTrades.get(0).getOrderId()).isNotNull();
  }

}
