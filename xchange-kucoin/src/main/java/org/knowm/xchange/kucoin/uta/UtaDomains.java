package org.knowm.xchange.kucoin.uta;

/**
 * UTA domain identifiers used in structured exception context and endpoint policy.
 *
 * <p>Domains partition the UTA raw API surface per the CF-449 contract: market, account,
 * positions/margin, trade, asset/transfer, and common transport/policy concerns.
 */
public final class UtaDomains {

  public static final String COMMON = "common";
  public static final String MARKET = "market";
  public static final String ACCOUNT = "account";
  public static final String POSITION = "position";
  public static final String MARGIN = "margin";
  public static final String TRADE = "trade";
  public static final String ASSET = "asset";
  public static final String FEE = "fee";

  private UtaDomains() {}
}
