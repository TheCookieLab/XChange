package org.knowm.xchange.cryptocom.dto.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;

/** Maps deterministic provider fixtures for the phase-3 account DTOs. */
public class CryptoComAccountDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void feeRate_mapsProviderFields() throws Exception {
    List<CryptoComFeeRate> rates = readData("fee-rate.json", CryptoComFeeRate.class);

    assertThat(rates).hasSize(1);
    CryptoComFeeRate rate = rates.get(0);
    assertThat(rate.getInstrumentName()).isEqualTo("BTC_USDT");
    assertThat(rate.getEffectiveFeeTier()).isEqualTo(2);
    assertThat(rate.getFeeType()).isEqualTo(1);
    assertThat(rate.getFeeTiers()).hasSize(2);
    CryptoComFeeRate.FeeTier tier = rate.getFeeTiers().get(0);
    assertThat(tier.getTakerFeeRate()).isEqualTo("0.0004");
    assertThat(tier.getMakerFeeRate()).isEqualTo("0.0002");
    assertThat(tier.getTakerEffectiveFeeRate()).isEqualTo("0.00036");
    assertThat(tier.getMakerEffectiveFeeRate()).isEqualTo("0.00018");
    assertThat(tier.getFeeTier()).isEqualTo(2);
  }

  @Test
  public void position_mapsSignedQuantityAndRiskFields() throws Exception {
    List<CryptoComPosition> positions = readData("position.json", CryptoComPosition.class);

    assertThat(positions).hasSize(1);
    CryptoComPosition position = positions.get(0);
    assertThat(position.getAccountId()).isEqualTo("cdef1234-abcd-5678-efgh-1234567890ab");
    assertThat(position.getInstrumentName()).isEqualTo("BTCUSD-PERP");
    assertThat(position.getQuantity()).isEqualTo("-0.5");
    assertThat(position.getPosition()).isEqualTo("SHORT");
    assertThat(position.getCost()).isEqualTo("-20000.0");
    assertThat(position.getOpenPosCost()).isEqualTo("-20000.0");
    assertThat(position.getAllocatedCash()).isEqualTo("1000.0");
    assertThat(position.getMmContribution()).isEqualTo("500.0");
    assertThat(position.getMlContribution()).isEqualTo("250.0");
    assertThat(position.getMarkPrice()).isEqualTo("40500.5");
    assertThat(position.getLastPrice()).isEqualTo("40501.0");
    assertThat(position.getAverageCost()).isEqualTo("40000.0");
    assertThat(position.getSessionUpl()).isEqualTo("-250.25");
    assertThat(position.getUpl()).isEqualTo("250.25");
    assertThat(position.getUplHistory()).isEqualTo("120.5");
    assertThat(position.getClosePrice()).isEqualTo("40200.0");
    assertThat(position.getInsertTime()).isEqualTo(1699999999000L);
    assertThat(position.getUpdateTime()).isEqualTo(1700000001000L);
  }

  @Test
  public void account_mapsMarginRiskModelFields() throws Exception {
    List<CryptoComAccount> accounts = readData("account.json", CryptoComAccount.class);

    assertThat(accounts).hasSize(1);
    CryptoComAccount account = accounts.get(0);
    assertThat(account.getAccountId()).isEqualTo("cdef123456-abcd-5678-efgh-1234567890ab");
    assertThat(account.getAccountType()).isEqualTo("ACCOUNT_TYPE_MARGIN");
    assertThat(account.getMainAccountType()).isEqualTo("ACCOUNT_TYPE_MAIN");
    assertThat(account.getCharacterType()).isEqualTo("CHARACTER_TYPE_MAIN");
    assertThat(account.getMarginRiskModel()).isEqualTo("PORTFOLIO_MARGIN");
    assertThat(account.getCurrency()).isEqualTo("USD");
  }

  @Test
  public void userBalanceHistory_mapsWalletTrailFields() throws Exception {
    List<CryptoComUserBalanceHistoryRecord> records =
        readData("user-balance-history.json", CryptoComUserBalanceHistoryRecord.class);

    assertThat(records).hasSize(1);
    CryptoComUserBalanceHistoryRecord record = records.get(0);
    assertThat(record.getAccountId()).isEqualTo("cdef123456-abcd-5678-efgh-1234567890ab");
    assertThat(record.getEventType()).isEqualTo("TRADE");
    assertThat(record.getInstrumentName()).isEqualTo("BTC_USDT");
    assertThat(record.getAmount()).isEqualTo("-0.001");
    assertThat(record.getBalance()).isEqualTo("1.999");
    assertThat(record.getTransactionTime()).isEqualTo(1700000001000L);
  }

  private <T> List<T> readData(String resource, Class<T> elementType) throws Exception {
    InputStream is = getClass().getResourceAsStream(resource);
    CryptoComResponse response = mapper.readValue(is, CryptoComResponse.class);
    return mapper.convertValue(
        response.getResult().get("data"),
        mapper.getTypeFactory().constructCollectionType(List.class, elementType));
  }
}