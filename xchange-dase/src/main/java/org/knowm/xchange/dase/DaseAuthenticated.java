package org.knowm.xchange.dase;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.dase.dto.user.DaseUserProfile;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface DaseAuthenticated {

  @GET
  @Path("/users/me")
  DaseUserProfile getUserProfile(
      @HeaderParam("ex-api-key") String apiKey,
      @HeaderParam("ex-api-sign") ParamsDigest signer,
      @HeaderParam("ex-api-timestamp") SynchronizedValueFactory<String> timestamp)
      throws IOException;
}


