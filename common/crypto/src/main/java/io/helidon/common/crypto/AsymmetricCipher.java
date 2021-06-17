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

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

import javax.crypto.Cipher;

import io.helidon.common.Base64Value;

/**
 * This class provides simple and stateless way to encrypt and decrypt messages using selected asymmetric cipher.
 * <br>
 * It requires to have a {@link PrivateKey} provided for decryption purposes and
 * a {@link PublicKey} for encryption purposes.
 */
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
     * Encrypt the message with the provided public key and selected algorithm.
     *
     * @param algorithm algorithm name
     * @param provider algorithm provider
     * @param publicKey public key used for encryption
     * @param message message to be encrypted
     * @return encrypted message
     */
    public static Base64Value encrypt(String algorithm, String provider, PublicKey publicKey, Base64Value message) {
        Objects.requireNonNull(algorithm, "Algorithm for encryption cannot be null");
        Objects.requireNonNull(publicKey, "Public key cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        try {
            return performCryptoOperation(Cipher.ENCRYPT_MODE, algorithm, provider, publicKey, message);
        } catch (Exception e) {
            throw new CryptoException("Message could not be encrypted", e);
        }
    }

    /**
     * Decrypt the message with the provided private key and selected algorithm.
     *
     * @param algorithm algorithm name
     * @param provider algorithm provider
     * @param privateKey private key used for decryption
     * @param message message to be decrypted
     * @return decrypted message
     */
    public static Base64Value decrypt(String algorithm, String provider, PrivateKey privateKey, Base64Value message) {
        Objects.requireNonNull(algorithm, "Algorithm for decryption cannot be null");
        Objects.requireNonNull(privateKey, "Private key cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        try {
            return performCryptoOperation(Cipher.DECRYPT_MODE, algorithm, provider, privateKey, message);
        } catch (Exception e) {
            throw new CryptoException("Message could not be decrypted", e);
        }
    }

    @Override
    public Base64Value encrypt(Base64Value message) {
        if (publicKey == null) {
            throw new CryptoException("No public key present. Could not perform encrypt operation");
        }
        return encrypt(algorithm, provider, publicKey, message);
    }

    @Override
    public Base64Value decrypt(Base64Value encrypted) {
        if (privateKey == null) {
            throw new CryptoException("No private key present. Could not perform decryption operation");
        }
        return decrypt(algorithm, provider, privateKey, encrypted);
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
     */
    public static final class Builder implements io.helidon.common.Builder<AsymmetricCipher> {

        private String algorithm = ALGORITHM_RSA_ECB_OAEP256;
        private String provider = null;
        private PrivateKey privateKey;
        private PublicKey publicKey;

        private Builder() {
        }

        /**
         * Set algorithm which should be used.
         * <br>
         * Default value is {@link #ALGORITHM_RSA_ECB_OAEP256}.
         *
         * @param algorithm algorithm to be used
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "Algorithm cannot be null");
            this.provider = null;
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

        /**
         * Private key which should be used for decryption.
         *
         * @param privateKey private key
         * @return updated builder instance
         */
        public Builder privateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Public key which should be used for encryption.
         *
         * @param publicKey public key
         * @return updated builder instance
         */
        public Builder publicKey(PublicKey publicKey) {
            this.publicKey = publicKey;
            return this;
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
