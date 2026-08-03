package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.client.PolymarketEip712Signer;
import org.knowm.xchange.polymarket.dto.account.PolymarketBalanceResponse;

/** Raw Polymarket account access returning provider DTOs. */
public class PolymarketAccountServiceRaw extends PolymarketBaseService {

  /** CLOB asset-type selector for the USDC collateral balance. */
  static final String ASSET_TYPE_COLLATERAL = "COLLATERAL";

  protected PolymarketAccountServiceRaw(PolymarketExchange exchange) {
    super(exchange);
  }

  /** Collateral (USDC) balance in 6-decimal fixed-point, as seen by the EOA signature type. */
  public PolymarketBalanceResponse getCollateralBalance() throws IOException {
    return clobAuthenticated.getBalanceAllowance(
        walletAddress,
        timestampSecondsFactory(),
        apiKey,
        passphrase,
        l2Digest,
        ASSET_TYPE_COLLATERAL,
        PolymarketEip712Signer.SIGNATURE_TYPE_EOA);
  }
}
