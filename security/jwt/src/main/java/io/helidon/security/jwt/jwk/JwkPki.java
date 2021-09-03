/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.jwt.jwk;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.JwtUtil;

/**
 * PKI specific features of a JWK (Public/private key types of keys).
 */
@SuppressWarnings("WeakerAccess") // constants should be public
abstract class JwkPki extends Jwk {
    /**
     * JWK parameter for X.509 certificate chain URL.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.6">RFC 7517, section 4.6.</a>
     */
    public static final String PARAM_X509_CHAIN_URL = "x5u";
    /**
     * JWK parameter for X.509 certificate chain array.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.7">RFC 7517, section 4.7.</a>
     */
    public static final String PARAM_X509_CHAIN = "x5c";
    /**
     * JWK parameter for SHA 1 Thumbprint of X.509 certificate.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.8">RFC 7517, section 4.8.</a>
     */
    public static final String PARAM_X509_SHA_1 = "x5t";
    /**
     * JWK parameter for SHA 256 Thumbprint of X.509 certificate.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.9">RFC 7517, section 4.9.</a>
     */
    public static final String PARAM_X509_SHA_256 = "x5t#S256";

    private static final Logger LOGGER = Logger.getLogger(JwkPki.class.getName());
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final Optional<PrivateKey> privateKey;
    private final PublicKey publicKey;
    private final Optional<List<X509Certificate>> certificateChain;
    private final Optional<byte[]> sha1Thumbprint;
    private final Optional<byte[]> sha256Thumbprint;

    JwkPki(Builder<?> builder, PrivateKey privKey, PublicKey pubKey, String defaultAlgorithm) {
        super(builder, defaultAlgorithm);

        this.privateKey = Optional.ofNullable(privKey);
        this.publicKey = pubKey;
        this.certificateChain = Optional.ofNullable(builder.certificateChain).map(Collections::unmodifiableList);
        this.sha1Thumbprint = Optional.ofNullable(builder.sha1Thumbprint);
        this.sha256Thumbprint = Optional.ofNullable(builder.sha256Thumbprint);
    }

    public Optional<PrivateKey> privateKey() {
        return privateKey;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public Optional<List<X509Certificate>> certificateChain() {
        return certificateChain;
    }

    public Optional<byte[]> sha1Thumbprint() {
        return sha1Thumbprint;
    }

    public Optional<byte[]> sha256Thumbprint() {
        return sha256Thumbprint;
    }

    abstract String signatureAlgorithm();

    @Override
    public boolean doVerify(byte[] signedBytes, byte[] signatureToVerify) {
        String alg = signatureAlgorithm();

        if (ALG_NONE.equals(alg)) {
            return verifyNoneAlg(signatureToVerify);
        }

        Signature signature = JwtUtil.getSignature(alg);

        try {
            signature.initVerify(publicKey);
            signature.update(signedBytes);
            return signature.verify(signatureToVerify);
        } catch (Exception e) {
            throw new JwtException("Failed to verify signature. It may still be valid, but an exception was thrown", e);
        }
    }

    @Override
    public byte[] doSign(byte[] bytesToSign) {
        String alg = signatureAlgorithm();
        if (ALG_NONE.equals(alg)) {
            return EMPTY_BYTES;
        }
        Signature signature = JwtUtil.getSignature(alg);

        try {
            PrivateKey privateKey = this.privateKey
                    .orElseThrow(() -> new JwtException("To sign data, private key MUST be present"));

            signature.initSign(privateKey);
            signature.update(bytesToSign);
            return signature.sign();
        } catch (Exception e) {
            throw new JwtException("Failed to sign data", e);
        }
    }

    // this builder is not public, as a specific key type must be built
    static class Builder<T extends Builder<T>> extends Jwk.Builder<T> {
        private final T myInstance;
        private List<X509Certificate> certificateChain;
        private byte[] sha1Thumbprint;
        private byte[] sha256Thumbprint;

        @SuppressWarnings("unchecked")
        Builder() {
            this.myInstance = (T) this;
        }

        private static List<X509Certificate> processCertChain(List<String> base64s) {
            LinkedList<X509Certificate> certs = new LinkedList<>();

            CertificateFactory cf;
            try {
                cf = CertificateFactory.getInstance("X.509");
            } catch (CertificateException e) {
                throw new JwtException("Failed to get certificate factory. This is JVM misconfiguration", e);
            }

            base64s.forEach(it -> {
                byte[] rawBytes = DECODER.decode(it);
                try {
                    X509Certificate certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(rawBytes));
                    certs.add(certificate);
                } catch (CertificateException e) {
                    throw new JwtException("Failed to read certificate from JWK", e);
                }
            });

            return certs;
        }

        private static List<X509Certificate> processCertChain(URI uri) {
            LOGGER.log(Level.SEVERE, "Certificate chain from URL is not (yet) supported");
            return new LinkedList<>();
        }

        /**
         * Set certificate chain of the JWK to be built.
         *
         * @param chain certificate chain, where the certificate of this JWK's public must be the first in the list
         * @return updated builder instance
         */
        public T certificateChain(List<X509Certificate> chain) {
            if (null == this.certificateChain) {
                this.certificateChain = new LinkedList<>();
            } else {
                this.certificateChain.clear();
            }
            this.certificateChain.addAll(chain);
            return myInstance;
        }

        /**
         * Add a certificate to certificate chain of the JWK to be built.
         *
         * @param cert certificate to add to the chain
         * @return updated builder instance
         */
        public T addCertificateChain(X509Certificate cert) {
            if (null == this.certificateChain) {
                this.certificateChain = new LinkedList<>();
            }
            this.certificateChain.add(cert);
            return myInstance;
        }

        /**
         * Thumbprint (X.509 Certificate SHA-1 Thumbprint)
         * of the DER encoding of the certificate.
         *
         * Sometimes referred to as fingerprint.
         *
         * @param thumbprint thumbprint bytes (raw bytes)
         * @return updated builder instance
         */
        public T sha1Thumbprint(byte[] thumbprint) {
            this.sha1Thumbprint = thumbprint;
            return myInstance;
        }

        /**
         * Thumbprint (X.509 Certificate SHA-256 Thumbprint)
         * of the DER encoding of the certificate.
         *
         * Sometimes referred to as fingerprint.
         *
         * @param thumbprint thumbprint bytes (raw bytes)
         * @return updated builder instance
         */
        public T sha256Thumbprint(byte[] thumbprint) {
            this.sha256Thumbprint = thumbprint;
            return myInstance;
        }

        T fromJson(JsonObject json) {
            super.fromJson(json);
            // get cert chain from URL or from fields if present
            JwtUtil.getString(json, PARAM_X509_CHAIN_URL)
                                        .map(URI::create)
                                        .map(Builder::processCertChain)
                    .or(() -> JwtUtil.getStrings(json, PARAM_X509_CHAIN)
                            // certificate chain as base64 encoded array
                            .map(Builder::processCertChain))
                    .ifPresent(this::certificateChain);

            // thumbprints
            this.sha1Thumbprint = JwtUtil.getByteArray(json, PARAM_X509_SHA_1, "SHA-1 Certificate Thumbprint").orElse(null);
            this.sha256Thumbprint = JwtUtil.getByteArray(json, PARAM_X509_SHA_256, "SHA-256 Certificate Thumbprint").orElse(null);

            return myInstance;
        }
    }

}
