package info.bitrich.xchangestream.binance.dto.trade;

import org.knowm.xchange.binance.dto.trade.BinanceNewOrder;

import java.util.List;

public class BinanceWebsocketOrderCancelAndReplaceResponse {
    private String cancelResult;
    private String newOrderResult;
    private List<BinanceNewOrder> cancelResponse;
    private List<BinanceNewOrder> newOrderResponse;
}
