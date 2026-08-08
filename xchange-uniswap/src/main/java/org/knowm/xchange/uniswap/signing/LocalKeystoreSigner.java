package org.knowm.xchange.uniswap.signing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;

/**
 * Signs EIP-1559 transactions with the key from a password-encrypted Web3 V3 keystore.
 *
 * <p>The keystore stays on the XChange host; the password is obtained through a {@link
 * SecretProvider} at signing time and never persisted, logged, or placed in an {@code
 * ExchangeSpecification}. The configured wallet address is verified against the keystore key on
 * first use (fail closed).
 */
public final class LocalKeystoreSigner {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A signed transaction: the RLP bytes for broadcast and the locally computed transaction hash. */
  public record SignedTransaction(byte[] signedBytes, String hashHex) {

    /** Hex form of {@link #signedBytes()} for {@code eth_sendRawTransaction}. */
    public String signedHex() {
      return "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(signedBytes);
    }
  }

  private final Path keystorePath;
  private final SecretProvider secretProvider;
  private final String expectedAddress;
  private final Object lock = new Object();
  private ECKeyPair keyPair;
  private String address;

  /**
   * @param keystorePath path of the encrypted V3 keystore
   * @param secretProvider password source
   * @param expectedAddress normalized wallet address the keystore must match
   */
  public LocalKeystoreSigner(Path keystorePath, SecretProvider secretProvider, String expectedAddress) {
    this.keystorePath = keystorePath;
    this.secretProvider = secretProvider;
    this.expectedAddress = expectedAddress;
  }

  /** The keystore's address (loaded and verified on first use). */
  public String address() {
    keyPair();
    return address;
  }

  /**
   * Signs an EIP-1559 transaction with the configured chain id and returns the encoded bytes plus
   * the precomputed transaction hash.
   */
  public SignedTransaction sign(RawTransaction rawTransaction, long chainId) {
    ECKeyPair key = keyPair();
    byte[] signed = TransactionEncoder.signMessage(rawTransaction, chainId, Credentials.create(key));
    return new SignedTransaction(
        signed, "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(Hash.sha3(signed)));
  }

  private ECKeyPair keyPair() {
    synchronized (lock) {
      if (keyPair == null) {
        char[] password = null;
        try {
          password = secretProvider.password();
          WalletFile walletFile;
          try (java.io.InputStream in = Files.newInputStream(keystorePath)) {
            walletFile = MAPPER.readValue(in, WalletFile.class);
          } catch (IOException e) {
            throw new IllegalStateException("cannot read keystore " + keystorePath + ": " + e.getMessage(), e);
          }
          try {
            keyPair = Wallet.decrypt(new String(password), walletFile);
          } catch (org.web3j.crypto.exception.CipherException e) {
            throw new IllegalStateException(
                "cannot decrypt keystore " + keystorePath + " (wrong password?): " + e.getMessage(), e);
          }
          String derived = ("0x" + Keys.getAddress(keyPair)).toLowerCase();
          if (!derived.equals(expectedAddress)) {
            throw new IllegalStateException(
                "keystore " + keystorePath + " belongs to " + derived + " but wallet address is " + expectedAddress);
          }
          address = derived;
        } finally {
          if (password != null) {
            Arrays.fill(password, '\0');
          }
        }
      }
      return keyPair;
    }
  }

  /**
   * Creates a new password-encrypted Web3 V3 keystore at {@code path} from a fresh random key and
   * writes it with owner-only permissions.
   *
   * @return the created wallet address (checksummed)
   */
  public static String createKeystore(Path path, char[] password) throws Exception {
    if (Files.exists(path)) {
      throw new IllegalStateException("keystore already exists: " + path);
    }
    ECKeyPair keyPair = Keys.createEcKeyPair();
    WalletFile walletFile = Wallet.createStandard(new String(password), keyPair);
    byte[] json = MAPPER.writeValueAsBytes(walletFile);
    Files.write(
        path,
        json,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    try {
      Files.setPosixFilePermissions(
          path,
          java.util.Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      // non-POSIX filesystem: rely on the creating process's umask
    }
    return Keys.getAddress(keyPair);
  }
}
