package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingExchange;
import info.bitrich.xchangestream.okx.OkxStreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingTradeService;

/**
 * @deprecated use {@link info.bitrich.xchangestream.okx.OkxStreamingExchange} instead.
 */
@Deprecated
public class OkexStreamingExchange extends OkxStreamingExchange {

  public OkexStreamingExchange() {}

  @Override
  public StreamingMarketDataService getStreamingMarketDataService() {
    OkxStreamingMarketDataService okxStreamingMarketDataService =
        (OkxStreamingMarketDataService) super.getStreamingMarketDataService();
    return okxStreamingMarketDataService == null
        ? null
        : new OkexStreamingMarketDataService(okxStreamingMarketDataService);
  }

  @Override
  public OkexStreamingTradeService getStreamingTradeService() {
    OkxStreamingTradeService okxStreamingTradeService =
        (OkxStreamingTradeService) super.getStreamingTradeService();
    return okxStreamingTradeService == null
        ? null
        : new OkexStreamingTradeService(okxStreamingTradeService);
  }
}
