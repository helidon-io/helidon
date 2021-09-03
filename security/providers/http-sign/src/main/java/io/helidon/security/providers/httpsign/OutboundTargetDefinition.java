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

package io.helidon.security.providers.httpsign;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.security.util.TokenHandler;

/**
 * Configuration of outbound target to sign outgoing requests.
 */
public final class OutboundTargetDefinition {
    private final String keyId;
    private final String algorithm;
    private final KeyConfig keyConfig;
    private final HttpSignHeader header;
    private final byte[] hmacSharedSecret;
    private final SignedHeadersConfig signedHeadersConfig;
    private final TokenHandler tokenHandler;
    private final boolean backwardCompatibleEol;

    private OutboundTargetDefinition(Builder builder) {
        this.keyId = builder.keyId;
        this.algorithm = builder.algorithm;
        this.keyConfig = builder.keyConfig;
        this.header = builder.header;
        this.hmacSharedSecret = builder.hmacSharedSecret;
        this.signedHeadersConfig = builder.signedHeadersConfig;
        this.tokenHandler = builder.tokenHandler;
        this.backwardCompatibleEol = builder.backwardCompatibleEol;

        Objects.requireNonNull(algorithm, "Signature algorithm must not be null");
        Objects.requireNonNull(keyId, "Key id must not be null");
        Objects.requireNonNull(header, "Header to use must not be null");
        Objects.requireNonNull(signedHeadersConfig, "Configuration of how to sign headers must not be null");

        if (HttpSignProvider.ALGORITHM_HMAC.equals(algorithm)) {
            Objects.requireNonNull(hmacSharedSecret, "HMAC shared secret must not be null");
        } else if (HttpSignProvider.ALGORITHM_RSA.equals(algorithm)) {
            Objects.requireNonNull(keyConfig, "RSA Keys configuration must not be null");
        }
    }

    /**
     * Get a new builder .
     *
     * @param keyId keyId to send with signature
     * @return builder instance
     */
    public static Builder builder(String keyId) {
        return new Builder().keyId(keyId);
    }

    /**
     * Create a builder from configuration.
     *
     * @param config configuration located at this target, expects "key-id" to be a child
     * @return builder instance
     */
    public static Builder builder(Config config) {
        return new Builder().config(config);
    }

    /**
     * Create an instance from configuration.
     *
     * @param config configuration located at this outbound key, expects "key-id" to be a child
     * @return new instance configured from config
     */
    public static OutboundTargetDefinition create(Config config) {
        return builder(config).build();
    }

    /**
     * Key id of this service (will be mapped by target service to validate signature).
     *
     * @return key id string (may be an API key, key fingerprint, service name etc.)
     */
    public String keyId() {
        return keyId;
    }

    /**
     * Algorithm used by this signature.
     *
     * @return algorithm
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * Private key configuration for RSA based algorithms.
     *
     * @return private key location and configuration or empty optional if not configured
     */
    public Optional<KeyConfig> keyConfig() {
        return Optional.ofNullable(keyConfig);
    }

    /**
     * Shared secret for HMAC based algorithms.
     *
     * @return shared secret or empty optional if not configured
     */
    public Optional<byte[]> hmacSharedSecret() {
        return Optional.ofNullable(hmacSharedSecret);
    }

    /**
     * Header to store signature in.
     *
     * @return header type
     */
    public HttpSignHeader header() {
        return header;
    }

    /**
     * Configuration of method to headers to define headers to be signed.
     * <p>
     * The following headers have special handling:
     * <ul>
     * <li>date - if not present and required, will be added to request</li>
     * <li>host - if not present and required, will be added to request from target URI</li>
     * <li>(request-target) - as per spec, calculated from method and path</li>
     * <li>authorization - if {@link #header()} returns {@link HttpSignHeader#AUTHORIZATION} it is ignored</li>
     * </ul>
     *
     * @return configuration of headers to be signed
     */
    public SignedHeadersConfig signedHeadersConfig() {
        return signedHeadersConfig;
    }

    /**
     * When header type is set to {@link io.helidon.security.providers.httpsign.HttpSignHeader#CUSTOM},
     *  this handler will be used to create header in outbound request.
     *
     * @return token handler
     */
    public TokenHandler tokenHandler() {
        return tokenHandler;
    }

    /**
     * Whether to use old approach to EOL processing in signed data.
     * The old approach (pre Helidon 3.0.0) was adding a trailing end of line, which is
     * not aligned with the specification proposal.
     *
     * @return whether to use old approach (trailing EOL - {@code true}), or correct approach ({@code false}ú
     */
    public boolean backwardCompatibleEol() {
        return backwardCompatibleEol;
    }

    /**
     * Fluent API builder to build {@link OutboundTargetDefinition} instances.
     * Call {@link #build()} to create a new instance.
     */
    public static final class Builder implements io.helidon.common.Builder<OutboundTargetDefinition> {
        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());

        private String keyId;
        private String algorithm;
        private KeyConfig keyConfig;
        private HttpSignHeader header = HttpSignHeader.SIGNATURE;
        private byte[] hmacSharedSecret;
        private SignedHeadersConfig signedHeadersConfig = HttpSignProvider.DEFAULT_REQUIRED_HEADERS;
        private TokenHandler tokenHandler;
        // this is internal deprecation to make sure we switch this flag default to false for 3.0.0
        // to be removed in 4.0.0 (most likely)
        @Deprecated
        private boolean backwardCompatibleEol = true;

        private Builder() {
        }

        /**
         * Key id of this service (will be mapped by target service to validate signature).
         *
         * @param keyId key id mapped by target service
         * @return updated builder instance
         */
        public Builder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        /**
         * Header to store signature in.
         *
         * @param header header type
         * @return updated builder instance
         */
        public Builder header(HttpSignHeader header) {
            this.header = header;
            return this;
        }

        /**
         * Algorithm used by this signature.
         * Set automatically on call to methods {@link #privateKeyConfig(KeyConfig)} and {@link #hmacSecret(byte[])}.
         *
         * @param algorithm algorithm to use for outbound signatures
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Private key configuration for RSA based algorithms.
         * If called sets the algorithm to "rsa-sha256". Expects either explicit private key, or keystore and private key
         * alias.
         *
         * @param keyConfig private key configuration for outbound signatures
         * @return updated builder instance
         */
        public Builder privateKeyConfig(KeyConfig keyConfig) {
            if (null == algorithm) {
                algorithm = HttpSignProvider.ALGORITHM_RSA;
            }

            // make sure this is a private key (signature of outbound requests)
            keyConfig.privateKey()
                    .orElseThrow(() -> new HttpSignatureException("Configuration must contain a private key"));

            this.keyConfig = keyConfig;
            return this;
        }

        /**
         * Configuration of required and "if-present" headers to be signed for this target.
         * Defaults to the same as {@link HttpSignProvider.Builder#inboundRequiredHeaders(SignedHeadersConfig)}.
         *
         * @param config configuration of outbound headers to be signed for each method.
         * @return updated builder instance
         */
        public Builder signedHeaders(SignedHeadersConfig config) {
            this.signedHeadersConfig = config;
            return this;
        }

        /**
         * Shared secret for HMAC based algorithms.
         * Also sets the algorithm to "hmac-sha256"
         *
         * @param secret secret to sign outgoing requests (symmetric)
         * @return updated builder instance
         */
        public Builder hmacSecret(byte[] secret) {
            if (null == algorithm) {
                algorithm = HttpSignProvider.ALGORITHM_HMAC;
            }
            this.hmacSharedSecret = Arrays.copyOf(secret, secret.length);
            return this;
        }

        /**
         * Shared secret for HMAC based algorithms.
         * Calls {@link #hmacSecret(byte[])} getting bytes of the secret string with UTF-8.
         *
         * @param secret shared secret to sign outgoing requests
         * @return updated builder instance
         */
        public Builder hmacSecret(String secret) {
            return hmacSecret(secret.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Configure a token handler to create the outbound header.
         *
         * @param tokenHandler token handler to use
         * @return updated builder instance
         */
        public Builder tokenHandler(TokenHandler tokenHandler) {
            this.tokenHandler = tokenHandler;
            this.header = HttpSignHeader.CUSTOM;
            return this;
        }

        @Override
        public OutboundTargetDefinition build() {
            if (backwardCompatibleEol) {
                LOGGER.warning("HTTP signatures is using legacy HTTP signature processing. This will not work"
                                       + " with third party tools using the same spec and with Helidon newer than 3.0.0."
                                       + " Please configure 'backward-compatible-eol' to false to use the correct approach.");
            }
            return new OutboundTargetDefinition(this);
        }

        /**
         * Update this builder instance from configuration.
         *
         * @param config config instance
         * @return updated builder instance
         */
        public Builder config(Config config) {
            Builder builder = new Builder();

            // mandatory
            builder.keyId(config.get("key-id").asString().get());
            config.get("header").asString().map(HttpSignHeader::valueOf).ifPresent(builder::header);
            config.get("sign-headers").as(SignedHeadersConfig::create).ifPresent(builder::signedHeaders);
            config.get("private-key").as(KeyConfig::create).ifPresent(builder::privateKeyConfig);
            config.get("hmac.secret").asString().ifPresent(builder::hmacSecret);

            // last, as we configure defaults based on configuration
            config.get("algorithm").asString().ifPresent(builder::algorithm);

            // backward compatibility with previous Helidon versions
            config.get("backward-compatible-eol").asBoolean().ifPresent(this::backwardCompatibleEol);

            return builder;
        }

        /**
         * Until version 3.0.0 (exclusive) there is a trailing end of line added to the signed
         * data.
         * When configured to {@code false}, the correct approach is used.
         *
         * @param backwardCompatible whether to run in backward compatible mode
         * @return updated builder instance
         */
        public Builder backwardCompatibleEol(Boolean backwardCompatible) {
            this.backwardCompatibleEol = backwardCompatible;
            return this;
        }
    }
}
