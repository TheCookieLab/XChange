package org.knowm.xchange.kucoin.uta.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;

/** Shared UTA transport endpoints (server time, private WebSocket token). */
@Path("api")
@Produces(MediaType.APPLICATION_JSON)
public interface UtaCommonAPI {

  /** Server time in milliseconds; shared by every KuCoin API generation. */
  @GET
  @Path("v1/timestamp")
  UtaResponse<Long> getServerTime() throws IOException;
}
