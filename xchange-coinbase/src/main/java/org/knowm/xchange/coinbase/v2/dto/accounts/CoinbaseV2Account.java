package org.knowm.xchange.coinbase.v2.dto.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseV2Money;

/**
 * Coinbase API v2 account.
 *
 * <p>This represents a single Coinbase "account" (for example a USD wallet). These accounts are
 * used as the anchor for transaction history in {@code /v2/accounts/{account_id}/transactions}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2Account {

  private final String id;
  private final String name;
  private final Boolean primary;
  private final String type;
  private final CoinbaseV2Currency currency;
  private final CoinbaseV2Money balance;
  private final String createdAt;
  private final String updatedAt;
  private final String resource;
  private final String resourcePath;

  @JsonCreator
  public CoinbaseV2Account(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("primary") Boolean primary,
      @JsonProperty("type") String type,
      @JsonProperty("currency") CoinbaseV2Currency currency,
      @JsonProperty("balance") CoinbaseV2Money balance,
      @JsonProperty("created_at") String createdAt,
      @JsonProperty("updated_at") String updatedAt,
      @JsonProperty("resource") String resource,
      @JsonProperty("resource_path") String resourcePath) {
    this.id = id;
    this.name = name;
    this.primary = primary;
    this.type = type;
    this.currency = currency;
    this.balance = balance;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.resource = resource;
    this.resourcePath = resourcePath;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Boolean getPrimary() {
    return primary;
  }

  public String getType() {
    return type;
  }

  public CoinbaseV2Currency getCurrency() {
    return currency;
  }

  public CoinbaseV2Money getBalance() {
    return balance;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public String getResource() {
    return resource;
  }

  public String getResourcePath() {
    return resourcePath;
  }

  @Override
  public String toString() {
    return "CoinbaseV2Account [id=" + id + ", name=" + name + ", primary=" + primary + ", type="
        + type + ", currency=" + currency + ", balance=" + balance + "]";
  }
}

