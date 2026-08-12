package org.knowm.xchange.bitget.uta.v3.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ErrorAdapter;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3AccountInfo;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3Asset;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferOutcomeUnknownException;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferRequest;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferResult;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferableCoin;
import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;
import si.mazi.rescu.HttpStatusIOException;

/** Raw UTA v3 account calls. */
public class BitgetUtaV3AccountServiceRaw extends BitgetUtaV3BaseService {

  public BitgetUtaV3AccountServiceRaw(BitgetExchange exchange) {
    super(exchange);
  }

  /** Unified balances; {@code coin} filters to one coin when non-null. */
  public List<BitgetUtaV3Asset> getAssets(String coin) throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .assets(apiKey, bitgetUtaV3Digest, passphrase, exchange.getNonceFactory(), coin)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Raw account info payload (mode, uid, permissions, fee rates). */
  public BitgetUtaV3AccountInfo getAccountInfoRaw() throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .accountInfo(apiKey, bitgetUtaV3Digest, passphrase, exchange.getNonceFactory())
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /** Transferable amounts for a from/to account-type pair. */
  public List<BitgetUtaV3TransferableCoin> getTransferableCoins(
      String fromType, String toType, String coin, String marginCoin, String amount)
      throws IOException {
    try {
      return bitgetUtaV3Authenticated
          .transferableCoins(
              apiKey,
              bitgetUtaV3Digest,
              passphrase,
              exchange.getNonceFactory(),
              fromType,
              toType,
              coin,
              marginCoin,
              amount)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    }
  }

  /**
   * Initiates a transfer between account types; the transfer is asynchronous.
   *
   * <p>When {@code request} carries no {@code clientOid}, one is generated so every call is
   * idempotency-keyed. A transport-level failure (read timeout, connection reset) surfaces as
   * {@link BitgetUtaV3TransferOutcomeUnknownException}: the transfer may have been accepted, so
   * verify balances before retrying and reuse
   * {@link BitgetUtaV3TransferOutcomeUnknownException#getClientOid()} for an idempotent retry.
   */
  public BitgetUtaV3TransferResult transfer(BitgetUtaV3TransferRequest request) throws IOException {
    BitgetUtaV3TransferRequest idempotentRequest = withClientOid(request);
    try {
      return bitgetUtaV3Authenticated
          .transfer(
              apiKey, bitgetUtaV3Digest, passphrase, exchange.getNonceFactory(), idempotentRequest)
          .getData();
    } catch (BitgetUtaV3Exception e) {
      throw BitgetUtaV3ErrorAdapter.adapt(e);
    } catch (HttpStatusIOException e) {
      // the server responded with an HTTP-level error: the transfer was not accepted
      throw e;
    } catch (IOException e) {
      // no HTTP response (read timeout, connection reset): the transfer may have been accepted
      throw new BitgetUtaV3TransferOutcomeUnknownException(idempotentRequest.getClientOid(), e);
    }
  }

  private static BitgetUtaV3TransferRequest withClientOid(BitgetUtaV3TransferRequest request) {
    if (request.getClientOid() != null) {
      return request;
    }
    return request.toBuilder().clientOid(UUID.randomUUID().toString()).build();
  }
}
