package org.knowm.xchange.binance.service;

import static org.knowm.xchange.binance.BinanceExchange.EXCHANGE_TYPE;

import java.io.IOException;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceAuthenticated;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.BinanceFuturesAuthenticated;
import org.knowm.xchange.binance.auth.BinanceSigning;
import org.knowm.xchange.binance.coinm.BinanceCoinmAuthApi;
import org.knowm.xchange.binance.config.BinanceConfiguration;
import org.knowm.xchange.binance.config.BinanceKeyAlgorithm;
import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.dto.ExchangeType;
import org.knowm.xchange.binance.dto.meta.BinanceSystemStatus;
import org.knowm.xchange.binance.portfoliomargin.BinancePortfolioMarginApi;
import org.knowm.xchange.binance.spot.BinanceSpotApi;
import org.knowm.xchange.binance.spot.BinanceSpotAuthApi;
import org.knowm.xchange.binance.usdm.BinanceUsdmApi;
import org.knowm.xchange.binance.usdm.BinanceUsdmAuthApi;
import org.knowm.xchange.binance.wallet.BinanceWalletApi;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.service.BaseResilientExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Base service owning the per-family REST proxies.
 *
 * <p>Each product family is addressed through its explicit narrow interface; the legacy wide
 * facades ({@code binance}, {@code binanceFutures}, {@code inverseBinanceFutures}) remain only
 * for source compatibility during the documented grace period and must not be used by new code.
 */
public class BinanceBaseService extends BaseResilientExchangeService<BinanceExchange> {

  protected final Logger LOG = LoggerFactory.getLogger(getClass());

  protected final String apiKey;
  protected final ParamsDigest signatureCreator;

  /** Public Spot market-data client. */
  protected final BinanceSpotApi binanceSpot;

  /** Authenticated Spot account/trading client. */
  protected final BinanceSpotAuthApi binanceSpotAuth;

  /** Wallet/SAPI client. */
  protected final BinanceWalletApi binanceWallet;

  /** Public USDⓈ-M futures market-data client. */
  protected final BinanceUsdmApi binanceUsdm;

  /** Authenticated USDⓈ-M futures client. */
  protected final BinanceUsdmAuthApi binanceUsdmAuth;

  /** Authenticated COIN-M futures client. */
  protected final BinanceCoinmAuthApi binanceCoinmAuth;

  /** Portfolio Margin client. */
  protected final BinancePortfolioMarginApi binancePortfolioMargin;

  /**
   * @deprecated Legacy wide Spot facade; migrate callers to the family clients.
   */
  @Deprecated protected final BinanceAuthenticated binance;

  /**
   * @deprecated Legacy wide futures facade; migrate callers to the family clients.
   */
  @Deprecated protected BinanceFuturesAuthenticated binanceFutures;

  /**
   * @deprecated Legacy wide inverse-futures facade; migrate callers to the family clients.
   */
  @Deprecated protected BinanceFuturesAuthenticated inverseBinanceFutures;

  protected BinanceBaseService(
      BinanceExchange exchange, ResilienceRegistries resilienceRegistries) {

    super(exchange, resilienceRegistries);
    ExchangeSpecification specification = exchange.getExchangeSpecification();
    this.binance =
        ExchangeRestProxyBuilder.forInterface(BinanceAuthenticated.class, specification).build();
    this.binanceSpot =
        ExchangeRestProxyBuilder.forInterface(BinanceSpotApi.class, specification).build();
    this.binanceSpotAuth =
        ExchangeRestProxyBuilder.forInterface(BinanceSpotAuthApi.class, specification).build();
    this.binanceWallet =
        ExchangeRestProxyBuilder.forInterface(BinanceWalletApi.class, specification).build();
    this.binanceUsdm =
        ExchangeRestProxyBuilder.forInterface(BinanceUsdmApi.class, specification).build();
    this.binanceUsdmAuth =
        ExchangeRestProxyBuilder.forInterface(BinanceUsdmAuthApi.class, specification).build();
    this.binanceCoinmAuth =
        ExchangeRestProxyBuilder.forInterface(BinanceCoinmAuthApi.class, specification).build();
    this.binancePortfolioMargin =
        ExchangeRestProxyBuilder.forInterface(BinancePortfolioMarginApi.class, specification)
            .build();

    BinanceProductFamily family = exchange.getConfiguration().getProductFamily();
    if (specification.getExchangeSpecificParametersItem(EXCHANGE_TYPE) != null) {
      // Legacy selection takes precedence when both are present, to keep existing behavior.
      switch ((ExchangeType) specification.getExchangeSpecificParametersItem(EXCHANGE_TYPE)) {
        case FUTURES:
          family = BinanceProductFamily.USDM;
          break;
        case INVERSE:
          family = BinanceProductFamily.COINM;
          break;
        case PORTFOLIO_MARGIN:
          family = BinanceProductFamily.PORTFOLIO_MARGIN;
          break;
        default:
          break;
      }
    }
    switch (family) {
      case USDM:
      case PORTFOLIO_MARGIN:
        binanceFutures =
            ExchangeRestProxyBuilder.forInterface(BinanceFuturesAuthenticated.class, specification)
                .build();
        break;
      case COINM:
        inverseBinanceFutures =
            ExchangeRestProxyBuilder.forInterface(BinanceFuturesAuthenticated.class, specification)
                .build();
        break;
      default:
        break;
    }

    this.apiKey = specification.getApiKey();
    BinanceConfiguration configuration = exchange.getConfiguration();
    boolean legacyEd25519 = Boolean.TRUE.equals(specification.getExchangeSpecificParametersItem("ed25519"));
    BinanceKeyAlgorithm algorithm =
        configuration.getKeyAlgorithm() == BinanceKeyAlgorithm.HMAC_SHA_256 && legacyEd25519
            ? BinanceKeyAlgorithm.ED25519
            : configuration.getKeyAlgorithm();
    this.signatureCreator =
        BinanceSigning.createDigest(algorithm, specification.getSecretKey());
  }

  public Long getRecvWindow() {
    return exchange.getConfiguration().getRecvWindow();
  }

  public SynchronizedValueFactory<Long> getTimestampFactory() {
    return exchange.getTimestampFactory();
  }

  public BinanceSystemStatus getSystemStatus() throws IOException {
    return decorateApiCall(binanceWallet::systemStatus).call();
  }
}
