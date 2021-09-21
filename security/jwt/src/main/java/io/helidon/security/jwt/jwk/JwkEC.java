/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.security.jwt.jwk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonObject;

import io.helidon.security.jwt.JwtException;

import static io.helidon.security.jwt.JwtUtil.asBigInteger;
import static io.helidon.security.jwt.JwtUtil.asString;
import static io.helidon.security.jwt.JwtUtil.getBigInteger;
import static io.helidon.security.jwt.JwtUtil.getKeyFactory;

/**
 * Elliptic curve JSON web key.
 */
@SuppressWarnings("WeakerAccess") // constants should be public
public class JwkEC extends JwkPki {
    /**
     * The main Java security algorithm used.
     */
    public static final String SECURITY_ALGORITHM = "EC";
    /**
     * ECDSA using P-256 and SHA-256.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_ES256 = "ES256";
    /**
     * ECDSA using P-384 and SHA-384.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_ES384 = "ES384";
    /**
     * ECDSA using {@value #CURVE_P521} and SHA-512.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_ES512 = "ES512";
    /**
     * P-256 Curve.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.6.2.
     */
    public static final String CURVE_P256 = "P-256";
    /**
     * P-384 Curve.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.6.2.
     */
    public static final String CURVE_P384 = "P-384";
    /**
     * P-521 Curve.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.6.2.
     */
    public static final String CURVE_P521 = "P-521";

    /**
     * JWK parameter for EC curve.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.2.1.1.
     *
     * @see #CURVE_P256
     * @see #CURVE_P384
     * @see #CURVE_P521
     */
    public static final String PARAM_CURVE = "crv";
    /**
     * JWK parameter for X coordinate.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.2.1.2.
     */
    public static final String PARAM_X_COORDINATE = "x";
    /**
     * JWK parameter for X coordinate.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.2.1.3.
     */
    public static final String PARAM_Y_COODRINATE = "y";
    /**
     * JWK parameter for private key.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.2.2.1.
     */
    public static final String PARAM_PRIVATE_KEY = "d";

    // maps named curves to EC parameters specifications
    private static final Map<String, ECParameterSpec> CURVE_MAP = new HashMap<>();
    // maps JWK algorithms to Java algorithms
    private static final Map<String, String> ALG_MAP = new HashMap<>();

    static {
        // Values obtained from RFC (mapping of algorithms)
        ALG_MAP.put(ALG_ES256, "SHA256withECDSA");
        ALG_MAP.put(ALG_ES384, "SHA384withECDSA");
        ALG_MAP.put(ALG_ES512, "SHA512withECDSA");
        ALG_MAP.put(ALG_NONE, ALG_NONE);

        // Values obtained from org.bouncycastle.jce.ECNamedCurveTable
        CURVE_MAP.put(CURVE_P256, new ECParameterSpec(
                new EllipticCurve(
                        new ECFieldFp(new BigInteger(
                                "115792089210356248762697446949407573530086143415290314195533631308867097853951")),
                        new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853948"),
                        new BigInteger("41058363725152142129326129780047268409114441015993725554835256314039467401291")),
                new ECPoint(
                        new BigInteger("48439561293906451759052585252797914202762949526041747995844080717082404635286"),
                        new BigInteger("36134250956749795798585127919587881956611106672985015071877198253568414405109")),
                new BigInteger("115792089210356248762697446949407573529996955224135760342422259061068512044369"),
                1));

        CURVE_MAP.put(CURVE_P384, new ECParameterSpec(
                new EllipticCurve(
                        new ECFieldFp(new BigInteger(
                                "39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088"
                                        + "258938001861606973112319")),
                        new BigInteger(
                                "39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088"
                                        + "258938001861606973112316"),
                        new BigInteger(
                                "27580193559959705877849011840389048093056905856361568521428707301988689241309860865136260764"
                                        + "883745107765439761230575")),
                new ECPoint(
                        new BigInteger(
                                "26247035095799689268623156744566981891852923491109213387815615900925518854738050089022388053"
                                        + "975719786650872476732087"),
                        new BigInteger(
                                "83257109614890299855467512895201081792878530488613155947092059024805031998844192244386437603"
                                        + "92947333078086511627871")),
                new BigInteger(
                        "3940200619639447921227904010014361380507973927046544666794690527962765939911326356939895630815229491"
                                + "3554433653942643"),
                1));

        CURVE_MAP.put(CURVE_P521, new ECParameterSpec(
                new EllipticCurve(
                        new ECFieldFp(new BigInteger(
                                "68647976601306097149819007990813932172694353001433054093944634591855431833976560521225596406"
                                        + "61454554977296311391480858037121987999716643812574028291115057151")),
                        new BigInteger(
                                "68647976601306097149819007990813932172694353001433054093944634591855431833976560521225596406"
                                        + "61454554977296311391480858037121987999716643812574028291115057148"),
                        new BigInteger(
                                "10938490380737342745111123907668055699362075989516837489945863944959531161507350160137087375"
                                        + "73759623248592132296706313309438452531591012912142327488478985984")),
                new ECPoint(
                        new BigInteger(
                                "26617408020502170632287687167233609607298591687569731477066713684188029449964278084915450806"
                                        + "27771902352094241225065558662157113545570916814161637315895999846"),
                        new BigInteger(
                                "37571800257700204635455072244911836035944551347697624866945677796155444774405563166912344050"
                                        + "12945539562144444537289428522585666729196580810124344277578376784")),
                new BigInteger(
                        "6864797660130609714981900799081393217269435300143305409394463459185543183397655394245057746333217197"
                                + "532963996371363321113864768612440380340372808892707005449"),
                1));
    }

    private JwkEC(Builder builder) {
        super(builder, builder.privateKey, builder.publicKey, builder.defaultAlg);
    }

    /**
     * Create a builder instance.
     *
     * @return builder ready to create a new {@link JwkEC} instance.
     */
    public static Builder builder() {
        return new Builder().keyType(KEY_TYPE_EC);
    }

    /**
     * Create an instance from Json object.
     *
     * @param json with definition of this EC web key
     * @return new instance of this class constructed from json
     * @see Jwk#create(JsonObject) for generic method that can load any supported JWK type.
     */
    public static JwkEC create(JsonObject json) {
        return builder().fromJson(json).build();
    }

    @Override
    String signatureAlgorithm() {
        String jwkAlg = algorithm();
        String javaAlg = ALG_MAP.get(jwkAlg);

        if (null == javaAlg) {
            throw new JwtException("Unsupported algorithm for Elliptic curve: " + jwkAlg);
        }

        return javaAlg;
    }

    @Override
    public boolean doVerify(byte[] signedBytes, byte[] signatureToVerify) {
        try {
            return super.doVerify(signedBytes, signatureToVerify);
        } catch (JwtException e) {
            if (e.getCause().getMessage().contains("encoding")) {
                return changeSignatureEncodingToDER(signedBytes, signatureToVerify);
            } else {
                throw e;
            }
        }
    }

    private boolean changeSignatureEncodingToDER(byte[] signedBytes, byte[] signatureToVerify) {
        String alg = signatureAlgorithm();

        if (ALG_NONE.equals(alg)) {
            return verifyNoneAlg(signatureToVerify);
        }

        byte[] rBytes = Arrays.copyOfRange(signatureToVerify, 0, 32);
        byte[] sBytes = Arrays.copyOfRange(signatureToVerify, 32, 64);

        BigInteger r = new BigInteger(1, rBytes);
        BigInteger s = new BigInteger(1, sBytes);

        byte[] rb = r.toByteArray();
        byte[] sb = s.toByteArray();

        byte[] signatureDerBytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int length = 1 + calculateBodyLength(rb.length) + rb.length + 1 + calculateBodyLength(sb.length) + sb.length;
            outputStream.write(16 | 32);
            writeLength(outputStream, length);
            outputStream.write(2);
            writeLength(outputStream, rb.length);
            outputStream.write(rb);
            outputStream.write(2);
            writeLength(outputStream, sb.length);
            outputStream.write(sb);
            signatureDerBytes = outputStream.toByteArray();
        } catch (IOException e) {
            throw new JwtException("Signature encoding conversion to DER has failed.", e);
        }
        return super.doVerify(signedBytes, signatureDerBytes);
    }

    private static int calculateBodyLength(int length) {
        int count = 1;

        if (length > 127) {
            int size = 1;
            int val = length;

            while ((val >>>= 8) != 0) {
                size++;
            }

            for (int i = (size - 1) * 8; i >= 0; i -= 8) {
                count++;
            }
        }

        return count;
    }

    private void writeLength(OutputStream os, int length) throws IOException {
        if (length > 127) {
            int size = 1;
            int val = length;

            while ((val >>>= 8) != 0) {
                size++;
            }

            os.write((byte) (size | 0x80));

            for (int i = (size - 1) * 8; i >= 0; i -= 8) {
                os.write((byte) (length >> i));
            }
        } else {
            os.write((byte) length);
        }
    }

    /**
     * Builder for {@link JwkEC}.
     */
    public static final class Builder extends JwkPki.Builder<Builder> implements io.helidon.common.Builder<JwkEC> {
        private PrivateKey privateKey;
        private PublicKey publicKey;
        private String defaultAlg = ALG_ES256;

        private Builder() {
        }

        private static PublicKey toPublicKey(KeyFactory kf, ECPoint point, ECParameterSpec keySpec) {
            try {
                return kf.generatePublic(new ECPublicKeySpec(point, keySpec));
            } catch (InvalidKeySpecException e) {
                throw new JwtException("Failed to generate EC public key", e);
            }
        }

        private static PrivateKey toPrivateKey(KeyFactory kf, BigInteger privKeyValue, ECParameterSpec keySpec) {
            ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privKeyValue, keySpec);
            try {
                return kf.generatePrivate(privateKeySpec);
            } catch (InvalidKeySpecException e) {
                throw new JwtException("Failed to generate EC private key", e);
            }
        }

        /**
         * Set the private key to be used for performing security operations requiring private key,
         * such as signing data, encrypting/decrypting data etc.
         *
         * @param privateKey EC private key instance
         * @return updated builder instance
         */
        public Builder privateKey(ECPrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Set the public key to be used for performing security operations requiring public key,
         * such as signature verification, encrypting/decrypting data etc.
         *
         * @param publicKey EC public key instance
         * @return updated builder instance
         */
        public Builder publicKey(ECPublicKey publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        /**
         * Update this builder from JWK in json format.
         *
         * @param json JsonObject with the JWK
         * @return updated builder instance, just call {@link #build()} to build the {@link JwkEC} instance
         * @see JwkEC#create(JsonObject) as a shortcut if no additional configuration is to be done
         */
        public Builder fromJson(JsonObject json) {
            super.fromJson(json);

            // now EC specific fields
            //public key definition
            String curve = asString(json, PARAM_CURVE, "EC curve");

            //validate curve is supported
            ECParameterSpec keySpec = CURVE_MAP.get(curve);
            if (null == keySpec) {
                throw new JwtException("Curve \"" + curve + "\" not supported for EC key type. Only one of: " + CURVE_MAP.keySet()
                                               + " is supported");
            }

            //for the supported curves, x and y are both mandatory parameters
            BigInteger x = asBigInteger(json, PARAM_X_COORDINATE, "EC X Coordinate");
            BigInteger y = asBigInteger(json, PARAM_Y_COODRINATE, "EC Y Coordinate");

            KeyFactory kf = getKeyFactory(SECURITY_ALGORITHM);

            this.privateKey = getBigInteger(json, PARAM_PRIVATE_KEY, "EC Private Key")
                    .map(privKeyValue -> toPrivateKey(kf, privKeyValue, keySpec))
                    .orElse(null);

            ECPoint point = new ECPoint(x, y);
            this.publicKey = toPublicKey(kf, point, keySpec);

            switch (curve) {
            case CURVE_P256:
                this.defaultAlg = ALG_ES256;
                break;
            case CURVE_P384:
                this.defaultAlg = ALG_ES384;
                break;
            case CURVE_P521:
                this.defaultAlg = ALG_ES512;
                break;
            default:
                this.defaultAlg = ALG_ES256;
                break;
            }

            return this;
        }

        /**
         * Build a new {@link JwkEC} instance from this builder.
         *
         * @return instance of {@link JwkEC} configured from this builder
         */
        @Override
        public JwkEC build() {
            return new JwkEC(this);
        }
    }
}
