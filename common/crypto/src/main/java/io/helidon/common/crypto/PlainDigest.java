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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;

import io.helidon.integrations.common.rest.Base64Value;

/**
 * The PlainDigest is used for ordinary data digest creation and verification.
 * <p>
 * Should not be used for authentication purposes.
 */
public class PlainDigest implements Digest {

    /**
     * Digest MD2 algorithm.
     */
    public static final String ALGORITHM_MD2 = "MD2";

    /**
     * Digest MD5 algorithm.
     */
    public static final String ALGORITHM_MD5 = "MD5";

    /**
     * Digest SHA-1 algorithm.
     */
    public static final String ALGORITHM_SHA_1 = "SHA-1";

    /**
     * Digest SHA-224 algorithm.
     */
    public static final String ALGORITHM_SHA_224 = "SHA-224";

    /**
     * Digest SHA-256 algorithm.
     */
    public static final String ALGORITHM_SHA_256 = "SHA-256";

    /**
     * Digest SHA-384 algorithm.
     */
    public static final String ALGORITHM_SHA_384 = "SHA-384";

    /**
     * Digest SHA-512/224 algorithm.
     */
    public static final String ALGORITHM_SHA_512_224 = "SHA-512/224";

    /**
     * Digest SHA-512/256 algorithm.
     */
    public static final String ALGORITHM_SHA_512_256 = "SHA-512/256";

    /**
     * Digest SHA3-224 algorithm.
     */
    public static final String ALGORITHM_SHA3_224 = "SHA3-224";

    /**
     * Digest SHA3-256 algorithm.
     */
    public static final String ALGORITHM_SHA3_256 = "SHA3-256";

    /**
     * Digest SHA3-384 algorithm.
     */
    public static final String ALGORITHM_SHA3_384 = "SHA3-384";

    /**
     * Digest SHA3-512 algorithm.
     */
    public static final String ALGORITHM_SHA3_512 = "SHA3-512";

    private final String digestType;
    private final String provider;

    private PlainDigest(Builder builder) {
        this.digestType = builder.algorithm;
        this.provider = builder.provider;
    }

    /**
     * Create a new instance of this class based on selected algorithm.
     *
     * @param algorithm algorithm to be used
     * @return new instance
     */
    public static PlainDigest create(String algorithm) {
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
        try {
            MessageDigest digest;
            if (provider == null) {
                digest = MessageDigest.getInstance(digestType);
            } else {
                digest = MessageDigest.getInstance(digestType, provider);
            }
            return Base64Value.create(digest.digest(value.toBytes()));
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Could not create digest", e);
        }
    }

    /**
     * Builder of the {@link PlainDigest}.
     */
    public static final class Builder implements io.helidon.common.Builder<PlainDigest> {

        private String algorithm = ALGORITHM_SHA_256;
        private String provider = null;

        private Builder() {
        }

        /**
         * Set digest algorithm.
         *
         * @param algorithm algorithm to be used
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "Algorithm cannot be null");
            return this;
        }

        /**
         * Set provider of the algorithm.
         *
         * @param provider provider to be used
         * @return updated builder instance
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        @Override
        public PlainDigest build() {
            return new PlainDigest(this);
        }
    }

}
