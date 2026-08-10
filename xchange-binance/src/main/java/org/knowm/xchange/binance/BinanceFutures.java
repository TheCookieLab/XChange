package org.knowm.xchange.binance;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.binance.dto.meta.BinanceSystemStatus;
import org.knowm.xchange.binance.usdm.BinanceUsdmApi;

/**
 * Legacy public futures wire interface.
 *
 * @deprecated The Binance integration is split into explicit product families. Use {@link
 *     BinanceUsdmApi} (public USDⓈ-M futures market data) directly. This facade is retained for
 *     source compatibility during the documented grace period.
 */
@Deprecated
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BinanceFutures extends BinanceUsdmApi {

  /**
   * Fetch system status which is normal or system maintenance.
   *
   * @deprecated System status belongs to the Wallet/SAPI family ({@link
   *     org.knowm.xchange.binance.wallet.BinanceWalletApi#systemStatus()}); this copy is retained
   *     for source compatibility.
   */
  @Deprecated
  @GET
  @Path("sapi/v1/system/status")
  BinanceSystemStatus systemStatus() throws IOException;
}
