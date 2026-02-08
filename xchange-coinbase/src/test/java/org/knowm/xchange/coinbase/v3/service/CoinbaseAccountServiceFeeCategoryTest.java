package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CoinbaseAccountServiceFeeCategoryTest {

  @Test
  public void defaultsToNoFiltersWhenCategoryMissing() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters((String[]) null);
    assertNull(filters.productType());
    assertNull(filters.productVenue());

    CoinbaseAccountService.TransactionSummaryFilters empty =
        CoinbaseAccountService.resolveTransactionSummaryFilters();
    assertNull(empty.productType());
    assertNull(empty.productVenue());
  }

  @Test
  public void spotCategoryMapsToSpotProductType() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters("SPOT");
    assertEquals("SPOT", filters.productType());
    assertNull(filters.productVenue());
  }

  @Test
  public void futuresCategoryNormalizesToFutureProductType() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters("FUTURES");
    assertEquals("FUTURE", filters.productType());
    assertNull(filters.productVenue());
  }

  @Test
  public void intxCategoryMapsToFutureWithIntxVenueByDefault() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters("INTX");
    assertEquals("FUTURE", filters.productType());
    assertEquals("INTX", filters.productVenue());
  }

  @Test
  public void perpetualCategoryMapsToFutureWithIntxVenueByDefault() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters("PERPETUALS");
    assertEquals("FUTURE", filters.productType());
    assertEquals("INTX", filters.productVenue());
  }

  @Test
  public void explicitVenueOverridesDefaultVenue() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters("INTX", "OTHER");
    assertEquals("FUTURE", filters.productType());
    assertEquals("OTHER", filters.productVenue());
  }

  @Test
  public void trimsAndUppercasesCategoryValues() {
    CoinbaseAccountService.TransactionSummaryFilters filters =
        CoinbaseAccountService.resolveTransactionSummaryFilters("  spot  ");
    assertEquals("SPOT", filters.productType());
    assertNull(filters.productVenue());
  }
}

