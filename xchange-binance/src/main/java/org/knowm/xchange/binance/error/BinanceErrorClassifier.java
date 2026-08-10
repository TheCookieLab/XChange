package org.knowm.xchange.binance.error;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.binance.dto.BinanceException;

/**
 * Maps Binance error codes and HTTP statuses to a {@link BinanceRetryClassification}.
 *
 * <p>The mapping follows Binance's documented error catalog. Unknown codes default to {@link
 * BinanceRetryClassification#NO_RETRY}: an unrecognized provider rejection must not be blindly
 * retried, and an ambiguous placement outcome must be reconciled rather than replayed.
 */
public final class BinanceErrorClassifier {

  private static final Map<Integer, BinanceRetryClassification> BY_CODE = new ConcurrentHashMap<>();

  static {
    // Unknown/transport-level conditions (replay-safe operations may retry).
    put(-1000, BinanceRetryClassification.TRANSIENT); // unknown error
    put(-1001, BinanceRetryClassification.TRANSIENT); // disconnected
    put(-1006, BinanceRetryClassification.TRANSIENT); // unexpected response
    put(-1007, BinanceRetryClassification.TRANSIENT); // timeout
    put(-1008, BinanceRetryClassification.TRANSIENT); // server busy
    put(-1016, BinanceRetryClassification.TRANSIENT); // service shutting down
    put(-1021, BinanceRetryClassification.TRANSIENT); // invalid timestamp (resync and retry)

    // Rate limiting.
    put(-1003, BinanceRetryClassification.RATE_LIMITED); // too many requests
    put(-1015, BinanceRetryClassification.RATE_LIMITED); // too many orders
    put(-2025, BinanceRetryClassification.RATE_LIMITED); // max open orders
    put(-3002, BinanceRetryClassification.RATE_LIMITED); // SAPI too many requests

    // Authentication and authorization.
    put(-1002, BinanceRetryClassification.AUTHENTICATION); // unauthorized
    put(-1022, BinanceRetryClassification.AUTHENTICATION); // invalid signature
    put(-1132, BinanceRetryClassification.AUTHENTICATION); // API key header missing
    put(-1134, BinanceRetryClassification.AUTHENTICATION); // API key inactive
    put(-1136, BinanceRetryClassification.AUTHENTICATION); // no permission
    put(-2008, BinanceRetryClassification.AUTHENTICATION); // invalid key permissions
    put(-2014, BinanceRetryClassification.AUTHENTICATION); // bad API key format
    put(-2015, BinanceRetryClassification.AUTHENTICATION); // rejected API key

    // Everything else is a provider rejection: no blind retry.
    put(-1013, BinanceRetryClassification.NO_RETRY); // invalid message/order
    put(-1014, BinanceRetryClassification.NO_RETRY); // unknown order composition
    put(-1020, BinanceRetryClassification.NO_RETRY); // unsupported operation
    put(-1100, BinanceRetryClassification.NO_RETRY); // illegal characters
    put(-1101, BinanceRetryClassification.NO_RETRY); // too many parameters
    put(-1102, BinanceRetryClassification.NO_RETRY); // mandatory param missing
    put(-1103, BinanceRetryClassification.NO_RETRY); // unknown parameter
    put(-1104, BinanceRetryClassification.NO_RETRY); // unread parameters
    put(-1105, BinanceRetryClassification.NO_RETRY); // parameter empty
    put(-1106, BinanceRetryClassification.NO_RETRY); // parameter not required
    put(-1111, BinanceRetryClassification.NO_RETRY); // bad precision
    put(-1112, BinanceRetryClassification.NO_RETRY); // no depth
    put(-1114, BinanceRetryClassification.NO_RETRY); // TIF not required
    put(-1115, BinanceRetryClassification.NO_RETRY); // invalid TIF
    put(-1116, BinanceRetryClassification.NO_RETRY); // invalid order type
    put(-1117, BinanceRetryClassification.NO_RETRY); // invalid side
    put(-1118, BinanceRetryClassification.NO_RETRY); // empty new client order id
    put(-1119, BinanceRetryClassification.NO_RETRY); // empty orig client order id
    put(-1120, BinanceRetryClassification.NO_RETRY); // bad interval
    put(-1121, BinanceRetryClassification.NO_RETRY); // bad symbol
    put(-1122, BinanceRetryClassification.NO_RETRY); // invalid symbol status
    put(-1125, BinanceRetryClassification.NO_RETRY); // invalid listen key
    put(-1127, BinanceRetryClassification.NO_RETRY); // more than xx hours
    put(-1128, BinanceRetryClassification.NO_RETRY); // optional params bad combo
    put(-1130, BinanceRetryClassification.NO_RETRY); // invalid parameter
    put(-1131, BinanceRetryClassification.NO_RETRY); // bad API header format
    put(-1133, BinanceRetryClassification.NO_RETRY); // order header not in request
    put(-1135, BinanceRetryClassification.NO_RETRY); // bad recv window
    put(-2010, BinanceRetryClassification.NO_RETRY); // new order rejected
    put(-2011, BinanceRetryClassification.NO_RETRY); // cancel rejected
    put(-2013, BinanceRetryClassification.NO_RETRY); // no such order
    put(-2016, BinanceRetryClassification.NO_RETRY); // no trading window
    put(-2018, BinanceRetryClassification.NO_RETRY); // balance not sufficient
    put(-2019, BinanceRetryClassification.NO_RETRY); // margin insufficient
    put(-2020, BinanceRetryClassification.NO_RETRY); // unable to fill
    put(-2021, BinanceRetryClassification.NO_RETRY); // order would immediately trigger
    put(-2022, BinanceRetryClassification.NO_RETRY); // reduce-only rejected
    put(-2023, BinanceRetryClassification.NO_RETRY); // user in liquidation
    put(-2024, BinanceRetryClassification.NO_RETRY); // position not sufficient
    put(-2026, BinanceRetryClassification.NO_RETRY); // reduce-only order type not allowed
    put(-3000, BinanceRetryClassification.NO_RETRY); // invalid operation
    put(-3001, BinanceRetryClassification.NO_RETRY); // not applicable operation
  }

  private BinanceErrorClassifier() {}

  private static void put(int code, BinanceRetryClassification classification) {
    BY_CODE.put(code, classification);
  }

  /** Classifies a Binance API error by its error code. */
  public static BinanceRetryClassification classify(BinanceException exception) {
    if (exception == null) {
      return BinanceRetryClassification.NO_RETRY;
    }
    BinanceRetryClassification classification = BY_CODE.get(exception.getCode());
    if (classification != null) {
      return classification;
    }
    return classifyHttpStatus(exception.getHttpStatusCode());
  }

  /** Classifies a raw HTTP status when no Binance error payload is available. */
  public static BinanceRetryClassification classifyHttpStatus(int httpStatus) {
    if (httpStatus == 429 || httpStatus == 418) {
      return BinanceRetryClassification.RATE_LIMITED;
    }
    if (httpStatus >= 500 && httpStatus < 600) {
      return BinanceRetryClassification.TRANSIENT;
    }
    if (httpStatus == 401 || httpStatus == 403) {
      return BinanceRetryClassification.AUTHENTICATION;
    }
    return BinanceRetryClassification.NO_RETRY;
  }
}
