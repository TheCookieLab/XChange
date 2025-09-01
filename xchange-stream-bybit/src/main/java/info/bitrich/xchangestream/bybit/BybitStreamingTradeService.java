package info.bitrich.xchangestream.bybit;

import static org.knowm.xchange.bybit.BybitResilience.ORDER_CREATE_LINEAR_AND_INVERSE_RATE_LIMITER;
import static org.knowm.xchange.bybit.BybitResilience.ORDER_CREATE_OPTION_LIMITER;
import static org.knowm.xchange.bybit.BybitResilience.ORDER_CREATE_SPOT_RATE_LIMITER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.trade.BybitComplexOrderChanges;
import dto.trade.BybitComplexPositionChanges;
import dto.trade.BybitOrderChangesResponse;
import dto.trade.BybitPositionChangesResponse;
import dto.trade.BybitStreamOrderResponse;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.rxjava3.ratelimiter.operator.RateLimiterOperator;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.knowm.xchange.bybit.BybitAdapters;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.service.BybitException;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BybitStreamingTradeService implements StreamingTradeService {

  private final Logger LOG = LoggerFactory.getLogger(BybitStreamingTradeService.class);
  private final BybitUserDataStreamingService streamingService;
  private final BybitUserTradeService userTradeService;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private final ResilienceRegistries resilienceRegistries;

  public BybitStreamingTradeService(BybitUserDataStreamingService streamingService,BybitUserTradeService userTradeService, ResilienceRegistries resilienceRegistries) {
    this.streamingService = streamingService;
    this.userTradeService =  userTradeService;
    this.resilienceRegistries = resilienceRegistries;
  }

  public Single<Boolean> placeMarketOrder(MarketOrder order) throws JsonProcessingException {
    BybitCategory category = BybitAdapters.getCategory(order.getInstrument());
    Observable<Boolean> observable = userTradeService.subscribeChannel("order.create"+System.nanoTime(), order)
        .flatMap(node -> {
          BybitStreamOrderResponse response = mapper.treeToValue(node, BybitStreamOrderResponse.class);
          if(response != null && response.getRetCode()==0){
          return Observable.just(true);
        } else {
            assert response != null;
            return Observable.error(new BybitException(response.getRetCode(),String.valueOf(response.getRetMsg()), null));
          }
        });
    return observable.firstElement()
        .compose(RateLimiterOperator.of(getCreateOrderRateLimiter(category))).toSingle();
  }

  @Override
  /*
   * instrument param is not used
   * arg[0] BybitCategory, if null then subscribe to all category
   */
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    String channelUniqueId = "order";
    if (args[0] != null && args[0] instanceof BybitCategory) {
      channelUniqueId += "." + ((BybitCategory) args[0]).getValue();
    }
    return streamingService
        .subscribeChannel(channelUniqueId)
        .flatMap(
            node -> {
              BybitOrderChangesResponse bybitOrderChangesResponse =
                  mapper.treeToValue(node, BybitOrderChangesResponse.class);
              return Observable.fromIterable(
                  BybitStreamAdapters.adaptOrdersChanges(bybitOrderChangesResponse.getData()));
            });
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair pair, Object... args) {
    return getOrderChanges((Instrument) pair, args);
  }

  public Observable<BybitComplexOrderChanges> getComplexOrderChanges(BybitCategory category) {
    String channelUniqueId = "order";
    if (category != null) {
      channelUniqueId += "." + category.getValue();
    }
    return streamingService
        .subscribeChannel(channelUniqueId)
        .flatMap(
            node -> {
              BybitOrderChangesResponse bybitOrderChangesResponse =
                  mapper.treeToValue(node, BybitOrderChangesResponse.class);
              return Observable.fromIterable(
                  BybitStreamAdapters.adaptComplexOrdersChanges(
                      bybitOrderChangesResponse.getData()));
            });
  }

  public Observable<OpenPosition> getPositionChanges(BybitCategory category) {
    String channelUniqueId = "position";
    if (category != null) {
      channelUniqueId += "." + category.getValue();
    }
    return streamingService
        .subscribeChannel(channelUniqueId)
        .flatMap(
            node -> {
              BybitPositionChangesResponse bybitPositionChangesResponse =
                  mapper.treeToValue(node, BybitPositionChangesResponse.class);
              return Observable.fromIterable(
                  BybitStreamAdapters.adaptPositionChanges(bybitPositionChangesResponse.getData())
                      .getOpenPositions());
            });
  }

  public Observable<BybitComplexPositionChanges> getBybitPositionChanges(BybitCategory category) {
    String channelUniqueId = "position";
    if (category != null) {
      channelUniqueId += "." + category.getValue();
    }
    return streamingService
        .subscribeChannel(channelUniqueId)
        .flatMap(
            node -> {
              BybitPositionChangesResponse bybitPositionChangesResponse =
                  mapper.treeToValue(node, BybitPositionChangesResponse.class);
              return Observable.fromIterable(
                  BybitStreamAdapters.adaptComplexPositionChanges(
                      bybitPositionChangesResponse.getData()));
            });
  }

  private RateLimiter getCreateOrderRateLimiter(BybitCategory category) {
    switch (category) {
      case LINEAR:
      case INVERSE:
        return resilienceRegistries.rateLimiters().rateLimiter(ORDER_CREATE_LINEAR_AND_INVERSE_RATE_LIMITER);
      case OPTION:
        return resilienceRegistries.rateLimiters().rateLimiter(ORDER_CREATE_OPTION_LIMITER);
      default: //SPOT
        return resilienceRegistries.rateLimiters().rateLimiter(ORDER_CREATE_SPOT_RATE_LIMITER);
    }
  }
}
