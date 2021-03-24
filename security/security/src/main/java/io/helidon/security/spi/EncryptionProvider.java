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

import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

/**
 * Provider that can encrypt and decrypt secrets.
 *
 * @param <T> type of the custom configuration object
 * @see io.helidon.security.Security#encrypt(String, byte[])
 * @see io.helidon.security.Security#decrypt(String, String)
 */
public interface EncryptionProvider<T extends ProviderConfig> extends SecurityProvider {
    /**
     * Create encryption support from configuration.
     *
     * @param config config located on the node of the specific encryption {@code config} node
     * @return encryption support to encrypt/decrypt
     */
    EncryptionSupport encryption(Config config);

    /**
     * Create encryption support from configuration object.
     *
     * @param providerConfig configuring a specific encryption
     * @return encryption support to encrypt/decrypt
     */
    EncryptionSupport encryption(T providerConfig);

    /**
     * Encryption support created for each named encryption configuration.
     */
    class EncryptionSupport {
        private final Function<byte[], Single<String>> encryptionFunction;
        private final Function<String, Single<byte[]>> decryptionFunction;

        /**
         * Encryption support based on the two functions.
         *
         * @param encryptionFunction encrypts the provided bytes into cipher text
         * @param decryptionFunction decrypts cipher text into bytes
         */
        protected EncryptionSupport(Function<byte[], Single<String>> encryptionFunction,
                                    Function<String, Single<byte[]>> decryptionFunction) {
            this.encryptionFunction = encryptionFunction;
            this.decryptionFunction = decryptionFunction;
        }

        /**
         * Create a new support based on encrypt and decrypt functions.
         *
         * @param encryptionFunction encrypts the provided bytes into cipher text
         * @param decryptionFunction decrypts cipher text into bytes
         * @return new encryption support
         */
        public static EncryptionSupport create(Function<byte[], Single<String>> encryptionFunction,
                                               Function<String, Single<byte[]>> decryptionFunction) {
            return new EncryptionSupport(encryptionFunction, decryptionFunction);
        }

        /**
         * Encrypt the bytes.
         *
         * @param bytes bytes to encrypt
         * @return future with the encrypted cipher text
         */
        public Single<String> encrypt(byte[] bytes) {
            return encryptionFunction.apply(bytes);
        }

        /**
         * Decrypt the bytes.
         *
         * @param encrypted cipher text
         * @return future with the decrypted bytes
         */
        public Single<byte[]> decrypt(String encrypted) {
            return decryptionFunction.apply(encrypted);
        }
    }
}
