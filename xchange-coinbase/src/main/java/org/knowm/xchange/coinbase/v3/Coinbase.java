package org.knowm.xchange.coinbase.v3;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public interface Coinbase extends org.knowm.xchange.coinbase.v2.Coinbase {

}
