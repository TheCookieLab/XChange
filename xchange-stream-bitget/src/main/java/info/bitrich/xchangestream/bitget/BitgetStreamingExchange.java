package info.bitrich.xchangestream.bitget;

import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3PrivateStreamingService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingAccountService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingMarketDataService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingService;
import info.bitrich.xchangestream.bitget.uta.v3.BitgetUtaV3StreamingTradeService;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingAccountService;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Completable;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;

@Getter
public class BitgetStreamingExchange extends BitgetExchange implements StreamingExchange {

  private NettyStreamingService<?> publicStreamingService;
  private NettyStreamingService<?> privateStreamingService;
  private StreamingMarketDataService streamingMarketDataService;
  private StreamingTradeService streamingTradeService;
  private StreamingAccountService streamingAccountService;

  @Override
  public Completable connect(ProductSubscription... args) {
    switch (getApiMode()) {
      case CLASSIC_V2:
        return connectClassicV2();
      case UTA_V3:
        return connectUtaV3();
      default:
        throw new IllegalStateException("Unknown Bitget API mode: " + getApiMode());
    }
  }

  private Completable connectClassicV2() {
    publicStreamingService = new BitgetStreamingService(Config.V2_PUBLIC_WS_URL);
    if (StringUtils.isNoneBlank(
        exchangeSpecification.getApiKey(),
        exchangeSpecification.getSecretKey(),
        exchangeSpecification.getPassword())) {
      privateStreamingService =
          new BitgetPrivateStreamingService(
              Config.V2_PRIVATE_WS_URL,
              exchangeSpecification.getApiKey(),
              exchangeSpecification.getSecretKey(),
              exchangeSpecification.getPassword());
      streamingTradeService =
          new BitgetStreamingTradeService((BitgetPrivateStreamingService) privateStreamingService);
      privateStreamingService.connect().blockingAwait();
    }
    applyStreamingSpecification(exchangeSpecification, publicStreamingService);
    streamingMarketDataService =
        new BitgetStreamingMarketDataService((BitgetStreamingService) publicStreamingService);

    return publicStreamingService.connect();
  }

  private Completable connectUtaV3() {
    publicStreamingService = new BitgetUtaV3StreamingService(Config.V3_PUBLIC_WS_URL);
    applyStreamingSpecification(exchangeSpecification, publicStreamingService);
    streamingMarketDataService =
        new BitgetUtaV3StreamingMarketDataService(
            (BitgetUtaV3StreamingService) publicStreamingService);
    if (StringUtils.isNoneBlank(
        exchangeSpecification.getApiKey(),
        exchangeSpecification.getSecretKey(),
        exchangeSpecification.getPassword())) {
      privateStreamingService =
          new BitgetUtaV3PrivateStreamingService(
              Config.V3_PRIVATE_WS_URL,
              exchangeSpecification.getApiKey(),
              exchangeSpecification.getSecretKey(),
              exchangeSpecification.getPassword());
      // private channels must honor the same proxy/cert/connection-hook/auto-reconnect
      // settings as the public socket
      applyStreamingSpecification(exchangeSpecification, privateStreamingService);
      streamingTradeService =
          new BitgetUtaV3StreamingTradeService(
              (BitgetUtaV3PrivateStreamingService) privateStreamingService,
              (BitgetUtaV3TradeService) getTradeService());
      streamingAccountService =
          new BitgetUtaV3StreamingAccountService(
              (BitgetUtaV3PrivateStreamingService) privateStreamingService);
      return privateStreamingService.connect().andThen(publicStreamingService.connect());
    }
    return publicStreamingService.connect();
  }

  @Override
  public Completable disconnect() {
    NettyStreamingService<?> publicService = publicStreamingService;
    NettyStreamingService<?> privateService = privateStreamingService;
    if (publicService == null && privateService == null) {
      return Completable.complete();
    }
    if (publicService == null) {
      return privateService.disconnect();
    }
    if (privateService == null) {
      return publicService.disconnect();
    }
    return privateService.disconnect().andThen(publicService.disconnect());
  }

  @Override
  public boolean isAlive() {
    if (publicStreamingService == null) {
      return false;
    }
    if (privateStreamingService == null) {
      return publicStreamingService.isSocketOpen();
    }
    return publicStreamingService.isSocketOpen() && privateStreamingService.isSocketOpen();
  }

  @Override
  public void useCompressedMessages(boolean compressedMessages) {
    if (publicStreamingService != null) {
      publicStreamingService.useCompressedMessages(compressedMessages);
    }
    if (privateStreamingService != null) {
      privateStreamingService.useCompressedMessages(compressedMessages);
    }
  }
}
