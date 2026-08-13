package org.knowm.xchange.bybit.config;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;

/**
 * Validated, typed configuration for the Bybit V5 REST and streaming modules.
 *
 * <p>{@link #from(ExchangeSpecification)} resolves and validates the execution environment and
 * account type before any service or transport is constructed, so unsupported or contradictory
 * combinations fail fast instead of silently rerouting traffic. The legacy exchange-specific
 * parameters ({@code Exchange.USE_SANDBOX}, {@code BybitExchange.SPECIFIC_PARAM_TESTNET}, {@code
 * BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE}, {@link #EXCHANGE_TYPE}) remain the supported input
 * surface; this class only makes their interpretation explicit and shared across modules.
 */
public final class BybitConfiguration {

  /** Exchange-specific parameter key selecting the public-stream category. */
  public static final String EXCHANGE_TYPE = "Exchange_Type";

  /** Public-stream category used when {@link #EXCHANGE_TYPE} is not configured. */
  public static final BybitCategory DEFAULT_STREAM_CATEGORY = BybitCategory.LINEAR;

  private final BybitEnvironment environment;
  private final BybitAccountType accountType;

  private BybitConfiguration(BybitEnvironment environment, BybitAccountType accountType) {
    this.environment = environment;
    this.accountType = accountType;
  }

  /** Validated execution environment (production, demo, or testnet). */
  public BybitEnvironment getEnvironment() {
    return environment;
  }

  /** Validated account type, defaulting to {@link BybitAccountType#UNIFIED}. */
  public BybitAccountType getAccountType() {
    return accountType;
  }

  /**
   * Resolves and validates the full configuration from an exchange specification.
   *
   * @throws IllegalArgumentException on contradictory environment flags or an unsupported account
   *     type value
   */
  public static BybitConfiguration from(ExchangeSpecification specification) {
    return new BybitConfiguration(
        BybitEnvironment.resolve(specification), resolveAccountType(specification));
  }

  /**
   * Resolves the public-stream category from the {@link #EXCHANGE_TYPE} parameter, defaulting to
   * {@link #DEFAULT_STREAM_CATEGORY} when unset. The historical behavior without the parameter was
   * a {@code NullPointerException} during transport construction; the default makes the transport
   * deterministic instead.
   */
  public static BybitCategory resolveStreamCategory(ExchangeSpecification specification) {
    Object raw = specification.getExchangeSpecificParametersItem(EXCHANGE_TYPE);
    if (raw == null) {
      return DEFAULT_STREAM_CATEGORY;
    }
    if (raw instanceof BybitCategory) {
      return (BybitCategory) raw;
    }
    throw new IllegalArgumentException(
        "Unsupported Bybit stream category: "
            + raw
            + ". Expected a BybitCategory (e.g. BybitCategory.LINEAR) for parameter "
            + EXCHANGE_TYPE
            + ".");
  }

  private static BybitAccountType resolveAccountType(ExchangeSpecification specification) {
    Object raw =
        specification.getExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE);
    if (raw == null) {
      return BybitAccountType.UNIFIED;
    }
    if (raw instanceof BybitAccountType) {
      return (BybitAccountType) raw;
    }
    throw new IllegalArgumentException(
        "Unsupported Bybit account type: "
            + raw
            + ". Expected a BybitAccountType (e.g. BybitAccountType.UNIFIED) for parameter "
            + BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE
            + ".");
  }
}
