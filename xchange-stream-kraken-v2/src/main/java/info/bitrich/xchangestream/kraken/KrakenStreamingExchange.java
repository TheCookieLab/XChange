package info.bitrich.xchangestream.kraken;

import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.kraken.config.Config;
import io.reactivex.rxjava3.core.Completable;
import lombok.Getter;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;

@Getter
public class KrakenStreamingExchange extends BaseExchange implements StreamingExchange {

  private KrakenStreamingService publicStreamingService;
  private StreamingMarketDataService streamingMarketDataService;
  private StreamingTradeService streamingTradeService;
  private StreamingAccountService streamingAccountService;

  @Override
  public Completable connect(ProductSubscription... args) {
    publicStreamingService = new KrakenStreamingService(Config.V2_PUBLIC_WS_URL);
    applyStreamingSpecification(exchangeSpecification, publicStreamingService);

    streamingMarketDataService = new KrakenStreamingMarketDataService(publicStreamingService);

    return publicStreamingService.connect();
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    var specification = new ExchangeSpecification(getClass());
    specification.setExchangeName("Kraken");
    specification.setShouldLoadRemoteMetaData(false);
    return specification;
  }

  @Override
  public Completable disconnect() {
    KrakenStreamingService service = publicStreamingService;
    publicStreamingService = null;
    streamingMarketDataService = null;
    streamingTradeService = null;
    streamingAccountService = null;
    return service.disconnect();
  }

  @Override
  public boolean isAlive() {
    return publicStreamingService != null && publicStreamingService.isSocketOpen();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    publicStreamingService.useCompressedMessages(compressedMessages);
  }

  @Override
  protected void initServices() {

  }
}
