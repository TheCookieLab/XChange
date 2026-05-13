## [![XChange](https://raw.githubusercontent.com/TheCookieLab/XChange/main/etc/XChange_64_64.png)](https://github.com/TheCookieLab/XChange) XChange

[![Discord](https://img.shields.io/discord/778301671302365256?logo=Discord)](https://discord.gg/HX9MbWZ)
[![Java CI with Maven](https://github.com/TheCookieLab/XChange/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/TheCookieLab/XChange/actions/workflows/maven.yml)

XChange is a Java library for trading and market-data access across many
cryptocurrency exchanges. It provides one consistent API for exchange discovery,
account data, orders, trades, tickers, order books, and streaming market data
where supported.

This repository is a maintained fork focused on production trading reliability,
fast exchange support updates, and a modern Maven release pipeline.

## Install

Release artifacts are published under
[`com.github.thecookielab.xchange`](https://central.sonatype.com/search?q=g:com.github.thecookielab.xchange).
Set `xchange.version` to the latest release from Central, or use
`0.2.0-SNAPSHOT` with the snapshots repository below.

Add `xchange-core` plus the exchange modules you use:

```xml
<properties>
  <xchange.version>0.2.0-SNAPSHOT</xchange.version>
</properties>

<dependencies>
  <dependency>
    <groupId>com.github.thecookielab.xchange</groupId>
    <artifactId>xchange-core</artifactId>
    <version>${xchange.version}</version>
  </dependency>
  <dependency>
    <groupId>com.github.thecookielab.xchange</groupId>
    <artifactId>xchange-XYZ</artifactId>
    <version>${xchange.version}</version>
  </dependency>
</dependencies>
```

If the exchange supports streaming, add its streaming module:

```xml
<dependency>
  <groupId>com.github.thecookielab.xchange</groupId>
  <artifactId>xchange-stream-XYZ</artifactId>
  <version>${xchange.version}</version>
</dependency>
```

For snapshots, add the Central Portal snapshots repository:

```xml
<repository>
  <id>central-portal-snapshots</id>
  <name>Central Portal Snapshots</name>
  <url>https://central.sonatype.com/repository/maven-snapshots/</url>
  <releases>
    <enabled>false</enabled>
  </releases>
  <snapshots>
    <enabled>true</enabled>
  </snapshots>
</repository>
```

## REST API

### Public Market Data

```java
Exchange bitstamp = ExchangeFactory.INSTANCE.createExchange(BitstampExchange.class);
MarketDataService marketDataService = bitstamp.getMarketDataService();
Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
System.out.println(ticker);
```

### Private Account Data

Create an `ExchangeSpecification`, set the exchange-specific credentials, and
pass it to `ExchangeFactory`.

```java
ExchangeSpecification spec = new BitstampExchange().getDefaultExchangeSpecification();
spec.setUserName("34387");
spec.setApiKey("a4SDmpl9s6xWJS5fkKRT6yn41vXuY0AM");
spec.setSecretKey("sisJixU6Xd0d1yr6w02EHCb9UwYzTNuj");

Exchange bitstamp = ExchangeFactory.INSTANCE.createExchange(spec);
AccountInfo accountInfo = bitstamp.getAccountService().getAccountInfo();
System.out.println(accountInfo);
```

Credential fields vary by exchange. Some exchanges require only an API key and
secret; others also require fields such as username or passphrase. See the
[FAQ](https://github.com/TheCookieLab/XChange/wiki/Frequently-Asked-Questions)
for configuration examples.

## WebSocket API

Use `StreamingExchange` for real-time subscriptions when an exchange module
supports streaming.

```java
StreamingExchange exchange =
    StreamingExchangeFactory.INSTANCE.createExchange(BitstampStreamingExchange.class);

exchange.connect().blockingAwait();

Disposable trades =
    exchange.getStreamingMarketDataService()
        .getTrades(CurrencyPair.BTC_USD)
        .subscribe(
            trade -> LOG.info("Trade: {}", trade),
            error -> LOG.error("Trade subscription failed", error));

Disposable orderBook =
    exchange.getStreamingMarketDataService()
        .getOrderBook(CurrencyPair.BTC_USD)
        .subscribe(book -> LOG.info("Order book: {}", book));

Thread.sleep(20000);

trades.dispose();
orderBook.dispose();
exchange.disconnect().blockingAwait();
```

Streaming authentication uses the same `ExchangeSpecification` model as the REST
API when the exchange supports private streaming channels.

## Build and Validation

| Task | Command |
| --- | --- |
| Run unit tests | `mvn -B clean test` |
| Run unit and integration tests | `mvn -B clean verify -DskipIntegrationTests=false` |
| Install locally | `mvn -B clean install` |
| Build one module | `mvn -B -pl <module> -am test` |
| Create aggregate Javadocs | `mvn -B javadoc:aggregate` |
| Generate dependency tree | `mvn -B dependency:tree` |
| Check dependency updates | `mvn -B versions:display-dependency-updates versions:display-plugin-updates versions:display-property-updates` |
| Run vulnerability audit | `mvn -B org.owasp:dependency-check-maven:check -DskipIntegrationTests=true` |
| Format Java | `mvn -B com.spotify.fmt:fmt-maven-plugin:format` |
| Sort POMs | `mvn -B com.github.ekryd.sortpom:sortpom-maven-plugin:sort` |

## Dependency Maintenance

Dependency update reports use
`config/dependency-updates/version-rules.xml` to reject prerelease candidates.
Treat "latest" as the latest stable Maven Central release. Do not adopt alpha,
beta, milestone, RC, preview, early-access, snapshot, or classifier-specific
variants unless a security advisory has no stable fix.

Shared dependency and plugin versions belong in the root `pom.xml` properties and
dependency management. Keep module-local versions only when a module intentionally
differs from the shared build, and document that reason in the module POM.

Before closing dependency work:

1. Run the Maven Versions report.
2. Review the dependency tree for convergence problems.
3. Cross-check Dependabot alerts or run the OWASP audit.
4. Run affected module tests.
5. Run `mvn -B clean install` for full-project dependency changes.

## Integration Status

| Exchange | Status |
| --- | --- |
| bitfinex | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/bitfinex.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/bitfinex.yaml) |
| bitget | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/bitget.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/bitget.yaml) |
| bitmex | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/bitmex.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/bitmex.yaml) |
| coinbase | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/coinbase.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/coinbase.yaml) |
| coinex | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/coinex.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/coinex.yaml) |
| deribit | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/deribit.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/deribit.yaml) |
| gate.io | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/gateio-v4.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/gateio-v4.yaml) |
| kraken | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/kraken.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/kraken.yaml) |
| mexc | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/mexc.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/mexc.yaml) |
| stream-bitfinex | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/stream-bitfinex.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/stream-bitfinex.yaml) |
| stream-coinbase | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/stream-coinbase.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/stream-coinbase.yaml) |
| stream-deribit | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/stream-deribit.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/stream-deribit.yaml) |
| stream-kraken-v2 | [![status](https://github.com/TheCookieLab/XChange/actions/workflows/stream-kraken-v2.yaml/badge.svg)](https://github.com/TheCookieLab/XChange/actions/workflows/stream-kraken-v2.yaml) |

## Project Resources

- [Wiki](https://github.com/TheCookieLab/XChange/wiki)
- [FAQ](https://github.com/TheCookieLab/XChange/wiki/Frequently-Asked-Questions)
- [Exchange Support](https://github.com/TheCookieLab/XChange/wiki/Exchange-support)
- [Design Notes](https://github.com/TheCookieLab/XChange/wiki/Design-Notes)
- [New Implementation Best Practices](https://github.com/TheCookieLab/XChange/wiki/New-Implementation-Best-Practices)
- [Code Style](https://github.com/TheCookieLab/XChange/wiki/Code-Style)
- [Issues](https://github.com/TheCookieLab/XChange/issues)
- [Discord](https://discord.gg/HX9MbWZ)

## Contributing

Before submitting a new exchange implementation, read
[New Implementation Best Practices](https://github.com/TheCookieLab/XChange/wiki/New-Implementation-Best-Practices).

Thanks to all [contributors](CONTRIBUTORS) who have helped build XChange.

## Fork Attribution

This repository is a maintained fork of the original XChange project:

- Upstream repository: [knowm/XChange](https://github.com/knowm/XChange)
- Original project site: [knowm.org/open-source/xchange](http://knowm.org/open-source/xchange)

This fork preserves upstream MIT license and copyright notices. Release notes
include an **Upstream Credits** section for upstream PRs and commits included in
each release.

## Maintained by TheCookieLab

XChange is maintained by [TheCookieLab](https://github.com/TheCookieLab).
