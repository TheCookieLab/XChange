package info.bitrich.xchangestream.kraken;

import static org.assertj.core.api.Assertions.assertThat;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

class KrakenStreamingMarketDataServiceIntegration extends KrakenStreamingExchangeIT {

  @Test
  void ticker() {
    Observable<Ticker> observable =
        exchange.getStreamingMarketDataService().getTicker(CurrencyPair.BTC_USD);

    TestObserver<Ticker> testObserver = observable.test();

    Ticker ticker = testObserver.awaitCount(1).values().get(0);

    testObserver.dispose();

    assertThat(ticker.getInstrument()).isEqualTo(CurrencyPair.BTC_USD);
    assertThat(ticker.getLast()).isNotNull();

    if (ticker.getBid().signum() > 0 && ticker.getAsk().signum() > 0) {
      assertThat(ticker.getBid()).isLessThan(ticker.getAsk());
    }
  }
}
