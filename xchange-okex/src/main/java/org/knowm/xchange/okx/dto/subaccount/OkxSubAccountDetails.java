package org.knowm.xchange.okx.dto.subaccount;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class OkxSubAccountDetails {

  @JsonProperty("enable")
  private String enable;

  @JsonProperty("subAcct")
  private String subAcct;

  @JsonProperty("label")
  private String label;

  @JsonProperty("mobile")
  private String mobile;

  @JsonProperty("gAuth")
  private String gAuth;

  @JsonProperty("ts")
  private String ts;

  /**
   * Manual accessors for {@code gAuth}: Lombok would generate {@code getGAuth()}, which Jackson's
   * legacy bean mangling would treat as a separate {@code gauth} property instead of the wire key
   * {@code gAuth}. Declaring the accessors with the annotation merges them into one property.
   */
  @JsonProperty("gAuth")
  public String getGAuth() {
    return gAuth;
  }

  @JsonProperty("gAuth")
  public void setGAuth(String gAuth) {
    this.gAuth = gAuth;
  }
}
