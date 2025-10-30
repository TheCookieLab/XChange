package org.knowm.xchange.coinbase.v3.dto.portfolios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing a list of portfolios.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getportfolios">List Portfolios</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePortfoliosResponse {

  private final List<CoinbasePortfolio> portfolios;

  @JsonCreator
  public CoinbasePortfoliosResponse(
      @JsonProperty("portfolios") List<CoinbasePortfolio> portfolios) {
    this.portfolios = portfolios == null ? Collections.emptyList() : Collections.unmodifiableList(portfolios);
  }

  @Override
  public String toString() {
    return "CoinbasePortfoliosResponse [portfolios count=" + (portfolios == null ? 0 : portfolios.size()) + "]";
  }
}

