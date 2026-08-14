package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.okx.OkxPrivateStreamingService;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okex.OkexExchange;

/**
 * Legacy private streaming transport service retained so downstream code that constructs it
 * directly or references its public channel constants (for example {@link #USER_ORDER_CHANGES})
 * keeps compiling during the migration grace period.
 *
 * @deprecated use {@link OkxPrivateStreamingService} instead.
 */
@Deprecated
public class OkexPrivateStreamingService extends OkxPrivateStreamingService {

  public OkexPrivateStreamingService(
      String privateApiUrl,
      ExchangeSpecification exchangeSpecification,
      OkexExchange okexExchange) {
    super(privateApiUrl, exchangeSpecification, okexExchange);
  }
}
