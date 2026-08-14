package info.bitrich.xchangestream.okex.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.knowm.xchange.okex.dto.OkexInstType;

/**
 * Legacy subscription topic retained for source and binary compatibility with pre-rename clients.
 *
 * @deprecated use {@link info.bitrich.xchangestream.okx.dto.OkxSubscriptionTopic} instead.
 */
@Data
@AllArgsConstructor
@Deprecated
public class OkexSubscriptionTopic {
  private final String channel;

  private final OkexInstType instType;

  private final String uly;

  private final String instId;
}
