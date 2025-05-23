package org.knowm.xchange.coinbase.v3;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * <p>Generates ECDSA-signed JWT tokens for authenticating requests to the Coinbase Advanced
 * Trading API. This class handles key pair initialization, JWT generation, and Bearer token
 * construction for REST API requests.</p>
 *
 * <p>Uses the BouncyCastle provider for elliptic curve cryptography operations (P-256 curve) and
 * the Java Cryptographic Extension (JCE) for key pair derivation.</p>
 *
 * @since 1.0
 */

public class CoinbaseV3Digest extends BaseParamsDigest {

  private static final SecureRandom RNG = new SecureRandom();

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private final ECPrivateKey privateKey;
  private final ECPublicKey publicKey;
  private final String keyName;

  /**
   * <p>Initializes a new digest instance with the provided API key name and secret key.</p>
   *
   * <p>Performs the following actions:</p>
   * <ol>
   *   <li>Registers the BouncyCastle security provider if not already present</li>
   *   <li>Parses and validates the EC key pair from the secret key PEM</li>
   *   <li>Stores the public/private keys and key name for JWT generation</li>
   * </ol>
   *
   * @param keyName   API key name (identifies the key in Coinbase API)
   * @param secretKey PEM-encoded private key string (ECDSA P-256 format)
   * @throws Exception If key parsing fails or BouncyCastle provider initialization fails
   * @see #createInstance(String, String)
   */

  private CoinbaseV3Digest(String keyName, String secretKey) throws Exception {
    super(secretKey, HMAC_SHA_256);

    KeyPair kp = loadECKeyPair(normalizePem(secretKey));
    this.publicKey = (ECPublicKey) kp.getPublic();
    this.privateKey = (ECPrivateKey) kp.getPrivate();
    this.keyName = keyName;
  }

  /**
   * Creates a new instance of CoinbaseV3Digest.
   *
   * @param keyName   API key name
   * @param secretKey PEM-encoded private key string
   * @return Initialized digest instance
   * @throws IllegalStateException If key parsing fails or provider initialization fails
   */

  public static CoinbaseV3Digest createInstance(String keyName, String secretKey) {
    if (keyName == null || secretKey == null) {
      Coinbase.LOG.warn("Missing api and/or secret key");
      return null;
    }
    try {
      return new CoinbaseV3Digest(keyName, secretKey);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize CoinbaseV3Digest", e);
    }
  }

  /**
   * <p>Loads an EC key pair from PEM-encoded input, supporting both PEMKeyPair and PrivateKeyInfo
   * formats.</p>
   *
   * <p>For PrivateKeyInfo inputs, derives the public key using the P-256 curve parameters.</p>
   *
   * @param secretKey PEM-encoded private key string
   * @return KeyPair containing the EC private and public keys
   * @throws IOException              If parsing fails
   * @throws GeneralSecurityException If key derivation or conversion fails
   * @throws IllegalArgumentException If input format is unrecognized
   */

  private static KeyPair loadECKeyPair(String secretKey)
      throws IOException, GeneralSecurityException {
    try (PEMParser parser = new PEMParser(new StringReader(secretKey))) {
      Object obj = parser.readObject();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

      ECPrivateKey priv;
      ECPublicKey pub;

      if (obj instanceof PEMKeyPair) {
        KeyPair kp = converter.getKeyPair((PEMKeyPair) obj);
        return kp;
      }

      if (obj instanceof PrivateKeyInfo) {
        // 1) get the private key
        priv = (ECPrivateKey) converter.getPrivateKey((PrivateKeyInfo) obj);

        // 2) derive BC curve + base point
        ECNamedCurveParameterSpec bcSpec = ECNamedCurveTable.getParameterSpec("P-256");
        org.bouncycastle.math.ec.ECPoint Q = bcSpec.getG().multiply(priv.getS()).normalize();

        // 3) convert BC point → JCA point
        java.security.spec.ECPoint w = new java.security.spec.ECPoint(
            Q.getAffineXCoord().toBigInteger(), Q.getAffineYCoord().toBigInteger());

        // 4) build JCA curve spec from BC parameters
        java.security.spec.EllipticCurve curve = new java.security.spec.EllipticCurve(
            new ECFieldFp(bcSpec.getCurve().getField().getCharacteristic()),
            bcSpec.getCurve().getA().toBigInteger(), bcSpec.getCurve().getB().toBigInteger(),
            bcSpec.getSeed());
        java.security.spec.ECParameterSpec jcaSpec = new java.security.spec.ECParameterSpec(curve,
            new java.security.spec.ECPoint(bcSpec.getG().getAffineXCoord().toBigInteger(),
                bcSpec.getG().getAffineYCoord().toBigInteger()), bcSpec.getN(),
            bcSpec.getH().intValue());

        // 5) build the JCA public‐key spec and generate it
        java.security.spec.ECPublicKeySpec pubSpec = new java.security.spec.ECPublicKeySpec(w,
            jcaSpec);
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        pub = (ECPublicKey) kf.generatePublic(pubSpec);

        return new KeyPair(pub, priv);
      }

      throw new IllegalArgumentException("Unknown PEM object: " + obj.getClass());
    }
  }

  /**
   * Generates a random hexadecimal string for use as a nonce in JWT headers.
   *
   * @param bytes Number of bytes to generate (1 byte = 2 hex characters)
   * @return Random hexadecimal string of length 2 * bytes
   */

  private static String randomHex(int bytes) {
    byte[] buf = new byte[bytes];
    RNG.nextBytes(buf);
    StringBuilder sb = new StringBuilder(bytes * 2);
    for (byte b : buf) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /**
   * Normalizes PEM-formatted key strings by:
   * <ul>
   *   <li>Replacing line breaks with standard newline characters</li>
   *   <li>Ensuring consistent header/footer formatting</li>
   *   <li>Removing extraneous whitespace</li>
   * </ul>
   *
   * @param pem Raw PEM string (may contain escaped newlines)
   * @return Normalized PEM string ready for parsing
   */

  private static String normalizePem(String pem) {
    return pem.replace("\\n", "\n")
        .replaceAll("-----BEGIN (.*) KEY-----\\s+", "-----BEGIN $1 KEY-----\n")
        .replaceAll("\\s+\n-----END (.*) KEY-----", "-----END $1 KEY-----");
  }

  /**
   * <p>Generates a JWT token for Coinbase Advanced Trading API authentication using the ES256
   * algorithm.</p>
   *
   * <p>Includes the following claims:</p>
   * <ul>
   *   <li>{@code kid}: API key name</li>
   *   <li>{@code iss}: "cdp" (issuer)</li>
   *   <li>{@code sub}: API key name</li>
   *   <li>{@code nbf}: Not before timestamp (current time)</li>
   *   <li>{@code exp}: Expiration timestamp (2 minutes from now)</li>
   *   <li>{@code uri}: Concatenation of HTTP method and URI path</li>
   *   <li>{@code nonce}: Random 16-byte hex value</li>
   * </ul>
   *
   * @param method HTTP method (e.g., "GET", "POST")
   * @param uri    Host/path combination (e.g., "api.coinbase.com/v3/brokerage/orders")
   * @return Signed JWT string
   */

  private String generateJWT(String method, String uri) {
    long now = Instant.now().getEpochSecond();
    Date nbf = Date.from(Instant.ofEpochSecond(now));
    Date exp = Date.from(Instant.ofEpochSecond(now + 120));

    Algorithm alg = Algorithm.ECDSA256(publicKey, privateKey);

    return JWT.create().withKeyId(keyName).withIssuer("cdp").withSubject(keyName).withNotBefore(nbf)
        .withExpiresAt(exp).withClaim("uri", method + " " + uri)
        .withHeader(Collections.singletonMap("nonce", randomHex(16))).sign(alg);
  }

  /**
   * <p>Generates the Bearer authentication header for REST API requests.</p>
   *
   * <p>Constructs a JWT token containing the request method and path, then formats it as a Bearer
   * token:</p>
   * <pre>"Bearer " + JWT_TOKEN</pre>
   *
   * @param restInvocation Rescu {@link RestInvocation} object containing request metadata
   * @return Bearer token string for authentication header
   */

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String hostAndPath =
        restInvocation.getBaseUrl().replaceFirst("^https?://", "") + restInvocation.getPath();
    return "Bearer " + generateJWT(restInvocation.getHttpMethod(), hostAndPath);
  }
}
