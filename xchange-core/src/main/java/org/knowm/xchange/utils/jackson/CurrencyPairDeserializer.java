package org.knowm.xchange.utils.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

public class CurrencyPairDeserializer extends JsonDeserializer<CurrencyPair> {

  public static CurrencyPair getCurrencyPairFromString(String currencyPairString) {

    if (currencyPairString == null || currencyPairString.isEmpty()) {
      return null;
    }

    /*
     * Performance optimization: Single pass over the string determines case mix and counts
     * non-alphanumeric separator characters, avoiding regex compilation, Matcher instantiation,
     * ArrayList creation, and String[] allocations during high-frequency deserialization.
     */
    boolean hasLower = false;
    boolean hasUpper = false;
    int separatorIndex = -1;
    int nonAlphaCount = 0;
    final int len = currencyPairString.length();

    for (int i = 0; i < len; i++) {
      char c = currencyPairString.charAt(i);
      if (c >= 'a' && c <= 'z') {
        hasLower = true;
      } else if (c >= 'A' && c <= 'Z') {
        hasUpper = true;
      } else if (c < '0' || c > '9') {
        nonAlphaCount++;
        separatorIndex = i;
      }
    }

    final boolean isMixedCase = hasLower && hasUpper;
    if (!isMixedCase) {
      currencyPairString = currencyPairString.toUpperCase();
    }

    if (nonAlphaCount == 1 && separatorIndex > 0 && separatorIndex < len - 1) {
      return new CurrencyPair(
          currencyPairString.substring(0, separatorIndex),
          currencyPairString.substring(separatorIndex + 1));
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
