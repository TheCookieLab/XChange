package org.knowm.xchange.bitso.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitso.BitsoAdapters;
import org.knowm.xchange.bitso.BitsoAuthenticated;
import org.knowm.xchange.bitso.BitsoFundingAuthenticated;
import org.knowm.xchange.bitso.BitsoJacksonObjectMapperFactory;
import org.knowm.xchange.bitso.dto.BitsoBaseResponse;
import org.knowm.xchange.bitso.dto.account.BitsoBalance;
import org.knowm.xchange.bitso.dto.funding.*;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.exceptions.ExchangeException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BitsoAccountServiceRaw extends BitsoBaseService {

  private final BitsoDigest signatureCreator;
  private final BitsoAuthenticated bitsoAuthenticated;
  private final BitsoFundingAuthenticated bitsoFundingAuthenticated;

  protected BitsoAccountServiceRaw(Exchange exchange) {
    super(exchange);

    this.bitsoAuthenticated =
        ExchangeRestProxyBuilder.forInterface(
                BitsoAuthenticated.class, exchange.getExchangeSpecification())
            .clientConfigCustomizer(
                clientConfig ->
                    clientConfig.setJacksonObjectMapperFactory(
                        new BitsoJacksonObjectMapperFactory()))
            .build();
    this.bitsoFundingAuthenticated =
        ExchangeRestProxyBuilder.forInterface(
                BitsoFundingAuthenticated.class, exchange.getExchangeSpecification())
            .clientConfigCustomizer(
                clientConfig ->
                    clientConfig.setJacksonObjectMapperFactory(
                        new BitsoJacksonObjectMapperFactory()))
            .build();
    this.signatureCreator =
        BitsoDigest.createInstance(
            exchange.getExchangeSpecification().getSecretKey(),
            exchange.getExchangeSpecification().getApiKey());
  }

  public BitsoBalance getBitsoBalance() throws IOException {

    BitsoBaseResponse<BitsoBalance> response =
        bitsoAuthenticated.getBalance(signatureCreator, exchange.getNonceFactory());

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error getting balance";
      throw new ExchangeException("Error getting balance. " + errorMessage);
    }

    return response.getPayload();
  }

  /** List funding transactions (deposits) */
  public List<BitsoFunding> getBitsoFundings(
      String currency, Integer limit, String marker, String sort) throws IOException {
    BitsoBaseResponse<BitsoFunding[]> response =
        bitsoFundingAuthenticated.listFundings(
            signatureCreator, exchange.getNonceFactory(), currency, limit, marker, sort);

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error getting fundings";
      throw new ExchangeException("Error getting fundings. " + errorMessage);
    }

    return Arrays.asList(response.getPayload());
  }

  /** List withdrawal transactions */
  public List<BitsoWithdrawal> getBitsoWithdrawals(
      String currency, Integer limit, String marker, String sort) throws IOException {
    BitsoBaseResponse<BitsoWithdrawal[]> response =
        bitsoFundingAuthenticated.listWithdrawals(
            signatureCreator, exchange.getNonceFactory(), currency, limit, marker, sort);

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error getting withdrawals";
      throw new ExchangeException("Error getting withdrawals. " + errorMessage);
    }

    return Arrays.asList(response.getPayload());
  }

  /** Create a cryptocurrency withdrawal */
  public BitsoWithdrawal createBitsoCryptoWithdrawal(BitsoWithdrawalRequest request)
      throws IOException {
    BitsoBaseResponse<BitsoWithdrawal> response =
        bitsoFundingAuthenticated.createCryptoWithdrawal(
            signatureCreator, exchange.getNonceFactory(), request);

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error creating crypto withdrawal";
      throw new ExchangeException("Error creating crypto withdrawal. " + errorMessage);
    }

    return response.getPayload();
  }

  /** Create a fiat withdrawal */
  public BitsoWithdrawal createBitsoFiatWithdrawal(BitsoWithdrawalRequest request)
      throws IOException {
    BitsoBaseResponse<BitsoWithdrawal> response =
        bitsoFundingAuthenticated.createFiatWithdrawal(
            signatureCreator, exchange.getNonceFactory(), request);

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error creating fiat withdrawal";
      throw new ExchangeException("Error creating fiat withdrawal. " + errorMessage);
    }

    return response.getPayload();
  }

  /** Get withdrawal methods for a currency */
  public List<BitsoWithdrawalMethod> getBitsoWithdrawalMethods(String currencyTicker)
      throws IOException {
    BitsoBaseResponse<BitsoWithdrawalMethod[]> response =
        bitsoFundingAuthenticated.getWithdrawalMethods(
            signatureCreator,
            exchange.getNonceFactory(),
            BitsoAdapters.toBitsoCurrency(currencyTicker));

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error getting withdrawal methods";
      throw new ExchangeException("Error getting withdrawal methods. " + errorMessage);
    }

    return Arrays.asList(response.getPayload());
  }

  /** List receiving accounts */
  public List<BitsoReceivingAccount> getBitsoReceivingAccounts(String currency) throws IOException {
    BitsoBaseResponse<BitsoReceivingAccount[]> response =
        bitsoFundingAuthenticated.listReceivingAccounts(
            signatureCreator, exchange.getNonceFactory(), currency);

    if (!response.getSuccess() || response.getError() != null) {
      String errorMessage =
          response.getError() != null
              ? response.getError().getMessage()
              : "Unknown error getting receiving accounts";
      throw new ExchangeException("Error getting receiving accounts. " + errorMessage);
    }

    return Arrays.asList(response.getPayload());
  }
}
