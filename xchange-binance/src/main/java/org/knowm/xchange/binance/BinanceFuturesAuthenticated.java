package org.knowm.xchange.binance;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.knowm.xchange.binance.coinm.BinanceCoinmAuthApi;
import org.knowm.xchange.binance.portfoliomargin.BinancePortfolioMarginApi;
import org.knowm.xchange.binance.usdm.BinanceUsdmAuthApi;

/**
 * Legacy authenticated futures wire interface mixing USDⓈ-M, COIN-M, and Portfolio Margin
 * endpoints.
 *
 * @deprecated The Binance integration is split into explicit product families. Use {@link
 *     BinanceUsdmAuthApi}, {@link BinanceCoinmAuthApi}, and {@link BinancePortfolioMarginApi}
 *     directly. This facade is retained for source compatibility during the documented grace
 *     period; new code must use the family clients.
 */
@Deprecated
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BinanceFuturesAuthenticated
    extends BinanceFutures, BinanceUsdmAuthApi, BinanceCoinmAuthApi, BinancePortfolioMarginApi {}
