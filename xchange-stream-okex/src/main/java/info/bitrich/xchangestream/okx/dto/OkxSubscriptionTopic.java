package info.bitrich.xchangestream.okx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.knowm.xchange.okx.dto.OkxInstType;

@Data
@AllArgsConstructor
public class OkxSubscriptionTopic {
  private final String channel;

  private final OkxInstType instType;

  private final String uly;

  private final String instId;
}
