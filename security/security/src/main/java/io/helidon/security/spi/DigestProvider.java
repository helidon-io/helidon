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

package io.helidon.security.spi;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

/**
 * Provider that can create digests of bytes, and then verify them.
 * The digest may be a signature, HMAC or similar.
 *
 * @param <T> type of the custom configuration object
 * @see io.helidon.security.Security#digest(String, byte[])
 * @see io.helidon.security.Security#verifyDigest(String, byte[], String)
 */
public interface DigestProvider<T extends ProviderConfig> extends SecurityProvider {
    /**
     * Create digest support from configuration.
     *
     * @param config config located on the node of the specific digest {@code config} node
     * @return digest support to digest/verify
     */
    DigestSupport digest(Config config);

    /**
     * Create digest support from configuration object.
     *
     * @param providerConfig configuring a specific digest
     * @return digest support to digest/verify
     */
    DigestSupport digest(T providerConfig);

    /**
     * Function to generate a digest from bytes.
     */
    @FunctionalInterface
    interface DigestFunction {
        /**
         * Create digest.
         *
         * @param data data to digest
         * @param preHashed whether the data is already a hash ({@code true}), or the raw data ({@code false})
         * @return future with the digest string (signature, HMAC)
         */
        Single<String> apply(byte[] data, Boolean preHashed);
    }

    /**
     * Function to verify a digest string.
     */
    @FunctionalInterface
    interface VerifyFunction {
        /**
         * Verify digest.
         *
         * @param data data that was digested
         * @param preHashed whether the data is already a hash
         * @param digest original digest of the data (signature, HMAC)
         * @return future with the result of verification
         */
        Single<Boolean> apply(byte[] data, Boolean preHashed, String digest);
    }

    /**
     * Digest support created for each named digest configuration, used by {@link io.helidon.security.Security}
     * for {@link io.helidon.security.Security#digest(String, byte[])}
     * and {@link io.helidon.security.Security#verifyDigest(String, byte[], String)} methods.
     */
    class DigestSupport {
        private final DigestFunction digestFunction;
        private final VerifyFunction verifyFunction;

        /**
         * Digest support based on the two functions.
         *
         * @param digestFunction digest function
         * @param verifyFunction verify function
         */
        protected DigestSupport(DigestFunction digestFunction,
                                VerifyFunction verifyFunction) {
            this.digestFunction = digestFunction;
            this.verifyFunction = verifyFunction;
        }

        /**
         * Create a new support based on digest and verify functions.
         *
         * @param digestFunction digest function
         * @param verifyFunction verify function
         * @return new digest support
         */
        public static DigestSupport create(DigestFunction digestFunction,
                                           VerifyFunction verifyFunction) {
            return new DigestSupport(digestFunction, verifyFunction);
        }

        /**
         * Generates a signature or an HMAC.
         * @param bytes bytes to sign
         * @param preHashed whether the bytes are pre-hashed
         * @return future with the digest (signature or HMAC)
         */
        public Single<String> digest(byte[] bytes, boolean preHashed) {
            return digestFunction.apply(bytes, preHashed);
        }

        /**
         * Verifies a signature or an HMAC.
         *
         * @param bytes bytes to verify
         * @param preHashed whether the bytes are pre-hashed
         * @param digest digest obtained from a third-part
         * @return future with {@code true} if the digest is valid, {@code false} if not valid, and an error if not
         *  a supported digest
         */
        public Single<Boolean> verify(byte[] bytes, boolean preHashed, String digest) {
            return verifyFunction.apply(bytes, preHashed, digest);
        }
    }
}
