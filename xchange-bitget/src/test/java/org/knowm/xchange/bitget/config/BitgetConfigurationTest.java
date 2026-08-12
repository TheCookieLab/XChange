package org.knowm.xchange.bitget.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitget.BitgetExchange;

class BitgetConfigurationTest {

  private static ExchangeSpecification specification() {
    return new ExchangeSpecification(BitgetExchange.class);
  }

  @Test
  void defaults_to_classic_v2() {
    BitgetConfiguration configuration = BitgetConfiguration.from(specification());

    assertThat(configuration.getApiMode()).isEqualTo(BitgetApiMode.CLASSIC_V2);
  }

  @Test
  void null_specification_uses_defaults() {
    BitgetConfiguration configuration = BitgetConfiguration.from(null);

    assertThat(configuration.getApiMode()).isEqualTo(BitgetApiMode.CLASSIC_V2);
  }

  @Test
  void reads_typed_mode() {
    ExchangeSpecification specification = specification();
    specification.setExchangeSpecificParametersItem(
        BitgetConfiguration.API_MODE, BitgetApiMode.UTA_V3);

    BitgetConfiguration configuration = BitgetConfiguration.from(specification);

    assertThat(configuration.getApiMode()).isEqualTo(BitgetApiMode.UTA_V3);
  }

  @Test
  void reads_enum_name_string() {
    ExchangeSpecification specification = specification();
    specification.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, "UTA_V3");

    BitgetConfiguration configuration = BitgetConfiguration.from(specification);

    assertThat(configuration.getApiMode()).isEqualTo(BitgetApiMode.UTA_V3);
  }

  @Test
  void rejects_unknown_mode() {
    ExchangeSpecification specification = specification();
    specification.setExchangeSpecificParametersItem(BitgetConfiguration.API_MODE, "BOGUS");

    assertThatThrownBy(() -> BitgetConfiguration.from(specification))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Bitget_ApiMode")
        .hasMessageContaining("CLASSIC_V2")
        .hasMessageContaining("UTA_V3");
  }

  @Test
  void mode_labels_are_distinct_and_stable() {
    assertThat(BitgetApiMode.CLASSIC_V2.getAccountLabel()).isEqualTo("classic");
    assertThat(BitgetApiMode.CLASSIC_V2.getApiGeneration()).isEqualTo("v2");
    assertThat(BitgetApiMode.UTA_V3.getAccountLabel()).isEqualTo("uta");
    assertThat(BitgetApiMode.UTA_V3.getApiGeneration()).isEqualTo("v3");
  }
}
