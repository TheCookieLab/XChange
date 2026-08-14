package info.bitrich.xchangestream.okx;

import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.CANCEL_ORDER;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.CHANGE_ORDER;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.PLACE_ORDER;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.USER_ORDER_CHANGES;
import static info.bitrich.xchangestream.okx.OkxPrivateStreamingService.USER_POSITION_CHANGES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.github.resilience4j.rxjava3.ratelimiter.operator.RateLimiterOperator;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import org.knowm.xchange.service.trade.params.CancelOrderParams;

public class OkxStreamingTradeService implements StreamingTradeService {

  private final OkxPrivateStreamingService privateStreamingService;
  private final ExchangeMetaData exchangeMetaData;
  private final ResilienceRegistries resilienceRegistries;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  public OkxStreamingTradeService(
      OkxPrivateStreamingService privateStreamingService,
      ExchangeMetaData exchangeMetaData,
      ResilienceRegistries resilienceRegistries) {
    this.privateStreamingService = privateStreamingService;
    this.exchangeMetaData = exchangeMetaData;
    this.resilienceRegistries = resilienceRegistries;
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    String channelUniqueId = USER_ORDER_CHANGES + OkxAdapters.adaptInstrument(instrument);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxOrderDetails> okxOrderDetails =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxOrderDetails.class));
              return Observable.fromIterable(
                  OkxAdapters.adaptOrdersChanges(okxOrderDetails, exchangeMetaData));
            });
  }

  // cannot use OrderChanges and UserTrades together
  // leave it for backward compatibility, but it is not trade at all
  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    String channelUniqueId = USER_ORDER_CHANGES + OkxAdapters.adaptInstrument(instrument);

    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxOrderDetails> okxOrderDetails =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxOrderDetails.class));
              return Observable.fromIterable(
                  OkxAdapters.adaptUserTrades(okxOrderDetails, exchangeMetaData).getUserTrades());
            });
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    String channelUniqueId = USER_POSITION_CHANGES + OkxAdapters.adaptInstrument(instrument);
    return privateStreamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<OkxPosition> okxPositions =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, OkxPosition.class));
              return Observable.fromIterable(
                  OkxAdapters.adaptOpenPositions(okxPositions, exchangeMetaData)
                      .getOpenPositions());
            });
  }

  @Override
  public Single<Integer> placeLimitOrder(LimitOrder order, Object... args) {
    if (privateStreamingService.isLoginDone()) {
      Observable<Integer> observable =
          privateStreamingService
              .subscribeChannel(String.valueOf(System.nanoTime()), PLACE_ORDER, order)
              .flatMap(
                  node -> {
                    TypeReference<OkxResponse<List<OkxOrderResponse>>> typeReference =
                        new TypeReference<>() {};
                    OkxResponse<List<OkxOrderResponse>> response =
                        mapper.treeToValue(node, typeReference);
                    if (response.getCode().equals("0")) {
                      return Observable.just(0);
                    } else {
                      return Observable.just(Integer.parseInt(response.getData().get(0).getCode()));
                    }
                  });
      return observable
          .firstOrError()
          .compose(
              RateLimiterOperator.of(
                  resilienceRegistries
                      .rateLimiters()
                      .rateLimiter(OkxAuthenticated.placeOrderPath)));
    } else {
      throw new UnsupportedOperationException("privateStreamingService not authorized");
    }
  }

  @Override
  public Single<Integer> placeMarketOrder(MarketOrder order, Object... args) {
    if (privateStreamingService.isLoginDone()) {
      Observable<Integer> observable =
          privateStreamingService
              .subscribeChannel(String.valueOf(System.nanoTime()), PLACE_ORDER, order)
              .flatMap(
                  node -> {
                    TypeReference<OkxResponse<List<OkxOrderResponse>>> typeReference =
                        new TypeReference<>() {};
                    OkxResponse<List<OkxOrderResponse>> response =
                        mapper.treeToValue(node, typeReference);
                    if (response.getCode().equals("0")) {
                      return Observable.just(0);
                    } else {
                      return Observable.just(Integer.parseInt(response.getData().get(0).getCode()));
                    }
                  });
      return observable
          .firstOrError()
          .compose(
              RateLimiterOperator.of(
                  resilienceRegistries
                      .rateLimiters()
                      .rateLimiter(OkxAuthenticated.placeOrderPath)));
    } else {
      throw new UnsupportedOperationException("privateStreamingService not authorized");
    }
  }

  @Override
  public Single<Integer> changeOrder(LimitOrder order, Object... args) {
    if (privateStreamingService.isLoginDone()) {
      Observable<Integer> observable =
          privateStreamingService
              .subscribeChannel(String.valueOf(System.nanoTime()), CHANGE_ORDER, order)
              .flatMap(
                  node -> {
                    TypeReference<OkxResponse<List<OkxOrderResponse>>> typeReference =
                        new TypeReference<>() {};
                    OkxResponse<List<OkxOrderResponse>> response =
                        mapper.treeToValue(node, typeReference);
                    if (response.getCode().equals("0")) {
                      return Observable.just(0);
                    } else {
                      return Observable.just(Integer.parseInt(response.getData().get(0).getCode()));
                    }
                  });
      return observable
          .firstOrError()
          .compose(
              RateLimiterOperator.of(
                  resilienceRegistries
                      .rateLimiters()
                      .rateLimiter(OkxAuthenticated.amendOrderPath)));
    } else {
      throw new UnsupportedOperationException("privateStreamingService not authorized");
    }
  }

  @Override
  public Single<Integer> cancelOrder(CancelOrderParams params, Object... args) {
    if (privateStreamingService.isLoginDone()) {
      Observable<Integer> observable =
          privateStreamingService
              .subscribeChannel(String.valueOf(System.nanoTime()), CANCEL_ORDER, params)
              .flatMap(
                  node -> {
                    TypeReference<OkxResponse<List<OkxOrderResponse>>> typeReference =
                        new TypeReference<>() {};
                    OkxResponse<List<OkxOrderResponse>> response =
                        mapper.treeToValue(node, typeReference);
                    if (response.getCode().equals("0")) {
                      return Observable.just(0);
                    } else {
                      return Observable.just(Integer.parseInt(response.getData().get(0).getCode()));
                    }
                  });
      return observable
          .firstOrError()
          .compose(
              RateLimiterOperator.of(
                  resilienceRegistries
                      .rateLimiters()
                      .rateLimiter(OkxAuthenticated.cancelOrderPath)));
    } else {
      throw new UnsupportedOperationException("privateStreamingService not authorized");
    }
  }
}
