package org.knowm.xchange.client.ratelimit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import si.mazi.rescu.ParamsDigest;

/** Shared REST interface and policy for the rescu rate-limit boundary tests. */
final class RateLimitedProxyFixture {

  static final long SEC = 1_000_000_000L;
  static final String SCOPE = "u1";

  private RateLimitedProxyFixture() {}

  /** The proxied interface: a plain read, a path-parameter read, a signed read and a write. */
  @Path("api/v3")
  @Produces(MediaType.APPLICATION_JSON)
  public interface Api {

    @GET
    @Path("things")
    String list() throws IOException;

    @GET
    @Path("things/{id}")
    String one(@PathParam("id") String id) throws IOException;

    @GET
    @Path("signed")
    String signed(@HeaderParam("X-Sign") ParamsDigest digest) throws IOException;

    @POST
    @Path("orders")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    String create(@FormParam("n") String n) throws IOException;

    @GET
    @Path("/redirect/")
    String redirect() throws IOException;
  }

  /** A policy whose single user budget has the given capacity and 1/second refill. */
  static RateLimitPolicy policy(List<RateLimitRequest> seen, long capacity) {
    RateLimitOperation read =
        RateLimitOperation.unitCost("read", RateLimitPriority.EXECUTION, true, "user");
    RateLimitOperation signed =
        RateLimitOperation.unitCost("signed", RateLimitPriority.EXECUTION, true, "user");
    RateLimitOperation write =
        RateLimitOperation.unitCost("write", RateLimitPriority.EXECUTION, false, "user");
    List<RateLimitRequest> sink = Collections.synchronizedList(seen);
    return new RateLimitPolicy(
            "test.rescu",
            "test-1",
            "unit test fixture",
            List.of(
                RateLimitBudget.tokenBucket(
                    "user", ScopeKind.USER, capacity, 1, Duration.ofSeconds(1))),
            request -> {
              sink.add(request);
              String key = request.getOperationKey();
              if (key.startsWith("GET api/v3/things") || key.startsWith("GET api/v3/redirect")) {
                return read;
              }
              if (key.equals("GET api/v3/signed")) {
                return signed;
              }
              if (key.equals("POST api/v3/orders")) {
                return write;
              }
              return null;
            },
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of(418)))
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofHours(1))
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(4), 0.0);
  }

  static List<RateLimitRequest> newSeen() {
    return new ArrayList<>();
  }
}
