package info.bitrich.xchangestream.krakenfutures;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.krakenfutures.dto.KrakenFuturesStreamingFillsDeltaResponse;
import info.bitrich.xchangestream.krakenfutures.dto.KrakenFuturesStreamingFillsDeltaResponse.KrakenFuturesStreamingFill;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

@Slf4j
public class KrakenFuturesStreamingTradeService implements StreamingTradeService {

  private static final String FILLS_CHANNEL = "fills";

  private final ObjectMapper objectMapper = StreamingObjectMapperHelper.getObjectMapper();
  private final Observable<List<UserTrade>> fills;

  /** Highest fill sequence seen; fills at or below this value are redeliveries and are dropped. */
  private long lastFillSeq = Long.MIN_VALUE;

  public KrakenFuturesStreamingTradeService(KrakenFuturesStreamingService streamingService) {
    fills =
        streamingService
            .subscribeChannel(streamingService.FILLS)
            .filter(message -> message.has("feed") && message.has("fills"))
            .filter(message -> message.get("feed").asText().equals("fills"))
            .map(
                message -> {
                  KrakenFuturesStreamingFillsDeltaResponse delta =
                      objectMapper.treeToValue(
                          message, KrakenFuturesStreamingFillsDeltaResponse.class);
                  List<KrakenFuturesStreamingFill> freshFills =
                      delta.getFills().stream().filter(this::isNewFill).toList();
                  if (freshFills.size() < delta.getFills().size()) {
                    log.debug(
                        "Dropped {} duplicate fills (last seq {})",
                        delta.getFills().size() - freshFills.size(),
                        lastFillSeq);
                  }
                  return KrakenFuturesStreamingAdapters.adaptUserTrades(
                      new KrakenFuturesStreamingFillsDeltaResponse(
                          delta.getFeed(), delta.getUsername(), freshFills));
                });
  }

  /**
   * Deduplicates fills redelivered after a reconnect: the provider's per-fill {@code seq} is a
   * monotonic counter, so a fill at or below the highest seen sequence is a duplicate.
   */
  private synchronized boolean isNewFill(KrakenFuturesStreamingFill fill) {
    Long seq = fill.getSeq();
    if (seq == null) {
      return true; // no sequence information; cannot deduplicate
    }
    if (seq <= lastFillSeq) {
      return false;
    }
    lastFillSeq = seq;
    return true;
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    return fills
        .flatMapIterable(userTrades -> userTrades)
        .filter(userTrade -> userTrade.getInstrument().equals(instrument));
  }

  @Override
  public Observable<UserTrade> getUserTrades() {
    return fills.flatMapIterable(userTrades -> userTrades);
  }
}
