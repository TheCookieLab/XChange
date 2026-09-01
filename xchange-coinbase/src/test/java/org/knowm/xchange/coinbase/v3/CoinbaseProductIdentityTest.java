package org.knowm.xchange.coinbase.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity.AmbiguousMappingException;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity.Product;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseFutureProductDetails;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbasePerpetualDetails;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductsResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseMarketDataServiceRaw;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import si.mazi.rescu.ParamsDigest;

/** Deterministic tests for the Coinbase product identity catalog. */
public class CoinbaseProductIdentityTest {

  private static CoinbaseProductResponse spot(String productId, String base, String quote) {
    return new CoinbaseProductResponse(
        productId, null, null, null, null, null, base, quote, "SPOT", "EXCHANGE", null);
  }

  private static CoinbaseProductResponse future(
      String productId, String base, String quote, String venue, boolean perpetual) {
    CoinbaseFutureProductDetails details =
        perpetual
            ? new CoinbaseFutureProductDetails(
                base,
                "0.0001",
                "2026-01-01T00:00:00Z",
                null,
                null,
                new CoinbasePerpetualDetails("0.0001", "2026-01-01T00:00:00Z"),
                null)
            : new CoinbaseFutureProductDetails(
                base, "0.0001", "2026-01-01T00:00:00Z", null, null, null, null);
    return new CoinbaseProductResponse(
        productId, null, null, null, null, null, base, quote, "FUTURE", venue, details);
  }

  @Test
  public void spotMapsToCurrencyPairLosslessly() {
    CoinbaseProductIdentity identity =
        CoinbaseProductIdentity.build(
            Collections.singletonList(spot("BTC-USD", "BTC", "USD")));

    CurrencyPair pair = CurrencyPair.BTC_USD;
    assertEquals(pair, identity.instrument("BTC-USD"));
    assertEquals("BTC-USD", identity.requireProductId(pair));
    assertEquals("BTC-USD", identity.productId(pair));
  }

  @Test
  public void datedFutureKeepsExpiryPrompt() {
    CoinbaseProductIdentity identity =
        CoinbaseProductIdentity.build(
            Collections.singletonList(future("BTC-28MAR25-CFMF", "BTC", "USD", "CFM", false)));

    Instrument instrument = identity.instrument("BTC-28MAR25-CFMF");
    assertTrue(instrument instanceof FuturesContract);
    FuturesContract contract = (FuturesContract) instrument;
    assertEquals(CurrencyPair.BTC_USD, contract.getCurrencyPair());
    assertEquals("28MAR25-CFMF", contract.getPrompt());
    assertFalse(contract.isPerpetual());
    assertEquals("BTC-28MAR25-CFMF", identity.requireProductId(contract));
  }

  @Test
  public void perpetualMapsToPerpetualFuturesContract() {
    CoinbaseProductIdentity identity =
        CoinbaseProductIdentity.build(
            Collections.singletonList(future("BTC-PERP-INTX", "BTC", "USD", "INTX", true)));

    Instrument instrument = identity.instrument("BTC-PERP-INTX");
    assertTrue(instrument instanceof FuturesContract);
    FuturesContract contract = (FuturesContract) instrument;
    assertEquals("PERP", contract.getPrompt());
    assertTrue(contract.isPerpetual());
    assertEquals("BTC-PERP-INTX", identity.requireProductId(contract));
    assertEquals(contract, identity.perpetualInstrument("BTC-PERP"));
    Product product = identity.product("BTC-PERP-INTX");
    assertNotNull(product);
    assertTrue(product.perpetual());
    assertEquals("INTX", product.productVenue());
  }

  @Test
  public void opaqueCdeFutureUsesOrdinaryContractAndCatalogPreservesNativeProductId() {
    CoinbaseProductIdentity identity =
        CoinbaseProductIdentity.build(
            Collections.singletonList(
                future("ETP-20DEC30-CDE", "ETH", "USD", "CDE", false)));

    FuturesContract expected =
        new FuturesContract(new CurrencyPair("ETH", "USD"), "ETP-20DEC30-CDE");
    FuturesContract contract = (FuturesContract) identity.instrument("ETP-20DEC30-CDE");

    assertEquals(expected, contract);
    assertEquals(expected.hashCode(), contract.hashCode());
    HashMap<FuturesContract, String> nativeIdsByContract = new HashMap<>();
    nativeIdsByContract.put(expected, "ETP-20DEC30-CDE");
    assertEquals("ETP-20DEC30-CDE", nativeIdsByContract.get(contract));
    assertEquals("ETP-20DEC30-CDE", identity.requireProductId(contract));
    assertEquals("ETP-20DEC30-CDE", identity.requireProductId(expected));
    assertEquals("CDE", identity.product("ETP-20DEC30-CDE").productVenue());
  }

  @Test
  public void duplicateNativeProductIdsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CoinbaseProductIdentity.build(
                Arrays.asList(
                    spot("DUPLICATE", "BTC", "USD"),
                    future("DUPLICATE", "ETH", "USD", "CDE", false))));
  }

  @Test
  public void ambiguousInstrumentsAreRejectedNotSilentlyResolved() {
    // Two distinct product ids (different venues) produce the same instrument.
    CoinbaseProductIdentity identity =
        CoinbaseProductIdentity.build(
            Arrays.asList(
                future("BTC-PERP", "BTC", "USD", "CFM", true),
                future("BTC-PERP-INTX", "BTC", "USD", "INTX", true),
                future("BTC-PERP-CDE", "BTC", "USD", "CDE", true)));

    FuturesContract instrument = new FuturesContract(CurrencyPair.BTC_USD, "PERP");
    assertNull(identity.productId(instrument));
    AmbiguousMappingException exception =
        assertThrows(AmbiguousMappingException.class, () -> identity.requireProductId(instrument));
    assertTrue(exception.getMessage().contains("unambiguous"));
    // Native ids remain addressable through the raw registry.
    assertNotNull(identity.product("BTC-PERP-INTX"));
  }

  @Test
  public void productsWithoutCurrencyMetadataStayUnmapped() {
    CoinbaseProductIdentity identity =
        CoinbaseProductIdentity.build(
            Collections.singletonList(
                new CoinbaseProductResponse("UNKNOWN-PRODUCT", null, null, null, null, null)));

    assertNotNull(identity.product("UNKNOWN-PRODUCT"));
    assertThrows(AmbiguousMappingException.class, () -> identity.instrument("UNKNOWN-PRODUCT"));
  }

  @Test
  public void unknownProductIdIsRejected() {
    CoinbaseProductIdentity identity = CoinbaseProductIdentity.build(Collections.emptyList());
    AmbiguousMappingException exception =
        assertThrows(AmbiguousMappingException.class, () -> identity.instrument("BTC-USD"));
    assertTrue(exception.getMessage().contains("unknown product id"));
  }

  @Test
  public void discoveryQueriesSpotAndFutureCatalogs() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listProducts(
            any(ParamsDigest.class),
            eq(250),
            eq(0),
            eq("SPOT"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(
            new CoinbaseProductsResponse(
                Collections.singletonList(spot("BTC-USD", "BTC", "USD"))));
    when(authenticated.listProducts(
            any(ParamsDigest.class),
            eq(250),
            eq(0),
            eq("FUTURE"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(
            new CoinbaseProductsResponse(
                Collections.singletonList(
                    future("BTC-PERP-INTX", "BTC", "USD", "INTX", true))));

    CoinbaseProductIdentity identity = CoinbaseProductIdentity.discover(rawWith(authenticated));

    assertEquals(2, identity.products().size());
    assertEquals("BTC-USD", identity.requireProductId(CurrencyPair.BTC_USD));
    assertEquals(
        "BTC-PERP-INTX",
        identity.requireProductId(new FuturesContract(CurrencyPair.BTC_USD, "PERP")));
  }

  @Test
  public void discoveryRejectsMissingProductCollection() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listProducts(
            any(ParamsDigest.class),
            eq(250),
            eq(0),
            eq("SPOT"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(null);

    ExchangeException exception =
        assertThrows(
            ExchangeException.class,
            () -> CoinbaseProductIdentity.discover(rawWith(authenticated)));

    assertTrue(exception.getMessage().contains("omitted products"));
  }

  @Test
  public void discoveryRejectsReplayedOffsetPage() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    List<CoinbaseProductResponse> repeatedPage = new ArrayList<>();
    for (int index = 0; index < CoinbaseProductIdentity.DISCOVERY_PAGE_SIZE; index++) {
      repeatedPage.add(spot("ASSET" + index + "-USD", "ASSET" + index, "USD"));
    }
    CoinbaseProductsResponse response = new CoinbaseProductsResponse(repeatedPage);
    when(authenticated.listProducts(
            any(ParamsDigest.class),
            eq(250),
            eq(0),
            eq("SPOT"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(response);
    when(authenticated.listProducts(
            any(ParamsDigest.class),
            eq(250),
            eq(250),
            eq("SPOT"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(response);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CoinbaseProductIdentity.discover(rawWith(authenticated)));

    assertTrue(exception.getMessage().contains("repeated product"));
  }

  private static CoinbaseMarketDataServiceRaw rawWith(CoinbaseAuthenticated authenticated) {
    return new CoinbaseMarketDataServiceRaw(
        mock(Exchange.class), authenticated, mock(ParamsDigest.class));
  }
}
