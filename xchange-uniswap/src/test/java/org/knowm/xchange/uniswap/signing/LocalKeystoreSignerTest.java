package org.knowm.xchange.uniswap.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;

/** V3 keystore round-trip and EIP-1559 signing (acceptance criterion AC3/AC4). */
class LocalKeystoreSignerTest {

  @TempDir Path tempDir;

  private static final String WALLET = "0x1111111111111111111111111111111111111111";

  @Test
  void createsAndSignsFromAnEncryptedKeystore() throws Exception {
    char[] password = "correct horse battery staple".toCharArray();
    Path keystore = TestSignerFixtures.keystore(tempDir, password);
    String address = TestSignerFixtures.derivedAddress(keystore, password);
    LocalKeystoreSigner signer = new LocalKeystoreSigner(keystore, () -> password, address);

    assertThat(signer.address()).isEqualTo(address);

    RawTransaction raw =
        RawTransaction.createTransaction(
            1L, BigInteger.TEN, BigInteger.valueOf(21000), WALLET, BigInteger.ZERO, "0x", BigInteger.ONE, BigInteger.valueOf(21_000_000_000L));
    LocalKeystoreSigner.SignedTransaction signed = signer.sign(raw, 1L);

    // the precomputed hash is keccak of the signed bytes
    assertThat(signed.hashHex())
        .isEqualTo("0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(Hash.sha3(signed.signedBytes())));
    // the signature recovers to the keystore address
    byte[] unsigned =
        org.web3j.rlp.RlpEncoder.encode(
            new org.web3j.rlp.RlpList(org.web3j.crypto.TransactionEncoder.asRlpValues(raw, null)));
    byte[] typed = new byte[unsigned.length + 1];
    typed[0] = 0x02;
    System.arraycopy(unsigned, 0, typed, 1, unsigned.length);
    byte[] messageHash = Hash.sha3(typed);
    List<byte[]> rlp = decodeRlp(signed.signedBytes());
    // items: chainId, nonce, maxPriority, maxFee, gasLimit, to, value, data, accessList,
    // yParity, r, s
    byte[] r = org.web3j.utils.Numeric.toBytesPadded(new BigInteger(1, rlp.get(10)), 32);
    byte[] s = org.web3j.utils.Numeric.toBytesPadded(new BigInteger(1, rlp.get(11)), 32);
    // yParity is RLP-encoded as a single byte or an empty string for zero; web3j recovery
    // expects the legacy 27/28 header
    byte v = (byte) ((rlp.get(9).length == 1 ? rlp.get(9)[0] : 0) + 27);
    BigInteger recoveredKey =
        org.web3j.crypto.Sign.signedMessageHashToKey(messageHash, new org.web3j.crypto.Sign.SignatureData(v, r, s));
    assertThat("0x" + org.web3j.crypto.Keys.getAddress(recoveredKey).toLowerCase()).isEqualTo(address);
  }

  @Test
  void rejectsAWrongPassword() throws Exception {
    char[] password = "right password".toCharArray();
    Path keystore = TestSignerFixtures.keystore(tempDir, password);
    LocalKeystoreSigner signer = new LocalKeystoreSigner(keystore, () -> "wrong password".toCharArray(), TestSignerFixtures.derivedAddress(keystore, password));
    assertThatThrownBy(() -> signer.address()).isInstanceOf(IllegalStateException.class).hasMessageContaining("decrypt");
  }

  @Test
  void rejectsKeystoreThatDoesNotMatchTheConfiguredWallet() throws Exception {
    char[] password = "right password".toCharArray();
    Path keystore = TestSignerFixtures.keystore(tempDir, password);
    LocalKeystoreSigner signer = new LocalKeystoreSigner(keystore, () -> password, WALLET);
    assertThatThrownBy(() -> signer.address())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("belongs to");
  }

  @Test
  void keystoreFileIsOwnerOnly() throws Exception {
    char[] password = "right password".toCharArray();
    Path keystore = TestSignerFixtures.keystore(tempDir, password);
    java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Files.getPosixFilePermissions(keystore);
    assertThat(permissions)
        .doesNotContain(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
        .doesNotContain(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
        .doesNotContain(java.nio.file.attribute.PosixFilePermission.GROUP_READ)
        .doesNotContain(java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
  }

  /** Minimal RLP decoder for a signed EIP-1559 transaction (type byte 0x02 + 11-item list). */
  private static java.util.List<byte[]> decodeRlp(byte[] payload) {
    int cursor = 1; // skip the 0x02 type byte
    int prefix = payload[cursor] & 0xff;
    cursor++;
    int listLength;
    if (prefix <= 0xf7) {
      listLength = prefix - 0xc0;
    } else {
      int lenBytes = prefix - 0xf7;
      listLength = new BigInteger(1, java.util.Arrays.copyOfRange(payload, cursor, cursor + lenBytes)).intValueExact();
      cursor += lenBytes;
    }
    int end = cursor + listLength;
    java.util.List<byte[]> items = new java.util.ArrayList<>();
    while (cursor < end) {
      int itemPrefix = payload[cursor] & 0xff;
      cursor++;
      int length;
      if (itemPrefix < 0x80) {
        items.add(new byte[] {payload[cursor - 1]});
        continue;
      } else if (itemPrefix <= 0xb7) {
        length = itemPrefix - 0x80;
      } else if (itemPrefix <= 0xbf) {
        int lenBytes = itemPrefix - 0xb7;
        length = new BigInteger(1, java.util.Arrays.copyOfRange(payload, cursor, cursor + lenBytes)).intValueExact();
        cursor += lenBytes;
      } else if (itemPrefix <= 0xf7) {
        // nested list (the EIP-1559 access list, empty in our transactions)
        length = itemPrefix - 0xc0;
      } else {
        int lenBytes = itemPrefix - 0xf7;
        length = new BigInteger(1, java.util.Arrays.copyOfRange(payload, cursor, cursor + lenBytes)).intValueExact();
        cursor += lenBytes;
      }
      items.add(java.util.Arrays.copyOfRange(payload, cursor, cursor + length));
      cursor += length;
    }
    return items;
  }

  private static final class TestSignerFixtures {
    static Path keystore(Path dir, char[] password) throws Exception {
      Path path = dir.resolve("wallet.json");
      LocalKeystoreSigner.createKeystore(path, password);
      return path;
    }
    static String derivedAddress(Path keystore, char[] password) throws Exception {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      try (java.io.InputStream in = Files.newInputStream(keystore)) {
        org.web3j.crypto.WalletFile walletFile = mapper.readValue(in, org.web3j.crypto.WalletFile.class);
        return ("0x" + org.web3j.crypto.Keys.getAddress(org.web3j.crypto.Wallet.decrypt(new String(password), walletFile)))
            .toLowerCase();
      }
    }
  }
}
