package org.knowm.xchange.dase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dase.dto.marketdata.DaseOrderBookSnapshot;
import org.knowm.xchange.dase.dto.marketdata.DaseTicker;
import org.knowm.xchange.dase.dto.marketdata.DaseTrade;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;

public class AdaptersJSONTest {

  private static final CurrencyPair PAIR = CurrencyPair.ADA_EUR;

  @Test
  public void adaptTicker() throws Exception {
    InputStream is =
        getClass()
            .getResourceAsStream("/org/knowm/xchange/dase/dto/marketdata/example-ticker.json");
    DaseTicker raw = new ObjectMapper().readValue(is, DaseTicker.class);
    Ticker t = DaseAdapters.adaptTicker(raw, PAIR);

    assertThat(t.getInstrument()).isEqualTo(PAIR);
    assertThat(t.getAsk()).isNotNull();
    assertThat(t.getBid()).isNotNull();
    assertThat(t.getLast()).isNotNull();
  }

  @Test
  public void adaptOrderBook() throws Exception {
    InputStream is =
        getClass()
            .getResourceAsStream("/org/knowm/xchange/dase/dto/marketdata/example-snapshot.json");
    DaseOrderBookSnapshot raw = new ObjectMapper().readValue(is, DaseOrderBookSnapshot.class);
    OrderBook ob = DaseAdapters.adaptOrderBook(raw, PAIR);

    assertThat(ob.getAsks()).isNotEmpty();
    assertThat(ob.getBids()).isNotEmpty();
    assertThat(ob.getTimeStamp()).isNotNull();
  }

  @Test
  public void adaptTrades() throws Exception {
    InputStream is =
        getClass()
            .getResourceAsStream("/org/knowm/xchange/dase/dto/marketdata/example-trades.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(is);
    List<DaseTrade> list =
        StreamSupport.stream(root.get("trades").spliterator(), false)
            .map(n -> mapper.convertValue(n, DaseTrade.class))
            .collect(Collectors.toList());
    Trades tr = DaseAdapters.adaptTrades(list, PAIR);

    assertThat(tr.getTrades()).hasSize(2);
    assertThat(tr.getTrades().get(0).getInstrument()).isEqualTo(PAIR);
    // maker_side: sell -> taker side buy -> BID
    assertThat(tr.getTrades().get(0).getType()).isEqualTo(org.knowm.xchange.dto.Order.OrderType.BID);
    // maker_side: buy -> taker side sell -> ASK
    assertThat(tr.getTrades().get(1).getType()).isEqualTo(org.knowm.xchange.dto.Order.OrderType.ASK);
  }
}
