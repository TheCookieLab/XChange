package info.bitrich.xchangestream.okex.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Legacy subscribe message retained for source and binary compatibility with pre-rename clients.
 *
 * @deprecated use {@link info.bitrich.xchangestream.okx.dto.OkxSubscribeMessage} instead.
 */
@Data
@AllArgsConstructor
@Deprecated
public class OkexSubscribeMessage<T> {
  private final String id;
  private final String op;
  private final List<T> args;
}
