package org.knowm.xchange.binance;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.knowm.xchange.binance.spot.BinanceSpotAuthApi;
import org.knowm.xchange.binance.spot.BinanceSpotApi;
import org.knowm.xchange.binance.wallet.BinanceWalletApi;

/**
 * Legacy authenticated Binance wire interface mixing Spot, Wallet/SAPI, and system endpoints.
 *
 * @deprecated The Binance integration is split into explicit product families. Use {@link
 *     BinanceSpotApi}, {@link BinanceSpotAuthApi} (authenticated Spot), and {@link
 *     BinanceWalletApi} (Wallet/SAPI) directly. This facade is retained for source compatibility
 *     during the documented grace period; new code must use the family clients.
 */
@Deprecated
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BinanceAuthenticated extends Binance, BinanceSpotAuthApi {}
