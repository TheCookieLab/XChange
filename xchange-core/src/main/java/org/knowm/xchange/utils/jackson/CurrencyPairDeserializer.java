package org.knowm.xchange.utils.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.regex.Pattern;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

public class CurrencyPairDeserializer extends JsonDeserializer<CurrencyPair> {

  // Pre-compiled pattern to avoid repeated compilation during deserialization
  private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

  public static CurrencyPair getCurrencyPairFromString(String currencyPairString) {

    if (currencyPairString == null || currencyPairString.isEmpty()) {
      return null;
    }

    /*
     * Preserve case if exchange is sending mixed-case, otherwise toUpperCase()
     * Performance optimization: Zero-allocation loop replaces regex matches(".*[a-z]+.*") & matches(".*[A-Z]+.*")
     */
    boolean hasLower = false;
    boolean hasUpper = false;
    for (int i = 0; i < currencyPairString.length(); i++) {
      char c = currencyPairString.charAt(i);
      if (c >= 'a' && c <= 'z') {
        hasLower = true;
      } else if (c >= 'A' && c <= 'Z') {
        hasUpper = true;
      }
      if (hasLower && hasUpper) {
        break;
      }
    }
    final boolean isMixedCase = hasLower && hasUpper;
    if (!isMixedCase) {
      currencyPairString = currencyPairString.toUpperCase();
    }

    /*
     * Assume all symbols are alphanumeric; anything else is a separator
     */
    final String symbols[] = NON_ALPHANUMERIC_PATTERN.split(currencyPairString);
    if (symbols.length == 2) {
      return new CurrencyPair(symbols[0], symbols[1]);
    }

    /*
     * The common case of two 3-character symbols (eg: "BTCUSD")
     */
    if (currencyPairString.length() == 6) {
      final String tradeCurrency = currencyPairString.substring(0, 3);
      final String priceCurrency = currencyPairString.substring(3);
      return new CurrencyPair(tradeCurrency, priceCurrency);
    }

    /*
     * Last-ditch effort to obtain the correct CurrencyPair (eg: "DOGEBTC", "BCBTC", or even "USDEUSD")
     */
    int bestGuess = currencyPairString.length() / 2;
    int bestLength = 0;
    for (int i = 1; i < currencyPairString.length() - 1; ++i) {
      final Currency tradeCurrency =
          Currency.getInstanceNoCreate(currencyPairString.substring(0, i));
      final Currency priceCurrency = Currency.getInstanceNoCreate(currencyPairString.substring(i));
      if (tradeCurrency != null) {
        if (priceCurrency != null) {
          return new CurrencyPair(tradeCurrency, priceCurrency);
        } else if (i > bestLength) {
          bestLength = i;
          bestGuess = i;
        }
      } else if (priceCurrency != null && currencyPairString.length() - i > bestLength) {
        bestLength = currencyPairString.length() - i;
        bestGuess = i;
      }
    }
    final String tradeCurrency = currencyPairString.substring(0, bestGuess);
    final String priceCurrency = currencyPairString.substring(bestGuess);
    return new CurrencyPair(tradeCurrency, priceCurrency);
  }

  @Override
  public CurrencyPair deserialize(JsonParser jsonParser, final DeserializationContext ctxt)
      throws IOException {

    final ObjectCodec oc = jsonParser.getCodec();
    final JsonNode node = oc.readTree(jsonParser);
    final String currencyPairString = node.asText();

    return getCurrencyPairFromString(currencyPairString);
  }
}
