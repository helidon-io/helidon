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

import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.json.JsonObject;

import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;

/**
 * Symmetric cipher JSON web key.
 */
@SuppressWarnings("WeakerAccess") // constants should be public
public class JwkOctet extends Jwk {
    /**
     * HMAC using SHA-256.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_HS256 = "HS256";
    /**
     * HMAC using SHA-384.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_HS384 = "HS384";
    /**
     * HMAC using SHA-512.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 7.1.2.
     */
    public static final String ALG_HS512 = "HS512";
    /**
     * Key value.
     *
     * The "k" (key value) parameter contains the value of the symmetric (or
     * other single-valued) key.  It is represented as the base64url
     * encoding of the octet sequence containing the key value.
     *
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.4.1.
     */
    public static final String PARAM_OCTET_KEY = "k";

    // maps JWK algorithms to Java algorithms
    private static final Map<String, String> ALG_MAP = new HashMap<>();

    static {
        // Values obtained from RFC (mapping of algorithms)
        ALG_MAP.put(ALG_HS256, "HmacSHA256");
        ALG_MAP.put(ALG_HS384, "HmacSHA384");
        ALG_MAP.put(ALG_HS512, "HmacSHA512");
        ALG_MAP.put(ALG_NONE, ALG_NONE);
    }

    private final byte[] keyBytes;

    private JwkOctet(Builder builder) {
        super(builder, ALG_HS256);

        this.keyBytes = builder.key;
    }

    /**
     * Create a builder instance.
     *
     * @return builder ready to create a new {@link JwkOctet} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance from Json object.
     * Note that the {@code "k"} must be base64 encoded.
     *
     * @param json with definition of this octet web key
     * @return new instance of this class constructed from json
     * @see Jwk#create(JsonObject) for generic method that can load any supported JWK type.
     */
    public static JwkOctet create(JsonObject json) {
        return builder().fromJson(json).build();
    }

    /**
     * Get the bytes of the secret key.
     *
     * @return byte array of the secret key
     */
    public byte[] getKeyBytes() {
        return Arrays.copyOf(keyBytes, keyBytes.length);
    }

    @Override
    public boolean doVerify(byte[] signedBytes, byte[] signature) {
        String alg = getSignatureAlgorithm();

        if (ALG_NONE.equals(alg)) {
            return verifyNoneAlg(signature);
        }

        byte[] ourSignature = sign(signedBytes);
        return Arrays.equals(signature, ourSignature);
    }

    @Override
    public byte[] doSign(byte[] bytesToSign) {
        String alg = getSignatureAlgorithm();

        if (ALG_NONE.equals(alg)) {
            return EMPTY_BYTES;
        }

        Mac mac = JwtUtil.getMac(alg);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, alg);
        try {
            mac.init(secretKey);
        } catch (InvalidKeyException e) {
            throw new JwtException("Failed to init Mac for algorithm: " + alg, e);
        }

        return mac.doFinal(bytesToSign);
    }

    private String getSignatureAlgorithm() {
        String jwkAlg = algorithm();
        String javaAlg = ALG_MAP.get(algorithm());

        if (null == javaAlg) {
            throw new JwtException("Unsupported algorithm for MAC: " + jwkAlg);
        }

        return javaAlg;
    }

    /**
     * Builder for {@link JwkOctet}.
     */
    public static final class Builder extends Jwk.Builder<Builder> implements io.helidon.common.Builder<JwkOctet> {
        private byte[] key;

        private Builder() {
        }

        /**
         * Update this builder from JWK in json format.
         * Note that the {@code "k"} must be base64 encoded.
         *
         * @param json JsonObject with the JWK
         * @return updated builder instance, just call {@link #build()} to build the {@link JwkOctet} instance
         * @see JwkOctet#create(JsonObject) as a shortcut if no additional configuration is to be done
         */
        public Builder fromJson(JsonObject json) {
            super.fromJson(json);

            this.key = JwtUtil.asByteArray(json, PARAM_OCTET_KEY, "Octet key");

            return this;
        }

        /**
         * Build a new {@link JwkOctet} instance from this builder.
         *
         * @return instance of {@link JwkOctet} configured from this builder
         */
        @Override
        public JwkOctet build() {
            return new JwkOctet(this);
        }

    }
}
