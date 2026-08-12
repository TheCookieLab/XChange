package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.Validate;

/**
 * One price-size level of a Bitget UTA v3 order-book push.
 *
 * <p>The wire format is a two-element array ({@code ["29000.0","0.5"]}).
 *
 * @since 5.1.0
 */
@Data
@Builder
public class BitgetUtaV3OrderBookLevel {

  private final BigDecimal price;
  private final BigDecimal size;

  @JsonCreator
  public static BitgetUtaV3OrderBookLevel fromArray(List<String> level) {
    Validate.isTrue(level != null && level.size() >= 2, "Order-book level must be [price, size]");
    return BitgetUtaV3OrderBookLevel.builder()
        .price(new BigDecimal(level.get(0)))
        .size(new BigDecimal(level.get(1)))
        .build();
  }
}
