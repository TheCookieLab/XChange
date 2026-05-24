package org.knowm.xchange.okcoin.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

public class OkCoinBaseService extends BaseExchangeService implements BaseService {

  /** Set to true if international site should be used */
  protected final boolean useIntl;

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinBaseService(Exchange exchange) {

    super(exchange);

    useIntl =
        (Boolean)
            exchange.getExchangeSpecification().getExchangeSpecificParameters().get("Use_Intl");
  }

  protected String createDelimitedString(String[] items) {
    return items == null ? null : String.join(",", items);
  }
}
