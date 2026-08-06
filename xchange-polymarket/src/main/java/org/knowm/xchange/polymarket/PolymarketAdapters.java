package org.knowm.xchange.polymarket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.polymarket.client.PolymarketEip712Signer;
import org.knowm.xchange.polymarket.dto.account.PolymarketBalanceResponse;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataPosition;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataTrade;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse.PolymarketBookLevel;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOpenOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOrderFlags;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketUserTrade;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Conversions between Polymarket wire DTOs and generic XChange DTOs.
 *
 * <p>Named provider rules (each enforced by an adapter test):
 *
 * <ul>
 *   <li>{@link #RULE_TOKEN_DIRECT} — generic {@code BID} maps to CLOB {@code BUY} on the outcome
 *       token carried as the contract's outcome id, {@code ASK} maps to {@code SELL}, at the
 *       quoted price in dollars per share.
 *   <li>{@link #RULE_AMOUNT_ENCODING} — maker/taker amounts are 6-decimal fixed-point integer
 *       strings; BUY posts pUSD notional (size × price) as maker amount, SELL posts shares.
 *   <li>{@link #RULE_QUANTITY_ENCODING} — read-side order/fill quantities are also 6-decimal
 *       fixed-point micro-units ({@code 100000000} = 100 shares); prices stay decimal dollars.
 *   <li>{@link #RULE_MAKER_TAKER} — a user fill belongs to the user's own order: the taker leg for
 *       {@code TAKER} rows, one fill per {@code maker_orders} entry owned by the account for
 *       {@code MAKER} rows.
 *   <li>{@link #RULE_NO_COMPLEMENT} — outcome tokens are never silently complemented: every CLOB
 *       record adapts to the contract of the token it actually references.
 * </ul>
 */
public final class PolymarketAdapters {

  /** Prediction-market provider id used in every Polymarket {@link PredictionMarketContract}. */
  public static final String PROVIDER = "polymarket";

  /** Quote collateral of every Polymarket contract and wallet balance: pUSD. */
  public static final Currency COLLATERAL = Currency.PUSD;

  /** Named provider rule: side mapping for placement and reads. */
  public static final String RULE_TOKEN_DIRECT =
      "Polymarket CLOB BUY on the contract's outcome token maps to generic BID, SELL to ASK,"
          + " at the quoted price in dollars per share.";

  /** Named provider rule: 6-decimal fixed-point maker/taker amount encoding. */
  public static final String RULE_AMOUNT_ENCODING =
      "Polymarket maker/taker amounts are 6-decimal fixed-point micro-units: BUY posts pUSD"
          + " notional (size x price) as makerAmount with shares as takerAmount; SELL posts"
          + " shares as makerAmount with pUSD notional as takerAmount; half-up rounding.";

  /** Named provider rule: 6-decimal fixed-point decoding of read-side order/fill quantities. */
  public static final String RULE_QUANTITY_ENCODING =
      "Polymarket /data/order(s) and /data/trades quantities (original_size, size_matched, size,"
          + " matched_amount) are 6-decimal fixed-point micro-units: 100000000 represents 100"
          + " shares; prices are decimal dollars per share.";

  /** Named provider rule: maker/taker attribution of user fills. */
  public static final String RULE_MAKER_TAKER =
      "A Polymarket user fill is attributed to the user's own order: a TAKER row uses"
          + " taker_order_id and the row size; a MAKER row yields one fill per maker_orders entry"
          + " whose maker_address is the configured account.";

  /** Named provider rule: complement tokens are addressed, never substituted. */
  public static final String RULE_NO_COMPLEMENT =
      "Polymarket outcome tokens are never silently complemented: a CLOB record adapts to the"
          + " contract whose outcomeId is the record's asset_id.";

  private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Condition id → negative-risk flag learned from the Gamma catalog and CLOB books. The generic
   * {@link PredictionMarketContract} cannot carry the market type, so discovery records it here
   * and order placement reads it to select the EIP-712 verifying contract.
   */
  private static final Map<String, Boolean> NEG_RISK_BY_CONDITION = new ConcurrentHashMap<>();

  private PolymarketAdapters() {}

  /** Resets the negative-risk registry; test seam. */
  static void resetNegRiskRegistry() {
    NEG_RISK_BY_CONDITION.clear();
  }

  /**
   * Negative-risk flag recorded for a condition, or {@code null} when the market has not been seen
   * by discovery ({@code remoteInit()}) or an order-book fetch.
   */
  public static Boolean negRiskForCondition(String conditionId) {
    return NEG_RISK_BY_CONDITION.get(conditionId);
  }

  /**
   * Parses the stringified JSON array of CLOB token ids on a Gamma market.
   *
   * @param market Gamma market record
   * @return token ids in outcome order; index 0 is the primary outcome
   */
  public static List<String> tokenIds(PolymarketGammaMarket market) {
    if (market.clobTokenIds() == null || market.clobTokenIds().isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(market.clobTokenIds(), new TypeReference<List<String>>() {});
    } catch (IOException e) {
      throw new ExchangeException(
          "Polymarket market " + market.conditionId() + " has unparseable clobTokenIds", e);
    }
  }

  /**
   * Builds the generic contract for one outcome of a Gamma market.
   *
   * @param market Gamma market record
   * @param outcomeIndex index into the outcomes/token-id arrays (0 = primary outcome)
   * @return prediction-market contract quoted in pUSD (Polymarket collateral)
   */
  public static PredictionMarketContract adaptContract(
      PolymarketGammaMarket market, int outcomeIndex) {
    List<String> tokenIds = tokenIds(market);
    if (outcomeIndex < 0 || outcomeIndex >= tokenIds.size()) {
      throw new IllegalArgumentException(
          "Outcome index " + outcomeIndex + " out of range for market " + market.conditionId());
    }
    if (market.conditionId() != null && market.negRisk() != null) {
      NEG_RISK_BY_CONDITION.put(market.conditionId(), market.negRisk());
    }
    return new PredictionMarketContract(
        PROVIDER, null, market.conditionId(), tokenIds.get(outcomeIndex), COLLATERAL);
  }

  /**
   * Builds the generic contract for a bare condition/token pair (read-side records).
   *
   * @param conditionId condition id ({@code 0x} hex)
   * @param tokenId CLOB outcome-token id
   */
  public static PredictionMarketContract contractForToken(String conditionId, String tokenId) {
    return new PredictionMarketContract(PROVIDER, null, conditionId, tokenId, COLLATERAL);
  }

  /**
   * Extracts and validates the CLOB token id from a generic instrument.
   *
   * @param instrument generic instrument; must be a Polymarket {@link PredictionMarketContract}
   * @return the outcome-token id
   */
  public static String tokenId(Instrument instrument) {
    if (!(instrument instanceof PredictionMarketContract contract)
        || !PROVIDER.equals(contract.getProvider())) {
      throw new InstrumentNotValidException(
          "Polymarket services require a PredictionMarketContract with provider 'polymarket': "
              + instrument);
    }
    return contract.getOutcomeId();
  }

  /** Condition id of a validated Polymarket instrument. */
  public static String conditionId(Instrument instrument) {
    tokenId(instrument);
    return ((PredictionMarketContract) instrument).getMarketId();
  }

  /** Adapts Gamma market attributes to exchange metadata. */
  public static InstrumentMetaData adaptMetadata(PolymarketGammaMarket market) {
    return InstrumentMetaData.builder()
        .priceScale(4)
        .volumeScale(6)
        .priceStepSize(market.orderPriceMinTickSize())
        .minimumAmount(market.orderMinSize())
        .contractValue(BigDecimal.ONE)
        .tradingFeeCurrency(COLLATERAL)
        .build();
  }

  /** Adapts a CLOB book to generic depth; levels arrive worst-first and are re-sorted. */
  public static OrderBook adaptOrderBook(PolymarketBookResponse book) {
    if (book.market() != null && book.negRisk() != null) {
      NEG_RISK_BY_CONDITION.put(book.market(), book.negRisk());
    }
    PredictionMarketContract contract = contractForToken(book.market(), book.assetId());
    List<LimitOrder> bids = new ArrayList<>();
    List<LimitOrder> asks = new ArrayList<>();
    if (book.bids() != null) {
      for (PolymarketBookLevel level : book.bids()) {
        bids.add(level(contract, OrderType.BID, level));
      }
    }
    if (book.asks() != null) {
      for (PolymarketBookLevel level : book.asks()) {
        asks.add(level(contract, OrderType.ASK, level));
      }
    }
    bids.sort(Comparator.comparing(LimitOrder::getLimitPrice).reversed());
    asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
    Date timestamp =
        book.timestamp() == null || book.timestamp().isBlank()
            ? null
            : new Date(Long.parseLong(book.timestamp()));
    return new OrderBook(timestamp, asks, bids);
  }

  /** Adapts a CLOB book's top of book to a generic ticker. */
  public static Ticker adaptTicker(PolymarketBookResponse book) {
    OrderBook orderBook = adaptOrderBook(book);
    Ticker.Builder builder =
        new Ticker.Builder().instrument(contractForToken(book.market(), book.assetId()));
    if (!orderBook.getBids().isEmpty()) {
      builder.bid(orderBook.getBids().get(0).getLimitPrice());
    }
    if (!orderBook.getAsks().isEmpty()) {
      builder.ask(orderBook.getAsks().get(0).getLimitPrice());
    }
    return builder.build();
  }

  /** Adapts public Data-API trades; {@code SELL} reads as an ask-side aggressor. */
  public static Trades adaptTrades(List<PolymarketDataTrade> trades) {
    List<Trade> adapted = new ArrayList<>();
    for (PolymarketDataTrade trade : trades) {
      adapted.add(
          Trade.builder()
              .type("SELL".equalsIgnoreCase(trade.side()) ? OrderType.ASK : OrderType.BID)
              .originalAmount(trade.size())
              .instrument(contractForToken(trade.conditionId(), trade.asset()))
              .price(trade.price())
              .timestamp(trade.timestamp() == null ? null : new Date(trade.timestamp() * 1000L))
              .id(trade.transactionHash())
              .build());
    }
    return new Trades(adapted);
  }

  /**
   * Builds the unsigned CLOB order for a generic limit order, applying {@link #RULE_TOKEN_DIRECT}
   * and {@link #RULE_AMOUNT_ENCODING}.
   *
   * @param order generic limit order on a Polymarket contract
   * @param walletAddress EOA address used as maker and signer
   * @param salt caller-supplied salt scoping retry identity
   * @param timestampMs creation time in unix milliseconds
   * @param negRisk whether the market settles on the NegRisk CTF Exchange; recorded on the order
   *     so the signer selects the matching EIP-712 verifying contract
   * @param signatureType CLOB signature strategy; only {@link
   *     PolymarketEip712Signer#SIGNATURE_TYPE_EOA} is implemented — anything else fails fast
   *     before an order can reach submission
   * @return unsigned order ready for {@code PolymarketEip712Signer.signOrder}
   */
  public static PolymarketSignedOrder toSignedOrder(
      LimitOrder order,
      String walletAddress,
      BigDecimal salt,
      long timestampMs,
      boolean negRisk,
      int signatureType) {
    if (signatureType != PolymarketEip712Signer.SIGNATURE_TYPE_EOA) {
      throw new NotAvailableFromExchangeException(
          "Polymarket order signing implements only EOA signatures (signature type "
              + PolymarketEip712Signer.SIGNATURE_TYPE_EOA
              + "); proxy ("
              + PolymarketEip712Signer.SIGNATURE_TYPE_POLY_PROXY
              + "), Gnosis Safe ("
              + PolymarketEip712Signer.SIGNATURE_TYPE_POLY_GNOSIS_SAFE
              + "), and EIP-1271 ("
              + PolymarketEip712Signer.SIGNATURE_TYPE_POLY_1271
              + ") wallet strategies are not supported.");
    }
    String tokenId = tokenId(order.getInstrument());
    BigDecimal price = order.getLimitPrice();
    if (price == null
        || price.compareTo(BigDecimal.ZERO) <= 0
        || price.compareTo(BigDecimal.ONE) >= 0
        || price.scale() > 4) {
      throw new IllegalArgumentException(
          "Polymarket limit price must be between 0 and 1 dollars exclusive with at most 4"
              + " decimal places: "
              + price);
    }
    BigDecimal size = order.getOriginalAmount();
    if (size == null || size.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Polymarket order size must be positive: " + size);
    }
    BigDecimal shareMicros = size.multiply(MICRO).setScale(0, RoundingMode.HALF_UP);
    BigDecimal pusdMicros =
        size.multiply(price).multiply(MICRO).setScale(0, RoundingMode.HALF_UP);
    boolean buy = order.getType() == OrderType.BID;
    return new PolymarketSignedOrder(
        salt.toBigInteger().toString(),
        walletAddress,
        walletAddress,
        tokenId,
        (buy ? pusdMicros : shareMicros).toPlainString(),
        (buy ? shareMicros : pusdMicros).toPlainString(),
        buy ? "BUY" : "SELL",
        "0",
        BigDecimal.valueOf(timestampMs).toBigInteger().toString(),
        PolymarketEip712Signer.SIGNATURE_TYPE_EOA,
        null,
        "0x" + "00".repeat(32),
        null,
        negRisk);
  }

  /** Maps the order type flag set to the CLOB order-type string. */
  public static String toOrderType(LimitOrder order) {
    if (order.hasFlag(PolymarketOrderFlags.FILL_OR_KILL)) {
      return "FOK";
    }
    if (order.hasFlag(PolymarketOrderFlags.IMMEDIATE_OR_CANCEL)) {
      return "FAK";
    }
    return "GTC";
  }

  /**
   * Maps a CLOB order record to a generic limit order per {@link #RULE_NO_COMPLEMENT} and {@link
   * #RULE_QUANTITY_ENCODING}.
   */
  public static LimitOrder adaptOrder(PolymarketOpenOrder order) {
    LimitOrder.Builder builder =
        new LimitOrder.Builder(
                "SELL".equalsIgnoreCase(order.side()) ? OrderType.ASK : OrderType.BID,
                contractForToken(order.market(), order.assetId()))
            .originalAmount(decodeMicros(order.originalSize()))
            .limitPrice(parseDecimal(order.price()))
            .id(order.id())
            .timestamp(parseEpochSeconds(order.createdAt()))
            .orderStatus(adaptOrderStatus(order.status(), order.sizeMatched()));
    BigDecimal matched = decodeMicros(order.sizeMatched());
    if (matched != null && matched.compareTo(BigDecimal.ZERO) > 0) {
      builder.cumulativeAmount(matched);
    }
    return builder.build();
  }

  /**
   * Maps a CLOB user fill to the generic user trades of the user's own orders per {@link
   * #RULE_MAKER_TAKER}, {@link #RULE_NO_COMPLEMENT}, and {@link #RULE_QUANTITY_ENCODING}. A taker
   * fill is the single {@code taker_order_id} leg; a maker fill yields one trade per {@code
   * maker_orders} entry whose maker address is the configured account.
   *
   * @param trade CLOB fill
   * @param accountAddress the authenticated account's wallet address, used to attribute maker
   *     fills; required for maker rows
   * @return one user trade per fill on the user's own orders
   */
  public static List<UserTrade> adaptUserTrade(
      PolymarketUserTrade trade, String accountAddress) {
    String traderSide = trade.traderSide() == null ? "" : trade.traderSide().toUpperCase();
    if ("TAKER".equals(traderSide)) {
      // The user took liquidity: their own order is the taker leg.
      return List.of(
          userTrade(trade, trade.takerOrderId(), trade.side(), trade.size(), trade.price()));
    }
    if ("MAKER".equals(traderSide)) {
      List<UserTrade> fills = new ArrayList<>();
      if (trade.makerOrders() != null) {
        for (PolymarketUserTrade.MakerOrder maker : trade.makerOrders()) {
          if (maker.makerAddress() != null
              && maker.makerAddress().equalsIgnoreCase(accountAddress)) {
            fills.add(
                userTrade(
                    trade, maker.orderId(), maker.side(), maker.matchedAmount(), maker.price()));
          }
        }
      }
      if (fills.isEmpty()) {
        throw new ExchangeException(
            "Polymarket maker fill "
                + trade.id()
                + " has no maker_orders entry owned by account "
                + accountAddress);
      }
      return fills;
    }
    throw new ExchangeException(
        "Polymarket user trade has unrecognized trader_side: " + trade.traderSide());
  }

  /** Maps CLOB lifecycle status to the generic order status. */
  static OrderStatus adaptOrderStatus(String status, String sizeMatched) {
    String value = status == null ? "" : status.toUpperCase();
    if (value.startsWith("ORDER_STATUS_")) {
      value = value.substring("ORDER_STATUS_".length());
    }
    if ("LIVE".equals(value)) {
      BigDecimal matched = decodeMicros(sizeMatched);
      return matched != null && matched.compareTo(BigDecimal.ZERO) > 0
          ? OrderStatus.PARTIALLY_FILLED
          : OrderStatus.OPEN;
    }
    return switch (value) {
      case "MATCHED" -> OrderStatus.FILLED;
      case "CANCELED" -> OrderStatus.CANCELED;
      case "CANCELED_MARKET_RESOLVED" -> OrderStatus.CANCELED;
      case "DELAYED" -> OrderStatus.PENDING_NEW;
      case "INVALID" -> OrderStatus.REJECTED;
      default -> OrderStatus.UNKNOWN;
    };
  }

  /**
   * Adapts the collateral balance (6-decimal fixed-point pUSD micro-units, {@link
   * #RULE_QUANTITY_ENCODING}) to a single pUSD wallet.
   */
  public static AccountInfo adaptAccountInfo(PolymarketBalanceResponse balance) {
    BigDecimal available = decodeMicros(balance.balance());
    if (available == null) {
      available = BigDecimal.ZERO;
    }
    Wallet wallet =
        new Wallet(
            null, null, List.of(new Balance(COLLATERAL, available, available)), null, null, null);
    return new AccountInfo(wallet);
  }

  /** Adapts Data-API positions; every token position is LONG its own outcome contract. */
  public static List<OpenPosition> adaptPositions(List<PolymarketDataPosition> positions) {
    List<OpenPosition> adapted = new ArrayList<>();
    for (PolymarketDataPosition position : positions) {
      adapted.add(
          OpenPosition.builder()
              .instrument(contractForToken(position.conditionId(), position.asset()))
              .type(OpenPosition.Type.LONG)
              .size(position.size())
              .price(position.avgPrice())
              .build());
    }
    return adapted;
  }

  /**
   * Decodes a 6-decimal fixed-point quantity string (micro-units) to shares per {@link
   * #RULE_QUANTITY_ENCODING}: {@code "100000000"} → {@code 100}, {@code "100050000"} → {@code
   * 100.05}. Returns {@code null} for blank input.
   */
  public static BigDecimal decodeMicros(String microUnits) {
    if (microUnits == null || microUnits.isBlank()) {
      return null;
    }
    BigDecimal shares = new BigDecimal(microUnits).movePointLeft(6).stripTrailingZeros();
    // Integral quantities decode to scale 0 (100, not 1E+2) so equality against
    // plain integer BigDecimals holds; fractional quantities keep their decimals.
    return shares.scale() < 0 ? shares.setScale(0) : shares;
  }

  private static UserTrade userTrade(
      PolymarketUserTrade trade, String orderId, String side, String size, String price) {
    return UserTrade.builder()
        .type("SELL".equalsIgnoreCase(side) ? OrderType.ASK : OrderType.BID)
        .originalAmount(decodeMicros(size))
        .instrument(contractForToken(trade.market(), trade.assetId()))
        .price(parseDecimal(price))
        .timestamp(parseEpochSeconds(trade.matchTime()))
        .id(trade.id())
        .orderId(orderId)
        .build();
  }

  private static LimitOrder level(
      PredictionMarketContract contract, OrderType type, PolymarketBookLevel level) {
    return new LimitOrder.Builder(type, contract)
        .originalAmount(parseDecimal(level.size()))
        .limitPrice(parseDecimal(level.price()))
        .build();
  }

  private static BigDecimal parseDecimal(String value) {
    return value == null || value.isBlank() ? null : new BigDecimal(value);
  }

  private static Date parseEpochSeconds(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new Date(Long.parseLong(value) * 1000L);
    } catch (NumberFormatException notEpoch) {
      try {
        return Date.from(Instant.parse(value));
      } catch (RuntimeException unparseable) {
        return null;
      }
    }
  }
}
