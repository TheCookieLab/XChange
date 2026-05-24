package info.bitrich.xchangestream.coinsph;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinsph.dto.CoinsphWebSocketExecutionReport;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;

public class CoinsphStreamingTradeService implements StreamingTradeService {
  private final CoinsphStreamingService service;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  /** Constructor with just streaming service. */
  public CoinsphStreamingTradeService(CoinsphStreamingService service) {
    this.service = service;
  }

  /** Constructor with streaming service and trade service. */
  public CoinsphStreamingTradeService(
      CoinsphStreamingService service,
      org.knowm.xchange.service.trade.TradeService tradeService) {
    this.service = service;
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    // User data streams (executionReport) are typically not per-symbol for subscription in
    // Coins.ph/Binance style.
    // The listenKey subscribes to all user data. Filtering by currencyPair happens client-side.
    // The channel name for user data is often the listenKey itself or a generic name.
    // For now, assume CoinsphStreamingService handles user data stream subscription internally.
    // The channel name here might be a generic one like "executionReport".

    // Ensure user data stream is started (this might be done in CoinsphStreamingService or
    // Exchange)
    // service.startUserDataStreamIfNeeded(); // Example placeholder

    return service
        .subscribeChannel("executionReport") // Generic channel for all execution reports
        .map(node -> mapper.treeToValue(node, CoinsphWebSocketExecutionReport.class))
        .filter(
            report ->
                currencyPair == null
                    || CoinsphStreamingAdapters.getCurrencyPairFromSymbol(report.getSymbol())
                        .equals(currencyPair)) // To be created
        .map(CoinsphStreamingAdapters::adaptOrder);
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    // Similar to getOrderChanges, relies on a general user data stream.
    // service.startUserDataStreamIfNeeded(); // Example placeholder

    return service
        .subscribeChannel("executionReport") // Generic channel for all execution reports
        .map(node -> mapper.treeToValue(node, CoinsphWebSocketExecutionReport.class))
        .filter(report -> "TRADE".equalsIgnoreCase(report.getExecutionType())) // Only actual trades
        .filter(
            report ->
                currencyPair == null
                    || CoinsphStreamingAdapters.getCurrencyPairFromSymbol(report.getSymbol())
                        .equals(currencyPair))
        .map(CoinsphStreamingAdapters::adaptUserTrade);
  }

  // Note: getOrderChanges() and getUserTrades() without currencyPair will return updates for ALL
  // pairs.
}
