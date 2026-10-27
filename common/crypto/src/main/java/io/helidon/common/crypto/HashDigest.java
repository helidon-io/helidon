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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.Base64Value;

/**
 * The HashDigest is used for ordinary data digest creation and verification.
 * <br>
 * Should not be used for authentication purposes.
 * Algorithm and provider availability are validated when the instance is built.
 */
@Api.Stable
public class HashDigest implements Digest {

    /**
     * Digest MD2 algorithm.
     * <br>
     * Value is: {@value}.
     * @deprecated It is strongly recommended not to use this algorithm as stated here
     * <a href="https://tools.ietf.org/html/rfc6149#section-6">RFC6149 - Section 6</a>.
     */
    @Deprecated
    public static final String ALGORITHM_MD2 = "MD2";

    /**
     * Digest MD5 algorithm.
     * <br>
     * Value is: {@value}.
     * @deprecated It is strongly recommended not to use this algorithm as stated here
     * <a href="https://tools.ietf.org/html/rfc6151#section-2.1">RFC6151 - Section 2.1</a>.
     */
    @Deprecated
    public static final String ALGORITHM_MD5 = "MD5";

    /**
     * Digest SHA-1 algorithm.
     * <br>
     * Value is: {@value}.
     * @deprecated SHA-1 is unsafe to use alone due to its vulnerability to collision attacks
     */
    @Deprecated
    public static final String ALGORITHM_SHA_1 = "SHA-1";

    /**
     * Digest SHA-224 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_224 = "SHA-224";

    /**
     * Digest SHA-256 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_256 = "SHA-256";

    /**
     * Digest SHA-384 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_384 = "SHA-384";

    /**
     * Digest SHA-512/224 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_512_224 = "SHA-512/224";

    /**
     * Digest SHA-512/256 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA_512_256 = "SHA-512/256";

    /**
     * Digest SHA3-224 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA3_224 = "SHA3-224";

    /**
     * Digest SHA3-256 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA3_256 = "SHA3-256";

    /**
     * Digest SHA3-384 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA3_384 = "SHA3-384";

    /**
     * Digest SHA3-512 algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA3_512 = "SHA3-512";

    private final String algorithm;
    private final String provider;

    private HashDigest(Builder builder) {
        this.algorithm = builder.algorithm;
        this.provider = builder.provider;
        messageDigest();
    }

    /**
     * Create a new instance of this class based on selected algorithm.
     *
     * @param algorithm algorithm to be used
     * @return new instance
     */
    public static HashDigest create(String algorithm) {
        return builder().algorithm(algorithm).build();
    }

    /**
     * Create new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Base64Value digest(Base64Value value) {
        Objects.requireNonNull(value, "Value to digest cannot be null");
        return Base64Value.create(messageDigest().digest(value.toBytes()));
    }

    private MessageDigest messageDigest() {
        try {
            if (provider == null) {
                return MessageDigest.getInstance(algorithm);
            } else {
                return MessageDigest.getInstance(algorithm, provider);
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Could not create digest", e);
        }
    }

    /**
     * Builder of the {@link HashDigest}.
     * Optional provider selection can be removed using {@link #clearProvider()}; setters do not accept {@code null}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, HashDigest> {

        private String algorithm = ALGORITHM_SHA_256;
        private String provider;

        private Builder() {
        }

        /**
         * Set digest algorithm.
         *
         * @param algorithm algorithm to be used
         * @return updated builder instance
         * @throws NullPointerException if algorithm is {@code null}
         */
        public Builder algorithm(String algorithm) {
            Objects.requireNonNull(algorithm, "Algorithm cannot be null");
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
         * @param provider provider to be used
         * @return updated builder instance
         * @throws NullPointerException if provider is {@code null}
         */
        public Builder provider(String provider) {
            Objects.requireNonNull(provider);
            this.provider = provider;
            return this;
        }

        /**
         * Algorithm used to create the digest.
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
         * Build a digest after validating algorithm and provider availability using JCA lookup.
         *
         * @return configured digest
         * @throws IllegalStateException if the algorithm or provider is unavailable
         * @throws IllegalArgumentException if the explicit provider name is empty
         */
        @Override
        public HashDigest build() {
            return new HashDigest(this);
        }
    }

}
