import java.nio.file.Path;
import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.currency.CurrencyPair;

/** Runs against downloaded jars, without opening an exchange connection. */
public final class ReleaseSmoke {
  public static void main(String[] args) throws Exception {
    String version = args[0];
    CurrencyPair pair = new CurrencyPair("BTC", "USD");
    if (!pair.equals(CurrencyPair.BTC_USD) || !"BTC/USD".equals(pair.toString())) {
      throw new IllegalStateException("Published CurrencyPair behavior is incompatible");
    }
    for (Class<?> type : new Class<?>[] {CurrencyPair.class, BitstampExchange.class}) {
      Path source = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (!source.getFileName().toString().endsWith("-" + version + ".jar")) {
        throw new IllegalStateException("Expected published " + version + " jar for " + type + ": " + source);
      }
    }
  }
}
