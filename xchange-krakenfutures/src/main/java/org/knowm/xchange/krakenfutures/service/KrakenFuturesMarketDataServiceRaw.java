package org.knowm.xchange.krakenfutures.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.krakenfutures.KrakenFuturesAdapters;
import org.knowm.xchange.krakenfutures.dto.marketData.KrakenFuturesInstruments;
import org.knowm.xchange.krakenfutures.dto.marketData.KrakenFuturesOrderBook;
import org.knowm.xchange.krakenfutures.dto.marketData.KrakenFuturesPublicFills;
import org.knowm.xchange.krakenfutures.dto.marketData.KrakenFuturesTicker;
import org.knowm.xchange.krakenfutures.dto.marketData.KrakenFuturesTickers;

/**
 * @author Jean-Christophe Laruelle
 */
public class KrakenFuturesMarketDataServiceRaw extends KrakenFuturesBaseService {

  /**
   * Constructor
   *
   * @param exchange of KrakenFutures
   */
  public KrakenFuturesMarketDataServiceRaw(Exchange exchange) {

    super(exchange);
  }

  public KrakenFuturesTicker getKrakenFuturesTicker(Instrument instrument) throws IOException {

    return getKrakenFuturesTickers()
        .getTicker(KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument));
  }

  public KrakenFuturesTickers getKrakenFuturesTickers() throws IOException {

    KrakenFuturesTickers tickers = krakenFuturesAuthenticated.getTickers();

    checkSuccess(tickers, "getKrakenFuturesTickers");
    return tickers;
  }

  public KrakenFuturesInstruments getKrakenFuturesInstruments() throws IOException {

    KrakenFuturesInstruments instruments = krakenFuturesAuthenticated.getInstruments();

    checkSuccess(instruments, "getKrakenFuturesInstruments");
    return instruments;
  }

  public KrakenFuturesOrderBook getKrakenFuturesOrderBook(Instrument instrument)
      throws IOException {

    KrakenFuturesOrderBook orderBook =
        krakenFuturesAuthenticated.getOrderBook(
            KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument));

    checkSuccess(orderBook, "getKrakenFuturesOrderBook");
    orderBook.setInstrument(instrument);
    return orderBook;
  }

  public KrakenFuturesPublicFills getKrakenFuturesTrades(Instrument instrument) throws IOException {

    KrakenFuturesPublicFills publicFills =
        krakenFuturesAuthenticated.getHistory(
            KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument));

    checkSuccess(publicFills, "getKrakenFuturesTrades");
    publicFills.setInstrument(instrument);
    return publicFills;
  }
}
