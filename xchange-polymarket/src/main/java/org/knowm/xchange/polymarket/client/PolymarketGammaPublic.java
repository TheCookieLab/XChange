package org.knowm.xchange.polymarket.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;

/** Public Gamma API discovery endpoints. */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PolymarketGammaPublic {

  /** Lists markets with offset pagination; filter to active, non-closed markets for discovery. */
  @GET
  @Path("markets")
  List<PolymarketGammaMarket> getMarkets(
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset,
      @QueryParam("active") Boolean active,
      @QueryParam("closed") Boolean closed)
      throws IOException;
}
