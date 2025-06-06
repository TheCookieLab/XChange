package org.knowm.xchange.bitso.dto.funding;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.util.Map;

/** Bitso Withdrawal Request DTO Used to create withdrawal requests */
@Value
@Builder
@Jacksonized
public class BitsoWithdrawalRequest {

  /** Currency code */
  private final String currency;

  /** Amount to withdraw */
  private final BigDecimal amount;

  private final BigDecimal maxFee;

  private final String asset;

  /** Network for crypto withdrawals */
  private final String network;

  private final String method;

  private final String protocol;

  /** Destination address for crypto withdrawals */
  private final String address;

  /** Address tag for currencies that require it */
  private final String addressTag;

  private final String originId;

  /** Receiving account ID for fiat withdrawals */
  private final String receivingAccountId;

  /** PIX key for PIX withdrawals */
  private final String pixKey;

  /** PIX key type (cpf, cnpj, email, phone, random) */
  private final String pixKeyType;

  /** Description for PIX withdrawals */
  private final String description;

  /** Internal reference ID */
  private final String internalId;

  /** Additional parameters for specific withdrawal types */
  private final Map<String, Object> additionalInfo;

  /** Notes or reference for the withdrawal */
  private final String notes;
}
