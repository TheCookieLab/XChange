package org.knowm.xchange.mexc.v3.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.mexc.v3.MexcV3Adapters;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.client.ReplaySafety;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3ListenKey;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3ListenKeyList;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3TradeFeeResponse;
import org.knowm.xchange.service.account.AccountService;

/** Account service over the authenticated MEXC Spot v3 REST surface. */
public class MexcV3AccountService extends MexcV3BaseService implements AccountService {

  public MexcV3AccountService(Exchange exchange) {
    super(exchange);
  }

  /** Raw account snapshot ({@code GET /api/v3/account}). */
  public org.knowm.xchange.mexc.v3.dto.account.MexcV3Account getAccountRaw()
      throws IOException, MexcV3Exception {
    return mexcV3Authenticated.account(apiKey, recvWindowMs, timestampFactory, signatureCreator);
  }

  /** Raw trade-fee response for one symbol ({@code GET /api/v3/tradeFee}). */
  public MexcV3TradeFeeResponse getTradeFeeRaw(
      org.knowm.xchange.currency.CurrencyPair pair) throws IOException, MexcV3Exception {
    return mexcV3Authenticated.tradeFee(
        apiKey, MexcV3Symbols.toMexcSymbol(pair), recvWindowMs, timestampFactory, signatureCreator);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    return execute(
        () -> new AccountInfo(MexcV3Adapters.adaptWallet(getAccountRaw())), ReplaySafety.READ);
  }

  /**
   * Opens a user-data stream and returns its listen key ({@code POST /api/v3/userDataStream}).
   *
   * <p>The key authorizes private WebSocket channels (account/orders/deals) when passed as the
   * {@code listenKey} query parameter on the stream URI. Keys expire after 60 minutes and must be
   * refreshed with {@link #keepAliveListenKey(String)}; at most 60 keys are valid per user, so
   * closed streams should call {@link #closeListenKey(String)}.
   *
   * <p>Classified {@link ReplaySafety#READ}: a transport failure may have created a key, but a
   * duplicate key is harmless (it expires in 60 minutes and the caller simply retries), so the raw
   * {@link IOException} propagates instead of an ambiguous error.
   */
  public MexcV3ListenKey createListenKey() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3Authenticated.createListenKey(), ReplaySafety.READ);
  }

  /** Lists all currently valid listen keys ({@code GET /api/v3/userDataStream}). */
  public MexcV3ListenKeyList listListenKeys() throws IOException, MexcV3Exception {
    return execute(() -> mexcV3Authenticated.listListenKeys(), ReplaySafety.READ);
  }

  /**
   * Renews a listen key's validity window ({@code PUT /api/v3/userDataStream}). MEXC documents the
   * key lifetime as 60 minutes; keeping it alive every 30 minutes leaves a full retry window.
   */
  public MexcV3ListenKey keepAliveListenKey(String listenKey) throws IOException, MexcV3Exception {
    return execute(() -> mexcV3Authenticated.keepAliveListenKey(listenKey), ReplaySafety.READ);
  }

  /**
   * Closes a user-data stream ({@code DELETE /api/v3/userDataStream}). The key stops authorizing
   * private WebSocket channels immediately; no further keepalive is needed afterwards.
   */
  public MexcV3ListenKey closeListenKey(String listenKey) throws IOException, MexcV3Exception {
    return execute(() -> mexcV3Authenticated.closeListenKey(listenKey), ReplaySafety.READ);
  }
}
