package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.okx.OkxBusinessStreamingService;
import org.knowm.xchange.ExchangeSpecification;

/**
 * Legacy business streaming transport service retained for source and binary compatibility with
 * pre-rename clients.
 *
 * @deprecated use {@link OkxBusinessStreamingService} instead.
 */
@Deprecated
public class OkexBusinessStreamingService extends OkxBusinessStreamingService {

  public OkexBusinessStreamingService(String apiUrl, ExchangeSpecification exchangeSpecification) {
    super(apiUrl, exchangeSpecification);
  }
}
