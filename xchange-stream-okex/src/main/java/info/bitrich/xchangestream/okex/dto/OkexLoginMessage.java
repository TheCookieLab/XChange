package info.bitrich.xchangestream.okex.dto;

import java.util.LinkedList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Legacy login message retained for source and binary compatibility with pre-rename clients.
 *
 * @deprecated use {@link info.bitrich.xchangestream.okx.dto.OkxLoginMessage} instead.
 */
@Data
@Deprecated
public class OkexLoginMessage {
  private String op = "login";

  List<LoginArg> args = new LinkedList<>();

  @Data
  @AllArgsConstructor
  public static class LoginArg {
    private String apiKey;
    private String passphrase;
    // Unix Epoch time, the unit is seconds
    private String timestamp;
    // https://www.okx.com/docs-v5/en/#websocket-api-login
    private String sign;
  }
}
