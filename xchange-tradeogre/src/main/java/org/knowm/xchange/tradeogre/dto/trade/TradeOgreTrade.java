package org.knowm.xchange.tradeogre.dto.trade;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeOgreTrade {

    private long date;
    private String type;
    private BigDecimal price;
    private BigDecimal quantity;

}