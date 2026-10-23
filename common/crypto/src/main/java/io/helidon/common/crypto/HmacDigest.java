/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.Api;
import io.helidon.common.Base64Value;

/**
 * The HmacDigest is used for simplification of the HMAC signature creation and verification.
 * Algorithm, provider and secret are validated when the instance is built.
 */
@Api.Stable
public class HmacDigest implements Digest {

    /**
     * HMAC using MD5 as a hash function.
     * <br>
     * Value is: {@value}.
     * @deprecated HMAC-MD5 should not be used for signature purposes, for more information see
     * <a href="https://tools.ietf.org/html/rfc6151#section-2.3">RFC6151 - Section 2.3</a>.
     */
    @Deprecated
    public static final String ALGORITHM_MD5 = "HmacMD5";

    /**
     * HMAC using SHA1 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_1 = "HmacSHA1";

    /**
     * HMAC using SHA224 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_224 = "HmacSHA224";

    /**
     * HMAC using SHA256 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_256 = "HmacSHA256";

    /**
     * HMAC using SHA384 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_384 = "HmacSHA384";

    /**
     * HMAC using SHA512 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_512 = "HmacSHA512";

    /**
     * HMAC using SHA512/224 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_512_224 = "HmacSHA512/224";

    /**
     * HMAC using SHA512/256 as a hash function.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_512_256 = "HmacSHA512/256";

    private final String algorithm;
    private final String provider;
    private final byte[] hmacSecret;

    private HmacDigest(Builder builder) {
        this.algorithm = builder.algorithm;
        this.provider = builder.provider;
        this.hmacSecret = builder.hmacSecret.clone();
        mac();
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
        Objects.requireNonNull(value, "Value to digest cannot be null");
        return Base64Value.create(mac().doFinal(value.toBytes()));
    }

    private Mac mac() {
        try {
            Mac mac;
            if (provider != null) {
                mac = Mac.getInstance(algorithm, provider);
            } else {
                mac = Mac.getInstance(algorithm);
            }
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret, algorithm);
            mac.init(secretKey);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchProviderException | IllegalArgumentException e) {
            throw new CryptoException("Could not create hmac digest", e);
        }
    }

    /**
     * Builder of the {@link HmacDigest}.
     * Optional provider selection can be removed using {@link #clearProvider()}; setters do not accept {@code null}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, HmacDigest> {

        private String algorithm = ALGORITHM_SHA_256;
        private String provider;
        private byte[] hmacSecret;

        private Builder() {
        }

        /**
         * Set new HMAC algorithm.
         * <br>
         * Default value is {@link #ALGORITHM_SHA_256}
         *
         * @param algorithm algorithm to set
         * @return updated builder instance
         * @throws NullPointerException if algorithm is {@code null}
         */
        public Builder algorithm(String algorithm) {
            Objects.requireNonNull(algorithm, "Hmac type cannot be null");
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Clear the explicit provider selection and use normal JCA provider selection.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearProvider() {
            this.provider = null;
            return this;
        }

        /**
         * Set provider of the algorithm.
         * <p>
         * To use normal JCA provider selection, call {@link #clearProvider()} instead of passing {@code null}.
         *
         * @param provider provider to set
         * @return updated builder instance
         * @throws NullPointerException if provider is {@code null}
         */
        public Builder provider(String provider) {
            Objects.requireNonNull(provider);
            this.provider = provider;
            return this;
        }

        /**
         * Secret key to be used in HMAC algorithm.
         *
         * @param hmacSecret secret key, copied by this builder
         * @return updated builder instance
         * @throws NullPointerException if secret key is {@code null}
         */
        public Builder hmacSecret(byte[] hmacSecret) {
            Objects.requireNonNull(hmacSecret, "Hmac base secret key");
            this.hmacSecret = hmacSecret.clone();
            return this;
        }

        /**
         * Algorithm used to create the HMAC digest.
         *
         * @return algorithm name
         * @since 28.0.0
         */
        public String algorithm() {
            return algorithm;
        }

        /**
         * Explicit provider selection, if configured.
         *
         * @return provider name, or empty for normal JCA provider selection
         * @since 28.0.0
         */
        public Optional<String> provider() {
            return Optional.ofNullable(provider);
        }

        /**
         * HMAC secret key, if configured.
         *
         * @return a copy of the secret key, or empty if not configured
         * @since 28.0.0
         */
        public Optional<byte[]> hmacSecret() {
            return Optional.ofNullable(hmacSecret).map(value -> value.clone());
        }

        /**
         * Build a digest after validating its algorithm, provider and secret key with JCA initialization.
         *
         * @return configured digest
         * @throws CryptoException if the secret key is missing or the configuration cannot initialize an HMAC
         */
        @Override
        public HmacDigest build() {
            if (hmacSecret == null) {
                throw new CryptoException("Hmac secret key has to be set");
            }
            return new HmacDigest(this);
        }
    }

}
