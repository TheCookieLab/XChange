package org.knowm.xchange.bitfinex.v1.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexDepositAddressRequest extends BitfinexAuthenticatedRequest {
  @JsonProperty("request")
  protected String request;

  @JsonProperty("method")
  private String method;

  @JsonProperty("wallet_name")
  private String wallet_name;

  @JsonProperty("renew")
  private int renew;

  public BitfinexDepositAddressRequest(String method, String wallet_name, int renew) {
    this.request = "/v1/deposit/new";
    this.method = method;
    this.wallet_name = wallet_name;
    this.renew = renew;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getWallet_name() {
    return wallet_name;
  }

  public void setWallet_name(String wallet_name) {
    this.wallet_name = wallet_name;
  }

  public int getRenew() {
    return renew;
  }

  public void setRenew(int renew) {
    this.renew = renew;
  }

  public String getRequest() {
    return request;
  }

  public void setRequest(String request) {
    this.request = request;
  }
}
