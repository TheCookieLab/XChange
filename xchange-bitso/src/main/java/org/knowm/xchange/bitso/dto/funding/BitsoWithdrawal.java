package org.knowm.xchange.bitso.dto.funding;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

/** Bitso Withdrawal Transaction DTO Represents a withdrawal transaction */
@Value
@Builder
@Jacksonized
public class BitsoWithdrawal {

  /** Withdrawal ID */
  private final String withdrawalId;

  /** Currency code */
  private final String currency;

  /** Withdrawal method (BTC, ETH, SPEI, PIX, etc.) */
  private final String method;

  /** Amount withdrawn */
  private final BigDecimal amount;

  /** Status (pending, complete, cancelled, failed) */
  private final String status;

  /** Creation timestamp */
  private final Date createdAt;

  /** Network for crypto withdrawal */
  private final String network;

  /** Destination address for crypto withdrawal */
  private final String address;

  /** Address tag for currencies that require it */
  private final String addressTag;

  /** Transaction hash for crypto withdrawal */
  private final String txHash;

  /** Number of confirmations for crypto withdrawal */
  private final Integer confirmations;

  /** Fee charged for the withdrawal */
  private final BigDecimal fee;

  /** Additional details specific to the withdrawal method */
  private final Map<String, Object> details;

  /** Internal reference ID */
  private final String internalId;

  /** External reference ID */
  private final String externalId;

  /** Receiving account ID for fiat withdrawals */
  private final String receivingAccountId;

  /** PIX key for PIX withdrawals */
  private final String pixKey;

  /** CLABE for Mexican peso withdrawals */
  private final String clabe;

  /** Bank name for fiat withdrawals */
  private final String bankName;

  /** Account holder name for fiat withdrawals */
  private final String accountHolderName;
}
