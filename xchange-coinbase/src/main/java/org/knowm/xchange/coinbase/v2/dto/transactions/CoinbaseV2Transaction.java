package org.knowm.xchange.coinbase.v2.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseV2Money;

/**
 * Coinbase API v2 transaction.
 *
 * <p>Transactions are used to classify account-level activity such as deposits, withdrawals, and
 * transfers that affect collateral independently of trading fills.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2Transaction {

  private final String id;
  private final String type;
  private final String status;
  private final CoinbaseV2Money amount;
  private final CoinbaseV2Money nativeAmount;
  private final String createdAt;
  private final String updatedAt;
  private final String resource;
  private final String resourcePath;
  private final CoinbaseV2TransactionDetails details;

  @JsonCreator
  public CoinbaseV2Transaction(
      @JsonProperty("id") String id,
      @JsonProperty("type") String type,
      @JsonProperty("status") String status,
      @JsonProperty("amount") CoinbaseV2Money amount,
      @JsonProperty("native_amount") CoinbaseV2Money nativeAmount,
      @JsonProperty("created_at") String createdAt,
      @JsonProperty("updated_at") String updatedAt,
      @JsonProperty("resource") String resource,
      @JsonProperty("resource_path") String resourcePath,
      @JsonProperty("details") CoinbaseV2TransactionDetails details) {
    this.id = id;
    this.type = type;
    this.status = status;
    this.amount = amount;
    this.nativeAmount = nativeAmount;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.resource = resource;
    this.resourcePath = resourcePath;
    this.details = details;
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getStatus() {
    return status;
  }

  public CoinbaseV2Money getAmount() {
    return amount;
  }

  public CoinbaseV2Money getNativeAmount() {
    return nativeAmount;
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

  public CoinbaseV2TransactionDetails getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return "CoinbaseV2Transaction [id=" + id + ", type=" + type + ", status=" + status + ", amount="
        + amount + ", createdAt=" + createdAt + "]";
  }
}

