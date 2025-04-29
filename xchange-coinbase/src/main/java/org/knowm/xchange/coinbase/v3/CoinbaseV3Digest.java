package org.knowm.xchange.coinbase.v3;

import static org.knowm.xchange.coinbase.v2.CoinbaseAuthenticated.CB_ACCESS_TIMESTAMP;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.ws.rs.HeaderParam;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.knowm.xchange.service.BaseParamsDigest;
import org.knowm.xchange.utils.DigestUtils;
import si.mazi.rescu.RestInvocation;

public class CoinbaseV3Digest extends BaseParamsDigest {

  static {
    // register BC once for PEM parsing + EC math
    Security.addProvider(new BouncyCastleProvider());
  }

  private CoinbaseV3Digest(String secretKey) {
    super(secretKey, HMAC_SHA_256);
  }

  public static CoinbaseV3Digest createInstance(String secretKey) {
    return secretKey == null ? null : new CoinbaseV3Digest(secretKey);
  }

  /**
   * Load an EC keypair from either a PEMKeyPair or a raw PrivateKeyInfo, deriving the public key on
   * the P-256 curve if necessary.
   */
  private static KeyPair loadECKeyPair(String privateKeyPEM)
      throws IOException, GeneralSecurityException {
    try (PEMParser parser = new PEMParser(new StringReader(privateKeyPEM))) {
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
   * Generate an ES256 JWT for Coinbase Advanced Trading authentication.
   *
   * @param method        HTTP method (e.g. "GET")
   * @param url           Full URL being called
   * @param privateKeyPEM The PEM‐encoded EC private key (PKCS#8 or EC PRIVATE KEY)
   * @param keyId         Your API key ID (goes into the kid header, sub claim)
   */
  public static String generateJWT(String method, String url, String privateKeyPEM, String keyId)
      throws Exception {

    KeyPair kp = loadECKeyPair(privateKeyPEM);
    ECPublicKey publicKey = (ECPublicKey) kp.getPublic();
    ECPrivateKey privateKey = (ECPrivateKey) kp.getPrivate();

    long now = Instant.now().getEpochSecond();

    // you can drop your custom 'nonce' header if you prefer,
    // or keep it if Coinbase wants it
    Map<String, Object> header = new HashMap<>();
    header.put("alg", "ES256");
    header.put("typ", "JWT");
    header.put("kid", keyId);
    header.put("nonce", String.valueOf(now));

    String uriClaim = method + " " + url;

    Date nbf = Date.from(Instant.ofEpochSecond(now));
    Date exp = Date.from(Instant.ofEpochSecond(now + 120));

    Algorithm alg = Algorithm.ECDSA256(publicKey, privateKey);

    return JWT.create().withHeader(header).withIssuer("cdp").withSubject(keyId).withNotBefore(nbf)
        .withExpiresAt(exp).withClaim("uri", uriClaim).sign(alg);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    final String pathWithQueryString = restInvocation.getInvocationUrl()
        .replace(restInvocation.getBaseUrl(), "");
    final String timestamp = restInvocation.getParamValue(HeaderParam.class, CB_ACCESS_TIMESTAMP)
        .toString();
    final String message = timestamp + restInvocation.getHttpMethod() + pathWithQueryString;

    String requestMethod = restInvocation.getHttpMethod();
    String url = restInvocation.getInvocationUrl();

//    String jwt = generateJWT(restInvocation.getHttpMethod(), restInvocation.getInvocationUrl(), privateKeyPEM, keyId);

    return DigestUtils.bytesToHex(getMac().doFinal(message.getBytes()));
  }
}
