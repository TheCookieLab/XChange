package org.knowm.xchange.coinbase.v2;

import static org.knowm.xchange.coinbase.v2.CoinbaseAuthenticated.CB_ACCESS_TIMESTAMP;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.ws.rs.HeaderParam;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.knowm.xchange.service.BaseParamsDigest;
import org.knowm.xchange.utils.DigestUtils;
import si.mazi.rescu.RestInvocation;

public class CoinbaseV2Digest extends BaseParamsDigest {

  static {
    // register BC once for PEM parsing + EC math
    Security.addProvider(new BouncyCastleProvider());
  }

  private CoinbaseV2Digest(String secretKey) {
    super(secretKey, HMAC_SHA_256);
  }

  public static CoinbaseV2Digest createInstance(String secretKey) {
    return secretKey == null ? null : new CoinbaseV2Digest(secretKey);
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

    return DigestUtils.bytesToHex(getMac().doFinal(message.getBytes()));
  }

  /**
   * @param requestMethod e.g. "GET"
   * @param url           e.g. "api.coinbase.com/api/v3/brokerage/accounts"
   * @param privateKeyPEM your PEM string, either "BEGIN EC PRIVATE KEY" or "BEGIN PRIVATE KEY"
   * @param name          your key‐ID / kid & sub claim
   */
  public String generateJWT(String requestMethod, String url, String privateKeyPEM, String name)
      throws Exception {
    // 1. Parse PEM and extract EC keypair (or derive public if only private)
    PEMParser pemParser = new PEMParser(new StringReader(privateKeyPEM));
    Object parsed = pemParser.readObject();
    pemParser.close();
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

    ECPrivateKey privateKey;
    ECPublicKey publicKey;

    if (parsed instanceof PEMKeyPair) {
      KeyPair kp = converter.getKeyPair((PEMKeyPair) parsed);
      privateKey = (ECPrivateKey) kp.getPrivate();
      publicKey = (ECPublicKey) kp.getPublic();
    } else if (parsed instanceof PrivateKeyInfo) {
      // only private info → convert + derive public
      privateKey = (ECPrivateKey) converter.getPrivateKey((PrivateKeyInfo) parsed);

      // curve parameters for P-256 / secp256r1
      ECNamedCurveParameterSpec ecP = ECNamedCurveTable.getParameterSpec("P-256");
      ECParameterSpec bcSpec = new ECNamedCurveParameterSpec("P-256", ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());

      // do Q = d * G
      BigInteger d = privateKey.getS();
      org.bouncycastle.math.ec.ECPoint Q = ecP.getG().multiply(d).normalize();
      java.security.spec.ECPoint w = new java.security.spec.ECPoint(
          Q.getAffineXCoord().toBigInteger(), Q.getAffineYCoord().toBigInteger());

      KeyFactory kf = KeyFactory.getInstance("EC", "BC");
      ECPublicKeySpec pubSpec = new ECPublicKeySpec(w, bcSpec);
      publicKey = (ECPublicKey) kf.generatePublic(pubSpec);
    } else {
      throw new IllegalArgumentException("Unrecognized PEM object: " + parsed.getClass());
    }

    // 2. Build header & claims
    long nowEpoch = Instant.now().getEpochSecond();
    Map<String, Object> header = new HashMap<>();
    header.put("alg", "ES256");
    header.put("typ", "JWT");
    header.put("kid", name);
    header.put("nonce", String.valueOf(nowEpoch));

    Instant notBefore = Instant.ofEpochSecond(nowEpoch);
    Instant expiresAt = notBefore.plusSeconds(120);
    String uri = requestMethod + " " + url;

    // 3. Sign with java-jwt
    Algorithm alg = Algorithm.ECDSA256(publicKey, privateKey);
    return JWT.create().withHeader(header).withIssuer("cdp").withSubject(name)
        .withNotBefore(Date.from(notBefore)).withExpiresAt(Date.from(expiresAt))
        .withClaim("uri", uri).sign(alg);
  }
}
