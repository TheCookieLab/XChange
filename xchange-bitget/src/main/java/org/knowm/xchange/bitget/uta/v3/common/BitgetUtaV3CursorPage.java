package org.knowm.xchange.bitget.uta.v3.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Typed cursor page returned by paginated v3 endpoints (unfilled-orders, history-orders, fills).
 *
 * <p>The provider's cursor is opaque: pass the returned value back as {@code cursor} to fetch the
 * next (older) page. Unfilled orders use the smallest orderId of the current page as the cursor;
 * history and fills use a provider-generated cursor.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3CursorPage<T> {

  @JsonProperty("list")
  private List<T> list;

  @JsonProperty("cursor")
  private String cursor;
}
