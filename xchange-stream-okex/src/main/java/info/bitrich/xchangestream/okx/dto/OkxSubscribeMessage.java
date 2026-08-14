package info.bitrich.xchangestream.okx.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OkxSubscribeMessage<T> {
  private final String id;
  private final String op;
  private final List<T> args;
}
