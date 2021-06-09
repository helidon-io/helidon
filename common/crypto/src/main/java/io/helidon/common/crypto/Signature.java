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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Objects;

import io.helidon.common.Base64Value;

/**
 * The Signature class is used for simplification of the digital signature creation and verification.
 */
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

    /**
     * Builder of the {@link Signature}.
     */
    public static final class Builder implements io.helidon.common.Builder<Signature> {

        private String algorithm = ALGORITHM_SHA256_RSA;
        private String provider = null;
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

        /**
         * Set private key which should be used for signature creation.
         *
         * @param privateKey private key
         * @return updated builder instance
         */
        public Builder privateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Set public key which should be used for signature verification.
         *
         * @param publicKey private key
         * @return updated builder instance
         */
        public Builder publicKey(PublicKey publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        @Override
        public Signature build() {
            if (privateKey == null && publicKey == null) {
                throw new CryptoException("At least private or public key has to be set");
            }
            return new Signature(this);
        }
    }

}
