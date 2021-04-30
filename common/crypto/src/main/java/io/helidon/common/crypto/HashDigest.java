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
 * The HashDigest is used for ordinary data digest creation and verification.
 * <br>
 * Should not be used for authentication purposes.
 */
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
        try {
            MessageDigest digest;
            if (provider == null) {
                digest = MessageDigest.getInstance(algorithm);
            } else {
                digest = MessageDigest.getInstance(algorithm, provider);
            }
            return Base64Value.create(digest.digest(value.toBytes()));
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Could not create digest", e);
        }
    }

    /**
     * Builder of the {@link HashDigest}.
     */
    public static final class Builder implements io.helidon.common.Builder<HashDigest> {

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
        public HashDigest build() {
            return new HashDigest(this);
        }
    }

}
