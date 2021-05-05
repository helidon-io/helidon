/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonObject;

import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;

import static io.helidon.security.jwt.JwtUtil.asBigInteger;
import static io.helidon.security.jwt.JwtUtil.getKeyFactory;

/**
 * RSA JSON web key.
 */
@SuppressWarnings("WeakerAccess") // constants should be public
public class JwkRSA extends JwkPki {
    /**
     * The main Java security algorithm used.
     */
    public static final String SECURITY_ALGORITHM = "RSA";

    /**
     * RSASSA-PKCS1-v1_5 using SHA-256.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_RS256 = "RS256";
    /**
     * RSASSA-PKCS1-v1_5 using SHA-384.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_RS384 = "RS384";
    /**
     * RSASSA-PKCS1-v1_5 using SHA-512.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_RS512 = "RS512";

    /**
     * JWK parameter for public key modulus.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.1.1.
     */
    public static final String PARAM_PUB_MODULUS = "n";

    /**
     * JWK parameter for public key exponent.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.1.2.
     */
    public static final String PARAM_PUB_EXP = "e";

    /**
     * JWK parameter for private key exponent.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.1.
     */
    public static final String PARAM_EXP = "d";

    /**
     * JWK parameter for private key First Prime Factor.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.2.
     */
    public static final String PARAM_FIRST_PRIME_FACTOR = "p";

    /**
     * JWK parameter for private key Second Prime Factor.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.3.
     */
    public static final String PARAM_SECOND_PRIME_FACTOR = "q";

    /**
     * JWK parameter for private key First Factor CRT Exponent.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.4.
     */
    public static final String PARAM_FIRST_FACTOR_CRT_EXP = "dp";

    /**
     * JWK parameter for private key Second Factor CRT Exponent.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.5.
     */
    public static final String PARAM_SECOND_FACTOR_CRT_EXP = "dq";

    /**
     * JWK parameter for private key First CRT Coefficient.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.6.
     */
    public static final String PARAM_FIRST_CRT_COEFF = "qi";

    /**
     * JWK parameter for private key Other Primes Info.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.3.2.7.
     */
    public static final String PARAM_OTHER_PRIMES = "oth";

    // maps JWK algorithms to Java algorithms
    private static final Map<String, String> ALG_MAP = new HashMap<>();

    static {
        // Values obtained from RFC (mapping of algorithms)
        ALG_MAP.put(ALG_RS256, "SHA256withRSA");
        ALG_MAP.put(ALG_RS384, "SHA384withRSA");
        ALG_MAP.put(ALG_RS512, "SHA512withRSA");

        ALG_MAP.put(ALG_NONE, ALG_NONE);
    }

    private JwkRSA(Builder builder) {
        super(builder, builder.privateKey, builder.publicKey, ALG_RS256);
    }

    /**
     * Create a builder instance.
     *
     * @return builder ready to create a new {@link JwkRSA} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance from Json object.
     *
     * @param json with definition of this RSA web key
     * @return new instance of this class constructed from json
     * @see Jwk#create(JsonObject) for generic method that can load any supported JWK type.
     */
    public static JwkRSA create(JsonObject json) {
        return builder().fromJson(json).build();
    }

    @Override
    String signatureAlgorithm() {
        String jwkAlg = algorithm();
        String javaAlg = ALG_MAP.get(jwkAlg);

        if (null == javaAlg) {
            throw new JwtException("Unsupported algorithm for RSA: " + jwkAlg);
        }

        return javaAlg;
    }

    /**
     * Builder for {@link JwkRSA}.
     */
    public static final class Builder extends JwkPki.Builder<Builder> implements io.helidon.common.Builder<JwkRSA> {
        private PrivateKey privateKey;
        private PublicKey publicKey;

        private Builder() {
        }

        private static PublicKey toPublicKey(KeyFactory kf, BigInteger modulus, BigInteger publicExponent) {
            try {
                return kf.generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
            } catch (InvalidKeySpecException e) {
                throw new JwtException("Failed to generate RSA public key", e);
            }
        }

        private static PrivateKey toPrivateKey(KeyFactory kf,
                                               BigInteger modulus,
                                               BigInteger publicExponent,
                                               BigInteger privateExponent,
                                               JsonObject json) {
            // "p" is optional, but when present, all others should be there
            return JwtUtil.getBigInteger(json, PARAM_FIRST_PRIME_FACTOR, "RSA first prime factor")
                    .map(firstPrimeFactor -> {
                        // Follow up issue is opened at security#26 to resolve this
                        JwtUtil.getBigInteger(json, PARAM_OTHER_PRIMES, "RSA other primes info")
                                .ifPresent(it -> {
                                    throw new JwtException(
                                            "Other primes info for RSA private key is not (yet) supported");
                                });

                        BigInteger secondPrimeFactor = asBigInteger(json, PARAM_SECOND_PRIME_FACTOR, "RSA second prime factor");
                        BigInteger firstFactorCrtExp = asBigInteger(json,
                                                                    PARAM_FIRST_FACTOR_CRT_EXP,
                                                                    "RSA first factor CRT exponent");
                        BigInteger secondFactorCrtExp = asBigInteger(json,
                                                                     PARAM_SECOND_FACTOR_CRT_EXP,
                                                                     "RSA second factor CRT exponent");
                        BigInteger firstCrtCoeff = asBigInteger(json, PARAM_FIRST_CRT_COEFF, "RSA first CRT coefficient");
                        try {
                            return kf.generatePrivate(new RSAPrivateCrtKeySpec(modulus,
                                                                               publicExponent,
                                                                               privateExponent,
                                                                               firstPrimeFactor,
                                                                               secondPrimeFactor,
                                                                               firstFactorCrtExp,
                                                                               secondFactorCrtExp,
                                                                               firstCrtCoeff));
                        } catch (Exception e) {
                            throw new JwtException("Failed to generate private key", e);
                        }
                    })
                    .orElseGet(() -> {
                        try {
                            return kf.generatePrivate(new RSAPrivateKeySpec(modulus, privateExponent));
                        } catch (InvalidKeySpecException e) {
                            throw new JwtException("Failed to generate private key based on modulus and private exponent");
                        }
                    });
        }

        /**
         * Set the private key to be used for performing security operations requiring private key,
         * such as signing data, encrypting/decrypting data etc.
         *
         * @param privateKey RSA private key instance
         * @return updated builder instance
         */
        public Builder privateKey(RSAPrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Set the public key to be used for performing security operations requiring public key,
         * such as signature verification, encrypting/decrypting data etc.
         *
         * @param publicKey RSA public key instance
         * @return updated builder instance
         */
        public Builder publicKey(RSAPublicKey publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        /**
         * Update this builder from JWK in json format.
         *
         * @param json JsonObject with the JWK
         * @return updated builder instance, just call {@link #build()} to build the {@link JwkRSA} instance
         * @see JwkRSA#create(JsonObject) as a shortcut if no additional configuration is to be done
         */
        public Builder fromJson(JsonObject json) {
            super.fromJson(json);

            // now RSA specific fields
            BigInteger modulus = asBigInteger(json, PARAM_PUB_MODULUS, "RSA modulus");
            BigInteger publicExponent = asBigInteger(json, PARAM_PUB_EXP, "RSA exponent");

            KeyFactory kf = getKeyFactory(SECURITY_ALGORITHM);

            this.privateKey = JwtUtil.getBigInteger(json, PARAM_EXP, "RSA private exponent")
                    .map(d -> toPrivateKey(kf, modulus, publicExponent, d, json))
                    .orElse(null);

            this.publicKey = toPublicKey(kf, modulus, publicExponent);

            return this;
        }

        /**
         * Build a new {@link JwkRSA} instance from this builder.
         *
         * @return instance of {@link JwkRSA} configured from this builder
         */
        @Override
        public JwkRSA build() {
            return new JwkRSA(this);
        }

    }
}
