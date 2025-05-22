package info.bitrich.xchangestream.bybit.example;

import info.bitrich.xchangestream.bybit.BybitStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;

import java.io.IOException;
import java.util.Properties;

import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE;

public class BaseBybitExchange {
    public static StreamingExchange connect(BybitCategory category, boolean withAuth) {
        ExchangeSpecification exchangeSpecification =
                new BybitStreamingExchange().getDefaultExchangeSpecification();

        if (withAuth) {
            Properties properties = new Properties();
            try {
                properties.load(BaseBybitExchange.class.getResourceAsStream("/secret.keys"));
                // Enter your authentication details here to run private endpoint tests
                final String API_KEY =
                        (properties.getProperty("apikey") == null)
                                ? System.getenv("bybit_apikey")
                                : properties.getProperty("apikey");
                final String SECRET_KEY =
                        (properties.getProperty("secret") == null)
                                ? System.getenv("bybit_secretkey")
                                : properties.getProperty("secret");
                exchangeSpecification.setApiKey(API_KEY);
                exchangeSpecification.setSecretKey(SECRET_KEY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
            exchangeSpecification.setExchangeSpecificParametersItem(
                    SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.UNIFIED);
            exchangeSpecification.setExchangeSpecificParametersItem(
                    BybitStreamingExchange.EXCHANGE_TYPE, category);
            exchangeSpecification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
            StreamingExchange exchange =
                    StreamingExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
            exchange.connect().blockingAwait();
            return exchange;
        }
    }
