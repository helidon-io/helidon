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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.Base64Value;

/**
 * The Signature class is used for simplification of the digital signature creation and verification.
 * Algorithm, provider and each configured key are validated when the instance is built.
 * A private key enables signing and a public key enables verification; either or both can be configured.
 * Keys are validated individually, without checking that they form a matching pair.
 */
@Api.Stable
public class Signature implements Digest {

    /**
     * RSA signature algorithm with no digest algorithm specified.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_NONE_RSA = "NONEwithRSA";

    /**
     * RSA signature algorithm with MD2 digest algorithm.
     * <br>
     * Value is: {@value}.
     * @deprecated It is strongly recommended not to use this algorithm for signature purposes as stated here
     * <a href="https://tools.ietf.org/html/rfc6149#section-6">RFC6149 - Section 6</a>.
     */
    @Deprecated
    public static final String ALGORITHM_MD2_RSA = "MD2withRSA";

    /**
     * RSA signature algorithm with MD5 digest algorithm.
     * <br>
     * Value is: {@value}.
     * @deprecated It is strongly recommended not to use this algorithm for signature purposes as stated here
     * <a href="https://tools.ietf.org/html/rfc6151#section-2">RFC6149 - Section 2</a>.
     */
    @Deprecated
    public static final String ALGORITHM_MD5_RSA = "MD5withRSA";

    /**
     * RSA signature algorithm with SHA1 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA1_RSA = "SHA1withRSA";

    /**
     * RSA signature algorithm with SHA224 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA224_RSA = "SHA224withRSA";

    /**
     * RSA signature algorithm with SHA256 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA256_RSA = "SHA256withRSA";

    /**
     * RSA signature algorithm with SHA384 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA384_RSA = "SHA384withRSA";

    /**
     * RSA signature algorithm with SHA512 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA512_RSA = "SHA512withRSA";

    /**
     * RSA signature algorithm with SHA512/224 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA512_224_RSA = "SHA512/224withRSA";

    /**
     * RSA signature algorithm with SHA512/256 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA512_256_RSA = "SHA512/256withRSA";

    /**
     * Elliptic curve digital signature algorithm with no digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_NONE_ECDSA = "NONEwithECDSA";

    /**
     * Elliptic curve digital signature algorithm with SHA1 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA1_ECDSA = "SHA1withECDSA";

    /**
     * Elliptic curve digital signature algorithm with SHA224 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA224_ECDSA = "SHA224withECDSA";

    /**
     * Elliptic curve digital signature algorithm with SHA256 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA256_ECDSA = "SHA256withECDSA";

    /**
     * Elliptic curve digital signature algorithm with SHA384 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA384_ECDSA = "SHA384withECDSA";

    /**
     * Elliptic curve digital signature algorithm with SHA512 digest algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_SHA512_ECDSA = "SHA512withECDSA";

    private final String algorithm;
    private final String provider;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private Signature(Builder builder) {
        this.algorithm = builder.algorithm;
        this.provider = builder.provider;
        this.privateKey = builder.privateKey;
        this.publicKey = builder.publicKey;
        validateConfiguration();
    }

    /**
     * Create a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Base64Value digest(Base64Value value) {
        if (privateKey == null) {
            throw new CryptoException("Private key not set. This object cannot create new signatures");
        }
        Objects.requireNonNull(value, "Value to sign cannot be null");
        try {
            java.security.Signature signature = getSignature();
            signature.initSign(privateKey);
            signature.update(value.toBytes());
            return Base64Value.create(signature.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | NoSuchProviderException e) {
            throw new CryptoException("Could not sign data", e);
        }
    }

    @Override
    public boolean verify(Base64Value toVerify, Base64Value digestToVerify) {
        if (publicKey == null) {
            throw new CryptoException("Public key not set. This object cannot verify the signatures");
        }
        Objects.requireNonNull(toVerify, "Value to verify cannot be null");
        Objects.requireNonNull(digestToVerify, "Signature to verify cannot be null");
        try {
            java.security.Signature signature = getSignature();
            signature.initVerify(publicKey);
            signature.update(toVerify.toBytes());
            return signature.verify(digestToVerify.toBytes());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | NoSuchProviderException e) {
            throw new CryptoException("Could not verify signature", e);
        }
    }

    private java.security.Signature getSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
        if (provider == null) {
            return java.security.Signature.getInstance(algorithm);
        } else {
            return java.security.Signature.getInstance(algorithm, provider);
        }
    }

    private void validateConfiguration() {
        try {
            if (privateKey != null) {
                getSignature().initSign(privateKey);
            }
            if (publicKey != null) {
                getSignature().initVerify(publicKey);
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | IllegalArgumentException e) {
            throw new CryptoException("Could not initialize signature", e);
        }
    }

    /**
     * Builder of the {@link Signature}.
     * Optional values can be removed using the corresponding {@code clear} methods; setters do not accept {@code null}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, Signature> {

        private String algorithm = ALGORITHM_SHA256_RSA;
        private String provider;
        private PrivateKey privateKey;
        private PublicKey publicKey;

        private Builder() {
        }

        /**
         * Set algorithm which should be used.
         * <br>
         * Default value is {@link #ALGORITHM_SHA256_RSA}.
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
         * Clear the private key, disabling signing for subsequently built instances.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearPrivateKey() {
            this.privateKey = null;
            return this;
        }

        /**
         * Set private key which should be used for signature creation.
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
         * Clear the public key, disabling verification for subsequently built instances.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearPublicKey() {
            this.publicKey = null;
            return this;
        }

        /**
         * Set public key which should be used for signature verification.
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
         * Algorithm used for signature creation and verification.
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
         * Private key used for signature creation, if configured.
         *
         * @return private key, or empty if not configured
         * @since 28.0.0
         */
        public Optional<PrivateKey> privateKey() {
            return Optional.ofNullable(privateKey);
        }

        /**
         * Public key used for signature verification, if configured.
         *
         * @return public key, or empty if not configured
         * @since 28.0.0
         */
        public Optional<PublicKey> publicKey() {
            return Optional.ofNullable(publicKey);
        }

        /**
         * Build a signature instance after validating JCA initialization for each configured key.
         * At least one key is required; keys are not checked for a matching pair.
         *
         * @return configured signature instance
         * @throws CryptoException if no key is configured or the configuration cannot initialize a signature
         */
        @Override
        public Signature build() {
            if (privateKey == null && publicKey == null) {
                throw new CryptoException("At least private or public key has to be set");
            }
            return new Signature(this);
        }
    }

}
