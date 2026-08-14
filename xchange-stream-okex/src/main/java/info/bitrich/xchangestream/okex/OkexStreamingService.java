package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.okx.OkxStreamingService;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Legacy streaming transport service retained so downstream code that constructs it directly or
 * references its public channel constants (for example {@link #ORDERBOOK_BBO_TBT}) keeps compiling
 * during the migration grace period.
 *
 * @deprecated use {@link OkxStreamingService} instead.
 */
@Deprecated
public class OkexStreamingService extends OkxStreamingService {

  public OkexStreamingService(String apiUrl, ExchangeSpecification exchangeSpecification) {
    super(apiUrl, exchangeSpecification);
  }
}
