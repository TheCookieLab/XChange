package info.bitrich.xchangestream.kraken;


import info.bitrich.xchangestream.kraken.dto.request.KrakenSubscribeMessage;
import info.bitrich.xchangestream.kraken.dto.request.KrakenUnsubscribeMessage;
import info.bitrich.xchangestream.kraken.dto.request.KrakenUnsubscribeMessage.Params;
import info.bitrich.xchangestream.kraken.dto.response.KrakenTickerMessage;
import lombok.experimental.UtilityClass;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

@UtilityClass
public class KrakenStreamingAdapters {

  public Ticker toTicker(KrakenTickerMessage.Payload payload) {

    return new Ticker.Builder()
        .instrument(payload.getCurrencyPair())
        .last(payload.getLastPrice())
        .bid(payload.getBestBidPrice())
        .bidSize(payload.getBestBidSize())
        .ask(payload.getBestAskPrice())
        .askSize(payload.getBestAskSize())
        .high(payload.getHigh24h())
        .low(payload.getLow24h())
        .volume(payload.getAssetVolume24h())
        .percentageChange(payload.getChangePercentage24h())
        .build();
  }

  /** Returns unique subscription id. Can be used as key for subscriptions caching */
  public String toSubscriptionUniqueId(String channelName, CurrencyPair currencyPair) {
    return channelName + "_" + currencyPair;
  }

  public KrakenSubscribeMessage toSubscribeMessage(String channelName, CurrencyPair currencyPair) {
    return KrakenSubscribeMessage.builder()
        .params(KrakenSubscribeMessage.Params.builder()
            .channel(channelName)
            .currencyPair(currencyPair)
            .build())
        .build();
  }

  public KrakenUnsubscribeMessage toUnsubscribeMessage(String subscriptionUniqueId) {
    var splitted = subscriptionUniqueId.split("_");
    var channelName = splitted[0];
    var currencyPair = new CurrencyPair(splitted[1]);

    return KrakenUnsubscribeMessage.builder()
        .params(Params.builder()
            .channel(channelName)
            .currencyPair(currencyPair)
            .build())
        .build();
  }

}
