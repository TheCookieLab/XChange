package org.knowm.xchange.cryptocom.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.client.ResilienceUtils;
import org.knowm.xchange.cryptocom.CryptoCom;
import org.knowm.xchange.cryptocom.CryptoComDigest;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.service.BaseResilientExchangeService;

public class CryptoComBaseService extends BaseResilientExchangeService<CryptoComExchange> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Map<Class<?>, JavaType> LIST_TYPES = new ConcurrentHashMap<>();

  protected final CryptoCom cryptoCom;

  protected CryptoComBaseService(
      CryptoComExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.cryptoCom = exchange.getCryptoCom();
  }

  /** Builds and signs the request envelope for a private Crypto.com Exchange v1 call. */
  protected CryptoComRequest buildRequest(String method, Map<String, Object> params) {
    long id = exchange.nextRequestId();
    long nonce = System.currentTimeMillis();
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    String apiSecret = exchange.getExchangeSpecification().getSecretKey();
    if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(apiSecret)) {
      throw new ExchangeSecurityException(
          "Crypto.com API key/secret must be configured to call private endpoint '"
              + method
              + "'");
    }
    return CryptoComDigest.sign(
        method, id, nonce, apiKey, apiSecret, params == null ? Collections.emptyMap() : params);
  }

  /**
   * Executes an API call through the exchange resilience chain, attaching the per-method rate
   * limiter only when the exchange-level {@link org.knowm.xchange.cryptocom.CryptoComRatePolicy}
   * configured one for {@code apiMethod}. Calls to methods without a policy entry run exactly as
   * before (no implicit limiting).
   */
  protected <T> T apiCall(String apiMethod, ResilienceUtils.CallableApi<T> callable)
      throws IOException {
    ResilienceUtils.DecorateCallableApi<T> decorated = decorateApiCall(callable);
    if (exchange.isMethodRateLimited(apiMethod)) {
      decorated = decorated.withRateLimiter(rateLimiter(apiMethod));
    }
    return decorated.call();
  }

  protected <T> List<T> toList(JsonNode node, Class<T> elementType) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Collections.emptyList();
    }
    JavaType listType =
        LIST_TYPES.computeIfAbsent(
            elementType, type -> MAPPER.getTypeFactory().constructCollectionType(List.class, type));
    return MAPPER.convertValue(node, listType);
  }

  protected <T> T toObject(JsonNode node, Class<T> type) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    return MAPPER.convertValue(node, type);
  }

  /** Converts the {@code result.data} array of a response envelope, tolerating a missing result. */
  protected <T> List<T> getDataList(CryptoComResponse response, Class<T> elementType) {
    JsonNode result = response.getResult();
    return toList(result == null ? null : result.get("data"), elementType);
  }

  protected <T> List<T> orEmpty(List<T> list) {
    return list == null ? Collections.emptyList() : list;
  }

  /**
   * Page fetch callback used by {@link #fetchPagesBounded}; may throw {@link IOException} which is
   * propagated to the caller.
   */
  @FunctionalInterface
  public interface PageFetcher<T> {
    List<T> fetch(int page, int pageSize) throws IOException;
  }

  /**
   * Bounded history paging honouring the caller limit and the provider continuation model.
   *
   * <p>Pages are fetched 1..N with {@code pageSize} rows each, stopping as soon as any of the
   * following holds: the caller's {@code callerLimit} is reached, an empty page is returned, a page
   * exactly repeats the previous page (no provider progress), or {@code maxPages} pages have been
   * consumed. The provider never sees a page beyond {@code maxPages}, so runaway loops are
   * impossible even when a continuation cursor is misbehaving.
   */
  protected <T> List<T> fetchPagesBounded(
      int maxPages, int pageSize, Integer callerLimit, PageFetcher<T> pageFetcher)
      throws IOException {
    List<T> collected = new ArrayList<>();
    List<T> previousPage = null;
    for (int page = 1; page <= maxPages; page++) {
      List<T> pageRows = pageFetcher.fetch(page, pageSize);
      if (pageRows == null || pageRows.isEmpty()) {
        break; // provider reports no further rows
      }
      if (pageRows.equals(previousPage)) {
        break; // repeated page: exhausted or cursor not advancing
      }
      collected.addAll(pageRows);
      if (callerLimit != null && collected.size() >= callerLimit) {
        // exact cap, even when the provider's page over-delivers
        if (collected.size() > callerLimit) {
          return new ArrayList<>(collected.subList(0, callerLimit));
        }
        break; // caller limit honoured, no over-fetch
      }
      if (pageRows.size() < pageSize) {
        break; // short page means the provider has no more rows
      }
      previousPage = pageRows;
    }
    return collected;
  }
}
