package org.knowm.xchange.kucoin.uta.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.kucoin.uta.dto.UtaWsToken;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/** Shared UTA transport endpoints (server time, private WebSocket token). */
@Path("api")
@Produces(MediaType.APPLICATION_JSON)
public interface UtaCommonAPI {

  /** Server time in milliseconds; shared by every KuCoin API generation. */
  @GET
  @Path("v1/timestamp")
  UtaResponse<Long> getServerTime() throws IOException;

  /** Private WebSocket token, valid 24 hours; used to build the private push endpoint. */
  @POST
  @Path("v2/bullet-private")
  UtaResponse<UtaWsToken> getPrivateWsToken(
      @HeaderParam(UtaConstants.API_HEADER_KEY) String apiKey,
      @HeaderParam(UtaConstants.API_HEADER_SIGN) ParamsDigest signature,
      @HeaderParam(UtaConstants.API_HEADER_TIMESTAMP) SynchronizedValueFactory<Long> nonce,
      @HeaderParam(UtaConstants.API_HEADER_PASSPHRASE) String apiPassphrase,
      @HeaderParam(UtaConstants.API_HEADER_KEY_VERSION) String keyVersion)
      throws IOException;
}
