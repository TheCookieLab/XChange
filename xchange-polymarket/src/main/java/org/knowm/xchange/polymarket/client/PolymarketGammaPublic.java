package org.knowm.xchange.polymarket.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarketsKeysetResponse;

/** Public Gamma API discovery endpoints. */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface PolymarketGammaPublic {

  /**
   * Lists markets with offset pagination; filter to active, non-closed markets for discovery.
   *
   * <p>Deprecated by the provider (responses carry {@code deprecation} and {@code sunset} headers)
   * and capped at {@code offset <= 2000}: any larger offset fails with HTTP 422 {@code offset too
   * large, use /markets/keyset for deeper pagination}. The provider also ignores {@code active}
   * here, so callers filter on it themselves. Use {@link #getMarketsKeyset} to reach the whole
   * catalog.
   */
  @GET
  @Path("markets")
  List<PolymarketGammaMarket> getMarkets(
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset,
      @QueryParam("active") Boolean active,
      @QueryParam("closed") Boolean closed)
      throws IOException;

  /**
   * Lists markets with keyset pagination, which reaches the whole catalog where the deprecated
   * offset endpoint above stops at offset 2000. Results are ordered by immutable numeric market id
   * ascending (the provider default), so an {@code afterCursor} from a previous run finds markets
   * created since. The provider clamps {@code limit} to 100 and ignores {@code active}, so callers
   * filter on {@code active} themselves.
   *
   * @param afterCursor opaque cursor from a previous page's {@code next_cursor}, or {@code null} to
   *     start at the first market; an unknown cursor is rejected with HTTP 422
   */
  @GET
  @Path("markets/keyset")
  PolymarketGammaMarketsKeysetResponse getMarketsKeyset(
      @QueryParam("limit") Integer limit,
      @QueryParam("after_cursor") String afterCursor,
      @QueryParam("closed") Boolean closed)
      throws IOException;
}
