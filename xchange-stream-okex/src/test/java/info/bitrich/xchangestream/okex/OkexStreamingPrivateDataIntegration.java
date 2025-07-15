package info.bitrich.xchangestream.okex;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class OkexStreamingPrivateDataIntegration {

  private static final Logger LOG =
      LoggerFactory.getLogger(OkexStreamingPrivateDataIntegration.class);
  StreamingExchange exchange;
  private final Instrument instrument = new FuturesContract("BTC/USDT/SWAP");

  @Before
  public void setUp() {
    Properties properties = new Properties();

    try {
      properties.load(this.getClass().getResourceAsStream("/secret.keys"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // Enter your authentication details here to run private endpoint tests
    final String API_KEY =
        (properties.getProperty("apikey") == null)
            ? System.getenv("okx_apikey")
            : properties.getProperty("apikey");
    final String SECRET_KEY =
        (properties.getProperty("secret") == null)
            ? System.getenv("okx_secretkey")
            : properties.getProperty("secret");
    final String PASSPHRASE =
        (properties.getProperty("passphrase") == null)
            ? System.getenv("okx_passphrase")
            : properties.getProperty("passphrase");

    ExchangeSpecification spec = new OkexStreamingExchange().getDefaultExchangeSpecification();
    spec.setApiKey(API_KEY);
    spec.setSecretKey(SECRET_KEY);
    spec.setExchangeSpecificParametersItem(OkexExchange.PARAM_PASSPHRASE, PASSPHRASE);
    // for xchange-stream demo
    spec.setExchangeSpecificParametersItem(OkexExchange.USE_SANDBOX, true);
    // for REST demo
    spec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, "1");
    exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(OkexStreamingExchange.class);
    exchange.applySpecification(spec);
    exchange.connect().blockingAwait();
    // OPTION - wait for login message response
    while (!exchange.isAlive()) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  public void checkUserTradesStream() throws InterruptedException {
    Disposable dis =
        exchange
            .getStreamingTradeService()
            .getUserTrades(instrument)
            .subscribe(System.out::println);
    TimeUnit.SECONDS.sleep(3);
    dis.dispose();
  }

  @Test
  public void testOrderBook() throws InterruptedException {
    Disposable dis =
        exchange
            .getStreamingMarketDataService()
            .getOrderBook(instrument)
            .doOnError(throwable -> LOG.error("Error: ", throwable))
            .subscribe(
                orderBook -> {
                  LOG.info(".");
                  assertThat(orderBook.getBids().get(0).getLimitPrice())
                      .isLessThan(orderBook.getAsks().get(0).getLimitPrice());
                  assertThat(orderBook.getBids().get(0).getInstrument()).isEqualTo(instrument);
                },
                throwable -> LOG.error("Error: ", throwable));
    TimeUnit.SECONDS.sleep(3);
    dis.dispose();
  }
}
