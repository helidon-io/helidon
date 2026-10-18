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

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.Cipher;

import io.helidon.common.Api;
import io.helidon.common.Base64Value;

/**
 * This class provides simple and stateless way to encrypt and decrypt messages using selected asymmetric cipher.
 * <br>
 * It requires to have a {@link PrivateKey} provided for decryption purposes and
 * a {@link PublicKey} for encryption purposes.
 */
@Api.Stable
public class AsymmetricCipher implements CommonCipher {

    /**
     * RSA cipher with ECB method using optimal asymmetric encryption padding with MD5 and MGF1.
     * <br>
     * Value is: {@value}.
     * @deprecated It is strongly recommended not to use this algorithm as stated here
     * <a href="https://tools.ietf.org/html/rfc6151#section-2">RFC6151 - 2</a>.
     */
    @Deprecated
    public static final String ALGORITHM_RSA_ECB_OAEP_MD5 = "RSA/ECB/OAEPWithMD5AndMGF1Padding";

    /**
     * RSA cipher with ECB method using optimal asymmetric encryption padding with SHA1 and MGF1.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_RSA_ECB_OAEP_SHA1 = "RSA/ECB/OAEPWithSHA1AndMGF1Padding";

    /**
     * RSA cipher with ECB method using optimal asymmetric encryption padding with SHA-256 and MGF1.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_RSA_ECB_OAEP256 = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * RSA cipher with ECB method using optimal asymmetric encryption padding with SHA-384 and MGF1.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_RSA_ECB_OAEP384 = "RSA/ECB/OAEPWithSHA-384AndMGF1Padding";

    /**
     * RSA cipher with ECB method using optimal asymmetric encryption padding with SHA-512/224 and MGF1.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_RSA_ECB_OAEP512_224 = "RSA/ECB/OAEPWithSHA-512/224AndMGF1Padding";

    /**
     * RSA cipher with ECB method using optimal asymmetric encryption padding with SHA-512/256 and MGF1.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_RSA_ECB_OAEP512_256 = "RSA/ECB/OAEPWithSHA-512/256AndMGF1Padding";

    /**
     * RSA cipher with ECB method using PKCS1 padding.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_RSA_ECB_PKCS1 = "RSA/ECB/PKCS1Padding";

    private final String algorithm;
    private final String provider;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private AsymmetricCipher(Builder builder) {
        this.algorithm = builder.algorithm;
        this.provider = builder.provider;
        this.privateKey = builder.privateKey;
        this.publicKey = builder.publicKey;
    }

    /**
     * Create a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Encrypt the message with the provided public key and selected algorithm using normal JCA provider selection.
     *
     * @param algorithm algorithm name
     * @param publicKey public key used for encryption
     * @param message message to be encrypted
     * @return encrypted message
     * @since 28.0.0
     */
    public static Base64Value encrypt(String algorithm, PublicKey publicKey, Base64Value message) {
        return encryptMessage(algorithm, null, publicKey, message);
    }

    /**
     * Encrypt the message with the provided public key and selected algorithm.
     *
     * @param algorithm algorithm name
     * @param provider algorithm provider, or {@code null} for normal JCA provider selection
     * @param publicKey public key used for encryption
     * @param message message to be encrypted
     * @return encrypted message
     * @deprecated use {@link #encrypt(String, PublicKey, Base64Value)} for normal JCA provider selection,
     *             or {@link #builder()} with {@link Builder#provider(String)} to select an explicit provider
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static Base64Value encrypt(String algorithm, String provider, PublicKey publicKey, Base64Value message) {
        return encryptMessage(algorithm, provider, publicKey, message);
    }

    /**
     * Decrypt the message with the provided private key and selected algorithm using normal JCA provider selection.
     *
     * @param algorithm algorithm name
     * @param privateKey private key used for decryption
     * @param message message to be decrypted
     * @return decrypted message
     * @since 28.0.0
     */
    public static Base64Value decrypt(String algorithm, PrivateKey privateKey, Base64Value message) {
        return decryptMessage(algorithm, null, privateKey, message);
    }

    /**
     * Decrypt the message with the provided private key and selected algorithm.
     *
     * @param algorithm algorithm name
     * @param provider algorithm provider, or {@code null} for normal JCA provider selection
     * @param privateKey private key used for decryption
     * @param message message to be decrypted
     * @return decrypted message
     * @deprecated use {@link #decrypt(String, PrivateKey, Base64Value)} for normal JCA provider selection,
     *             or {@link #builder()} with {@link Builder#provider(String)} to select an explicit provider
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static Base64Value decrypt(String algorithm, String provider, PrivateKey privateKey, Base64Value message) {
        return decryptMessage(algorithm, provider, privateKey, message);
    }

    @Override
    public Base64Value encrypt(Base64Value message) {
        if (publicKey == null) {
            throw new CryptoException("No public key present. Could not perform encrypt operation");
        }
        return encryptMessage(algorithm, provider, publicKey, message);
    }

    @Override
    public Base64Value decrypt(Base64Value encrypted) {
        if (privateKey == null) {
            throw new CryptoException("No private key present. Could not perform decryption operation");
        }
        return decryptMessage(algorithm, provider, privateKey, encrypted);
    }

    private static Base64Value encryptMessage(String algorithm, String provider, PublicKey publicKey, Base64Value message) {
        Objects.requireNonNull(algorithm, "Algorithm for encryption cannot be null");
        Objects.requireNonNull(publicKey, "Public key cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        try {
            return performCryptoOperation(Cipher.ENCRYPT_MODE, algorithm, provider, publicKey, message);
        } catch (Exception e) {
            throw new CryptoException("Message could not be encrypted", e);
        }
    }

    private static Base64Value decryptMessage(String algorithm, String provider, PrivateKey privateKey, Base64Value message) {
        Objects.requireNonNull(algorithm, "Algorithm for decryption cannot be null");
        Objects.requireNonNull(privateKey, "Private key cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        try {
            return performCryptoOperation(Cipher.DECRYPT_MODE, algorithm, provider, privateKey, message);
        } catch (Exception e) {
            throw new CryptoException("Message could not be decrypted", e);
        }
    }

    private static Base64Value performCryptoOperation(int mode, String algorithm, String provider, Key key, Base64Value data)
            throws Exception {
        Cipher cipher;
        if (provider == null) {
            cipher = Cipher.getInstance(algorithm);
        } else {
            cipher = Cipher.getInstance(algorithm, provider);
        }
        cipher.init(mode, key);
        return Base64Value.create(cipher.doFinal(data.toBytes()));
    }

    /**
     * Builder of the {@link AsymmetricCipher}.
     * Optional values can be removed using the corresponding {@code clear} methods; setters do not accept {@code null}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, AsymmetricCipher> {

        private String algorithm = ALGORITHM_RSA_ECB_OAEP256;
        private String provider;
        private PrivateKey privateKey;
        private PublicKey publicKey;

        private Builder() {
        }

        /**
         * Set algorithm which should be used.
         * <br>
         * Default value is {@link #ALGORITHM_RSA_ECB_OAEP256}.
         * Changing the algorithm preserves any explicit provider selection.
         *
         * @param algorithm algorithm to be used
         * @return updated builder instance
         * @throws NullPointerException if algorithm is {@code null}
         */
        public Builder algorithm(String algorithm) {
            Objects.requireNonNull(algorithm);
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
         * Clear the private key used for decryption.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearPrivateKey() {
            this.privateKey = null;
            return this;
        }

        /**
         * Private key which should be used for decryption.
         * <p>
         * To remove the private key, call {@link #clearPrivateKey()} instead of passing {@code null}.
         *
         * @param privateKey private key
         * @return updated builder instance
         * @throws NullPointerException if private key is {@code null}
         */
        public Builder privateKey(PrivateKey privateKey) {
            Objects.requireNonNull(privateKey);
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Clear the public key used for encryption.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearPublicKey() {
            this.publicKey = null;
            return this;
        }

        /**
         * Public key which should be used for encryption.
         * <p>
         * To remove the public key, call {@link #clearPublicKey()} instead of passing {@code null}.
         *
         * @param publicKey public key
         * @return updated builder instance
         * @throws NullPointerException if public key is {@code null}
         */
        public Builder publicKey(PublicKey publicKey) {
            Objects.requireNonNull(publicKey);
            this.publicKey = publicKey;
            return this;
        }

        /**
         * Algorithm used for encryption and decryption.
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
         * Private key used for decryption, if configured.
         *
         * @return private key
         * @since 28.0.0
         */
        public Optional<PrivateKey> privateKey() {
            return Optional.ofNullable(privateKey);
        }

        /**
         * Public key used for encryption, if configured.
         *
         * @return public key
         * @since 28.0.0
         */
        public Optional<PublicKey> publicKey() {
            return Optional.ofNullable(publicKey);
        }

        @Override
        public AsymmetricCipher build() {
            if (publicKey == null && privateKey == null) {
                throw new CryptoException("At least private or public key has to be set");
            }
            return new AsymmetricCipher(this);
        }

    }

}
