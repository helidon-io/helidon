/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.integrations.common.rest.Base64Value;

/**
 * The HmacDigest is used for simplification of the HMAC signature creation and verification.
 */
public class HmacDigest implements Digest {

    /**
     * HMAC using MD5 as a hash function.
     */
    public static final String ALGORITHM_MD5 = "HmacMD5";

    /**
     * HMAC using SHA1 as a hash function.
     */
    public static final String ALGORITHM_SHA_1 = "HmacSHA1";

    /**
     * HMAC using SHA224 as a hash function.
     */
    public static final String ALGORITHM_SHA_224 = "HmacSHA224";

    /**
     * HMAC using SHA256 as a hash function.
     */
    public static final String ALGORITHM_SHA_256 = "HmacSHA256";

    /**
     * HMAC using SHA384 as a hash function.
     */
    public static final String ALGORITHM_SHA_384 = "HmacSHA384";

    /**
     * HMAC using SHA512 as a hash function.
     */
    public static final String ALGORITHM_SHA_512 = "HmacSHA512";

    /**
     * HMAC using SHA512/224 as a hash function.
     */
    public static final String ALGORITHM_SHA_512_224 = "HmacSHA512/224";

    /**
     * HMAC using SHA512/256 as a hash function.
     */
    public static final String ALGORITHM_SHA_512_256 = "HmacSHA512/256";

    private final String algorithm;
    private final String provider;
    private final byte[] hmacSecret;

    private HmacDigest(Builder builder) {
        this.algorithm = builder.algorithm;
        this.provider = builder.provider;
        this.hmacSecret = builder.hmacSecret;
    }

    /**
     * Create new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create new instance based on provided HMAC secret.
     *
     * Algorithm of the returned instance is {@link #ALGORITHM_SHA_256}.
     *
     * @param hmacSecret hmac secret
     * @return new instance
     */
    public static HmacDigest create(byte[] hmacSecret) {
        return builder().hmacSecret(hmacSecret).build();
    }

    @Override
    public Base64Value digest(Base64Value value) {
        try {
            Mac mac;
            if (provider != null) {
                mac = Mac.getInstance(algorithm, provider);
            } else {
                mac = Mac.getInstance(algorithm);
            }
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret, algorithm);
            mac.init(secretKey);
            return Base64Value.create(mac.doFinal(value.toBytes()));
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchProviderException e) {
            throw new CryptoException("Could not create hmac digest", e);
        }
    }

    /**
     * Builder of the {@link HmacDigest}.
     */
    public static final class Builder implements io.helidon.common.Builder<HmacDigest> {

        private String algorithm = ALGORITHM_SHA_256;
        private String provider = null;
        private byte[] hmacSecret;

        private Builder() {
        }

        /**
         * Set new HMAC algorithm.
         * <p>
         * Default value is {@link #ALGORITHM_SHA_256}
         *
         * @param algorithm algorithm to set
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "Hmac type cannot be null");
            return this;
        }

        /**
         * Set provider of the algorithm.
         *
         * @param provider provider to set
         * @return updated builder instance
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Secret key to be used in HMAC algorithm.
         *
         * @param hmacSecret secret key
         * @return updated builder instance
         */
        public Builder hmacSecret(byte[] hmacSecret) {
            Objects.requireNonNull(hmacSecret, "Hmac base secret key");
            this.hmacSecret = hmacSecret.clone();
            return this;
        }

        @Override
        public HmacDigest build() {
            if (hmacSecret == null) {
                throw new CryptoException("Hmac secret key has to be set");
            }
            return new HmacDigest(this);
        }
    }

}
