package info.bitrich.xchangestream.kucoin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.kucoin.dto.uta.UtaOrderData;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.UtaAdapters;
import org.knowm.xchange.kucoin.uta.dto.UtaOrder;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderResult;
import org.knowm.xchange.kucoin.uta.dto.UtaTradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UTA private streaming trade service: order-change events over the private push socket and
 * WebSocket order placement/cancellation over the trading socket.
 *
 * <p>Private events are deduplicated by stable provider identity ({@code orderId + updatedTime}).
 * Placement/cancellation shares the REST unknown-outcome policy: a socket drop fails the pending
 * request explicitly and never resends it.
 */
public class UtaStreamingTradeService implements StreamingTradeService {

  private static final Logger LOG = LoggerFactory.getLogger(UtaStreamingTradeService.class);

  private final com.fasterxml.jackson.databind.ObjectMapper mapper =
      StreamingObjectMapperHelper.getObjectMapper();
  private final UtaStreamingService service;
  private final KucoinExchange exchange;
  private final Map<String, AtomicLong> lastSeenUpdate = new ConcurrentHashMap<>();

  public UtaStreamingTradeService(UtaStreamingService service, KucoinExchange exchange) {
    this.service = service;
    this.exchange = exchange;
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    String symbol = exchange.getUtaProviderSymbol(currencyPair);
    return getRawOrderChanges(symbol).map(UtaAdapters::adaptOrder);
  }

  /** Raw order events for a symbol; deduplicated by (orderId, updatedTime). */
  public Observable<UtaOrder> getRawOrderChanges(String symbol) {
    String tradeType = UtaTradeType.UNIFIED;
    return service
        .subscribeChannel("order", tradeType, symbol)
        .doOnError(ex -> LOG.warn("UTA order channel error for {}", symbol, ex))
        .map(node -> mapper.treeToValue(node, UtaOrderData.class))
        .filter(data -> data.getOi() != null && data.getOs() != null)
        .filter(
            data -> {
              String key = data.getOi();
              long updated = data.getU() == null ? 0L : data.getU();
              AtomicLong last = lastSeenUpdate.computeIfAbsent(key, k -> new AtomicLong());
              long previous = last.get();
              if (updated <= previous) {
                return false;
              }
              last.set(updated);
              return true;
            })
        .map(this::adaptRaw);
  }

  private UtaOrder adaptRaw(UtaOrderData d) {
    UtaOrder order = new UtaOrder();
    order.setOrderId(d.getOi());
    order.setClientOid(d.getCi());
    order.setStatus(d.getOs());
    order.setSymbol(d.getS());
    order.setOrderType(d.getOT());
    order.setSide(d.getS() == null ? null : "BUY".equalsIgnoreCase(d.getS()) ? "BUY" : "SELL");
    order.setSize(d.getQ());
    order.setPrice(d.getP());
    order.setSizeUnit(d.getQU());
    order.setAvgPrice(d.getAP());
    order.setFilledSize(d.getFS());
    order.setFee(d.getF());
    order.setFeeCurrency(d.getFC());
    order.setTimeInForce(d.getTIF());
    order.setPostOnly(d.getPO());
    order.setReduceOnly(d.getRO());
    order.setMarginMode(d.getMM());
    order.setPositionSide(d.getPS());
    order.setStp(d.getStp());
    order.setCancelReason(d.getCR());
    order.setTradeId(d.getTi());
    order.setTriggerOrderId(d.getToi());
    order.setOrderTime(d.getO());
    order.setUpdatedTime(d.getU());
    return order;
  }


  /** Places an order over the WebSocket trading socket (UTA mode). */
  public UtaOrderResult placeOrderWs(
      String tradeType, String symbol, String clientOid, String side, String orderType,
      String size, String sizeUnit, String price) throws IOException {
    return trading().placeOrder(args(tradeType, symbol, clientOid, side, orderType, size, sizeUnit, price));
  }

  /** Cancels an order over the WebSocket trading socket (UTA mode). */
  public UtaOrderResult cancelOrderWs(String tradeType, String symbol, String orderId, String clientOid)
      throws IOException {
    ObjectNode args = mapper.createObjectNode();
    args.put("tradeType", tradeType);
    args.put("symbol", symbol);
    if (orderId != null) {
      args.put("orderId", orderId);
    }
    if (clientOid != null) {
      args.put("clientOid", clientOid);
    }
    return trading().cancelOrder(args);
  }

  private ObjectNode args(
      String tradeType, String symbol, String clientOid, String side, String orderType,
      String size, String sizeUnit, String price) {
    ObjectNode args = mapper.createObjectNode();
    args.put("tradeType", tradeType);
    args.put("symbol", symbol);
    args.put("clientOid", clientOid);
    args.put("side", side);
    args.put("orderType", orderType);
    args.put("size", size);
    if (sizeUnit != null) {
      args.put("sizeUnit", sizeUnit);
    }
    if (price != null) {
      args.put("price", price);
    }
    return args;
  }

  private volatile UtaStreamingTradingService tradingService;

  private UtaStreamingTradingService trading() throws IOException {
    UtaStreamingTradingService local = tradingService;
    if (local == null) {
      synchronized (this) {
        local = tradingService;
        if (local == null) {
          local = new UtaStreamingTradingService(exchange);
          tradingService = local;
        }
      }
    }
    return local;
  }

  /** Connects the trading socket; idempotent. */
  public io.reactivex.rxjava3.core.Completable connectTrading() {
    try {
      return trading().connect().doOnError(e -> tradingService = null);
    } catch (IOException e) {
      return io.reactivex.rxjava3.core.Completable.error(e);
    }
  }

  /** Disconnects the trading socket; idempotent and null-safe. */
  public io.reactivex.rxjava3.core.Completable disconnectTrading() {
    UtaStreamingTradingService local = tradingService;
    if (local == null) {
      return io.reactivex.rxjava3.core.Completable.complete();
    }
    tradingService = null;
    return local.disconnect();
  }

  /** @return the provider symbol used for the given instrument in this service */
  public String providerSymbol(Instrument instrument) {
    return exchange.getUtaProviderSymbol(instrument);
  }
}
