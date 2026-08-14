/** */
package org.knowm.xchange.okx.service.params;

import lombok.Getter;
import lombok.Setter;
import org.knowm.xchange.service.marketdata.params.Params;

/**
 * @author leeyazhou
 */
@Setter
@Getter
public class OkxTickerParams implements Params {
  private String instType;
  private String uly;
  private String instFamily;
}
