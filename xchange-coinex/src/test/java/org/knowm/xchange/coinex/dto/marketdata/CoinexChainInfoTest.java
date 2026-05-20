package org.knowm.xchange.coinex.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinex.config.CoinexJacksonObjectMapperFactory;
import org.knowm.xchange.coinex.dto.CoinexResponse;

class CoinexChainInfoTest {

  @Test
  void handlesExplorerUrlTemplateValue() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    new CoinexJacksonObjectMapperFactory().configureObjectMapper(mapper);

    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/org/knowm/xchange/coinex/marketdata/all-deposit-withdraw-config-template-url.json")) {
      CoinexResponse<List<CoinexChainInfo>> response =
          mapper.readValue(inputStream, new TypeReference<CoinexResponse<List<CoinexChainInfo>>>() {});

      URI explorerAssetUrl = response.getData().getFirst().getChains().getFirst().getExplorerAssetUrl();
      assertThat(explorerAssetUrl)
          .isEqualTo(
              URI.create(
                  "https://eosauthority.com/tokens/%7Bidentity%7D?network=eosA-eos-core.vaulta"));
    }
  }
}
