package org.knowm.xchange.derivative;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.utils.ObjectMapperHelper;

class FuturesContractTest {

  @Test
  void serializeDeserialize() throws Exception {
    FuturesContract contractExpire = new FuturesContract("XBT/USD/200925");
    FuturesContract jsonCopy = ObjectMapperHelper.viaJSON(contractExpire);
    assertThat(jsonCopy).isEqualTo(contractExpire);

    FuturesContract contractPerpetual = new FuturesContract("XBT/USD/perpetual");
    FuturesContract jsonCopy2 = ObjectMapperHelper.viaJSON(contractPerpetual);
    assertThat(jsonCopy2).isEqualTo(contractPerpetual);
  }

  @Test
  void isPerpetual() {
    assertThat(new FuturesContract("BTC/USDT/PERP").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/perp").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/Perp").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/SWAP").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/swap").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/PERPETUAL").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/perpetual").isPerpetual()).isTrue();
    assertThat(new FuturesContract("BTC/USDT/Perpetual").isPerpetual()).isTrue();

    assertThat(new FuturesContract("BTC/USDT/200925").isPerpetual()).isFalse();
    assertThat(new FuturesContract("BTC/USDT/25DEC31").isPerpetual()).isFalse();
    assertThat(new FuturesContract("BTC/USDT/PERPETUALS").isPerpetual()).isFalse();
    assertThat(new FuturesContract("BTC/USDT/SWAPS").isPerpetual()).isFalse();
    assertThat(new FuturesContract("BTC/USDT/PERP_FUTURE").isPerpetual()).isFalse();
    assertThat(new FuturesContract(null, (String) null).isPerpetual()).isFalse();
  }
}
