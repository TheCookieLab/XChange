package org.knowm.xchange.polymarket.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataPosition;
import org.knowm.xchange.polymarket.dto.data.PolymarketDataTrade;

/** Public Data API endpoints (trades and positions are readable by wallet address). */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PolymarketDataPublic {

  /** Recent public trades for a condition id. */
  @GET
  @Path("trades")
  List<PolymarketDataTrade> getTrades(
      @QueryParam("market") String conditionId, @QueryParam("limit") Integer limit)
      throws IOException;

  /** Open outcome-token positions of a wallet. */
  @GET
  @Path("positions")
  List<PolymarketDataPosition> getPositions(
      @QueryParam("user") String walletAddress,
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset)
      throws IOException;
}
