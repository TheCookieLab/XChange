package org.knowm.xchange.coinbase.v2;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import org.knowm.xchange.coinbase.v2.dto.accounts.CoinbaseV2AccountsResponse;
import org.knowm.xchange.coinbase.v2.dto.transactions.CoinbaseV2TransactionsResponse;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import si.mazi.rescu.ParamsDigest;

/**
 * Coinbase API v2 authenticated endpoints.
 *
 * <p>These endpoints provide account and transaction history that is not currently exposed by
 * Coinbase Advanced Trade (v3). They are used by higher-level components to classify deposits,
 * withdrawals, and transfers as first-class "cash-flow" events rather than inferring them from
 * collateral residuals.</p>
 *
 * <p>Authentication uses the same {@code Authorization: Bearer <JWT>} header as the Advanced Trade
 * API. When a configured API key does not have access to v2 endpoints, callers should treat
 * failures as "not available" and fall back to residual-based adjustment accounting.</p>
 */
@Path("/v2")
@Produces(MediaType.APPLICATION_JSON)
public interface CoinbaseV2Authenticated {

  /**
   * All Coinbase API requests must include an Authorization Bearer header containing a JSON Web
   * Token (JWT) generated from the CDP API keys.
   */
  String CB_AUTHORIZATION_KEY = "Authorization";

  /**
   * List accounts for the authenticated user.
   *
   * <p>Pagination is driven by {@code starting_after}/{@code ending_before} and {@code order}.
   * Coinbase responses include a {@code pagination} object with {@code next_uri}/{@code previous_uri}
   * for convenience.</p>
   */
  @GET
  @Path("accounts")
  CoinbaseV2AccountsResponse listAccounts(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @QueryParam("limit") Integer limit,
      @QueryParam("starting_after") String startingAfter,
      @QueryParam("ending_before") String endingBefore,
      @QueryParam("order") String order) throws IOException, CoinbaseException;

  /**
   * List transactions for a specific Coinbase v2 account id.
   *
   * <p>This endpoint is used to detect deposits/withdrawals/transfers that affect the account's
   * collateral independently of trading fills.</p>
   *
   * @param accountId Coinbase v2 account id
   */
  @GET
  @Path("accounts/{account_id}/transactions")
  CoinbaseV2TransactionsResponse listTransactions(
      @HeaderParam(CB_AUTHORIZATION_KEY) ParamsDigest jwtDigest,
      @PathParam("account_id") String accountId,
      @QueryParam("limit") Integer limit,
      @QueryParam("starting_after") String startingAfter,
      @QueryParam("ending_before") String endingBefore,
      @QueryParam("order") String order) throws IOException, CoinbaseException;
}

