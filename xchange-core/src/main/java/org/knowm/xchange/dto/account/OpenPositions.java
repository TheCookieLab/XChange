package org.knowm.xchange.dto.account;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class OpenPositions implements Serializable {

  private final List<OpenPosition> openPositions;

}
