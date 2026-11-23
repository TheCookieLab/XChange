package org.knowm.xchange.utils.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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

    // The former case-detection regexes could not match across line terminators.
    boolean hasLower = false;
    boolean hasUpper = false;
    boolean hasLineTerminator = false;
    for (int i = 0; i < currencyPairString.length(); i++) {
      char c = currencyPairString.charAt(i);
      if (c >= 'a' && c <= 'z') {
        hasLower = true;
      } else if (c >= 'A' && c <= 'Z') {
        hasUpper = true;
      } else if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
        hasLineTerminator = true;
      }
    }
    if (!hasLower || !hasUpper || hasLineTerminator) {
      currencyPairString = currencyPairString.toUpperCase();
    }

    // Match split("[^a-zA-Z0-9]"): it discards trailing empty segments. Scan only
    // after case conversion, which can change both characters and string length.
    int end = currencyPairString.length();
    while (end > 0 && !isAsciiAlphanumeric(currencyPairString.charAt(end - 1))) {
      end--;
    }
    int separatorIndex = -1;
    for (int i = 0; i < end; i++) {
      if (!isAsciiAlphanumeric(currencyPairString.charAt(i))) {
        if (separatorIndex >= 0) {
          separatorIndex = -2;
          break;
        }
        separatorIndex = i;
      }
    }
    if (separatorIndex >= 0) {
      return new CurrencyPair(
          currencyPairString.substring(0, separatorIndex),
          currencyPairString.substring(separatorIndex + 1, end));
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

  private static boolean isAsciiAlphanumeric(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9');
  }

  @Override
  public CurrencyPair deserialize(JsonParser jsonParser, final DeserializationContext ctxt)
      throws IOException {

    if (jsonParser.hasToken(JsonToken.VALUE_STRING)) {
      return getCurrencyPairFromString(jsonParser.getText());
    }
    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
    return getCurrencyPairFromString(node.asText());
  }
}
