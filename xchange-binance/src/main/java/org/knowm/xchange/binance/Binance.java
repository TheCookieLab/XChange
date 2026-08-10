package org.knowm.xchange.binance;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.knowm.xchange.binance.spot.BinanceSpotApi;
import org.knowm.xchange.binance.wallet.BinanceWalletApi;

/**
 * Legacy public Spot wire interface.
 *
 * @deprecated The Binance integration is split into explicit product families. Use {@link
 *     BinanceSpotApi} (public Spot market data) and {@link BinanceWalletApi} (system status and
 *     SAPI wallet endpoints) directly. This facade is retained for source compatibility during
 *     the documented grace period and delegates nothing on its own; services should migrate to
 *     the family clients.
 */
@Deprecated
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface Binance extends BinanceSpotApi, BinanceWalletApi {}
