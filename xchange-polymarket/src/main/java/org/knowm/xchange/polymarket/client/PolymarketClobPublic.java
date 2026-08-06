package org.knowm.xchange.polymarket.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketBookResponse;
import org.knowm.xchange.polymarket.dto.marketdata.PolymarketPriceResponse;

/** Public Polymarket CLOB read endpoints (no credentials required). */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PolymarketClobPublic {

  /** Order book for one outcome token; levels are {@code price}/{@code size} dollar strings. */
  @GET
  @Path("book")
  PolymarketBookResponse getBook(@QueryParam("token_id") String tokenId) throws IOException;

  /** Current executable price for one side of one outcome token. */
  @GET
  @Path("price")
  PolymarketPriceResponse getPrice(
      @QueryParam("token_id") String tokenId, @QueryParam("side") String side) throws IOException;
}
