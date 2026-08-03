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
 *       strings; BUY posts USDC notional (size × price) as maker amount, SELL posts shares.
 *   <li>{@link #RULE_NO_COMPLEMENT} — outcome tokens are never silently complemented: every CLOB
 *       record adapts to the contract of the token it actually references.
 * </ul>
 */
public final class PolymarketAdapters {

  /** Prediction-market provider id used in every Polymarket {@link PredictionMarketContract}. */
  public static final String PROVIDER = "polymarket";

  /** Named provider rule: side mapping for placement and reads. */
  public static final String RULE_TOKEN_DIRECT =
      "Polymarket CLOB BUY on the contract's outcome token maps to generic BID, SELL to ASK,"
          + " at the quoted price in dollars per share.";

  /** Named provider rule: 6-decimal fixed-point maker/taker amount encoding. */
  public static final String RULE_AMOUNT_ENCODING =
      "Polymarket maker/taker amounts are 6-decimal fixed-point micro-units: BUY posts USDC"
          + " notional (size x price) as makerAmount with shares as takerAmount; SELL posts"
          + " shares as makerAmount with USDC notional as takerAmount; half-up rounding.";

  /** Named provider rule: complement tokens are addressed, never substituted. */
  public static final String RULE_NO_COMPLEMENT =
      "Polymarket outcome tokens are never silently complemented: a CLOB record adapts to the"
          + " contract whose outcomeId is the record's asset_id.";

  private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PolymarketAdapters() {}

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
   * @return prediction-market contract quoted in USD (USDC collateral)
   */
  public static PredictionMarketContract adaptContract(
      PolymarketGammaMarket market, int outcomeIndex) {
    List<String> tokenIds = tokenIds(market);
    if (outcomeIndex < 0 || outcomeIndex >= tokenIds.size()) {
      throw new IllegalArgumentException(
          "Outcome index " + outcomeIndex + " out of range for market " + market.conditionId());
    }
    return new PredictionMarketContract(
        PROVIDER, null, market.conditionId(), tokenIds.get(outcomeIndex), Currency.USD);
  }

  /**
   * Builds the generic contract for a bare condition/token pair (read-side records).
   *
   * @param conditionId condition id ({@code 0x} hex)
   * @param tokenId CLOB outcome-token id
   */
  public static PredictionMarketContract contractForToken(String conditionId, String tokenId) {
    return new PredictionMarketContract(PROVIDER, null, conditionId, tokenId, Currency.USD);
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
        .build();
  }

  /** Adapts a CLOB book to generic depth; levels arrive worst-first and are re-sorted. */
  public static OrderBook adaptOrderBook(PolymarketBookResponse book) {
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
    Ticker.Builder builder = new Ticker.Builder().instrument(contractForToken(book.market(), book.assetId()));
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
   * @return unsigned order ready for {@code PolymarketEip712Signer.signOrder}
   */
  public static PolymarketSignedOrder toSignedOrder(
      LimitOrder order, String walletAddress, BigDecimal salt, long timestampMs) {
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
    BigDecimal usdcMicros =
        size.multiply(price).multiply(MICRO).setScale(0, RoundingMode.HALF_UP);
    boolean buy = order.getType() == OrderType.BID;
    return new PolymarketSignedOrder(
        salt.toBigInteger().toString(),
        walletAddress,
        walletAddress,
        tokenId,
        (buy ? usdcMicros : shareMicros).toPlainString(),
        (buy ? shareMicros : usdcMicros).toPlainString(),
        buy ? "BUY" : "SELL",
        "0",
        BigDecimal.valueOf(timestampMs).toBigInteger().toString(),
        PolymarketEip712SignerHolder.SIGNATURE_TYPE_EOA,
        null,
        "0x" + "00".repeat(32),
        null);
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

  /** Maps a CLOB order record to a generic limit order per {@link #RULE_NO_COMPLEMENT}. */
  public static LimitOrder adaptOrder(PolymarketOpenOrder order) {
    LimitOrder.Builder builder =
        new LimitOrder.Builder(
                "SELL".equalsIgnoreCase(order.side()) ? OrderType.ASK : OrderType.BID,
                contractForToken(order.market(), order.assetId()))
            .originalAmount(parseDecimal(order.originalSize()))
            .limitPrice(parseDecimal(order.price()))
            .id(order.id())
            .timestamp(parseEpochSeconds(order.createdAt()))
            .orderStatus(adaptOrderStatus(order.status(), order.sizeMatched()));
    BigDecimal matched = parseDecimal(order.sizeMatched());
    if (matched != null && matched.compareTo(BigDecimal.ZERO) > 0) {
      builder.cumulativeAmount(matched);
    }
    return builder.build();
  }

  /** Maps a CLOB user fill to a generic user trade per {@link #RULE_NO_COMPLEMENT}. */
  public static UserTrade adaptUserTrade(PolymarketUserTrade trade) {
    return UserTrade.builder()
        .type("SELL".equalsIgnoreCase(trade.side()) ? OrderType.ASK : OrderType.BID)
        .originalAmount(parseDecimal(trade.size()))
        .instrument(contractForToken(trade.market(), trade.assetId()))
        .price(parseDecimal(trade.price()))
        .timestamp(parseEpochSeconds(trade.matchTime()))
        .id(trade.id())
        .orderId(trade.takerOrderId())
        .build();
  }

  /** Maps CLOB lifecycle status to the generic order status. */
  static OrderStatus adaptOrderStatus(String status, String sizeMatched) {
    String value = status == null ? "" : status;
    return switch (value) {
      case "live" ->
          parseDecimal(sizeMatched) != null
                  && parseDecimal(sizeMatched).compareTo(BigDecimal.ZERO) > 0
              ? OrderStatus.PARTIALLY_FILLED
              : OrderStatus.OPEN;
      case "matched" -> OrderStatus.FILLED;
      case "canceled" -> OrderStatus.CANCELED;
      case "delayed" -> OrderStatus.PENDING_NEW;
      default -> OrderStatus.UNKNOWN;
    };
  }

  /** Adapts the collateral balance (6-decimal fixed-point USDC) to a single USD wallet. */
  public static AccountInfo adaptAccountInfo(PolymarketBalanceResponse balance) {
    BigDecimal available =
        balance.balance() == null
            ? BigDecimal.ZERO
            : new BigDecimal(balance.balance()).movePointLeft(6);
    Wallet wallet =
        new Wallet(
            null, null, List.of(new Balance(Currency.USD, available, available)), null, null, null);
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

  /** Indirection to the signature-type constant without a package cycle in javadoc. */
  private static final class PolymarketEip712SignerHolder {
    private static final int SIGNATURE_TYPE_EOA =
        org.knowm.xchange.polymarket.client.PolymarketEip712Signer.SIGNATURE_TYPE_EOA;
  }
}
