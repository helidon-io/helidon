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

import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.security.SubjectType;

/**
 * Configuration of inbound client.
 * This information is used to validate incoming signatures and to create a principal.
 */
public class InboundClientDefinition {

    private final String keyId;
    private final String principalName;
    private final SubjectType subjectType;
    private final String algorithm;
    private final KeyConfig keyConfig;
    private final byte[] hmacSharedSecret;

    private InboundClientDefinition(Builder builder) {
        this.keyId = builder.keyId;
        this.algorithm = builder.algorithm;
        this.keyConfig = builder.keyConfig;
        this.hmacSharedSecret = builder.hmacSharedSecret;
        this.principalName = builder.principalName;
        this.subjectType = builder.subjectType;

        Objects.requireNonNull(algorithm, "Signature algorithm must not be null");
        Objects.requireNonNull(keyId, "Key id must not be null");
        Objects.requireNonNull(principalName, "Principal name must not be null");
        Objects.requireNonNull(subjectType, "Principal type must not be null");

        if (HttpSignProvider.ALGORITHM_HMAC.equals(algorithm)) {
            Objects.requireNonNull(hmacSharedSecret, "HMAC shared secret must not be null");
        } else if (HttpSignProvider.ALGORITHM_RSA.equals(algorithm)) {
            Objects.requireNonNull(keyConfig, "RSA Keys configuration must not be null");
        }
    }

    /**
     * Create a new builder for the keyId.
     *
     * @param keyId Key id as is received in inbound signature (mandatory part of the signature header) to
     *              map to configured RSA or HMAC key.
     * @return builder instance
     */
    public static Builder builder(String keyId) {
        return new Builder().keyId(keyId);
    }

    /**
     * Create a new builder from configuration.
     *
     * @param config configuration instance located at a single client definition (expect key-id as a child)
     * @return builder configured based on config
     */
    public static Builder builder(Config config) {
        return new Builder().config(config);
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration instance located at a single client definition (expect key-id as a child)
     * @return instance configured based on config
     */
    public static InboundClientDefinition create(Config config) {
        return new Builder().config(config).build();
    }

    /**
     * The key id of this client.
     *
     * @return key id to map this configuration to inbound signature
     */
    public String keyId() {
        return keyId;
    }

    /**
     * The principal name of the client.
     *
     * @return name to use when creating security principal for this client
     */
    public String principalName() {
        return principalName;
    }

    /**
     * The type of principal we have authenticated (either user or service, defaults to service).
     *
     * @return principal type to use when creating security principal for this client
     */
    public SubjectType subjectType() {
        return subjectType;
    }

    /**
     * Algorithm of signature used by this client.
     *
     * @return algorithm of signature expected in request
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * For rsa-sha256 algorithm, this provides access to the public key of the client.
     *
     * @return Public key configuration to validate signature or empty optional if none configured
     */
    public Optional<KeyConfig> keyConfig() {
        return Optional.ofNullable(keyConfig);
    }

    /**
     * For hmac-sha256 algorithm, this provides access to a secret shared with the client.
     *
     * @return shared secret to validate signature or empty optional if none configured
     */
    public Optional<byte[]> hmacSharedSecret() {
        return Optional.ofNullable(hmacSharedSecret);
    }

    /**
     * Fluent API builder to create a new instance of {@link InboundClientDefinition}.
     * Use {@link #build()} to create the instance.
     */
    public static final class Builder implements io.helidon.common.Builder<InboundClientDefinition> {
        private String keyId;
        private String algorithm;
        private KeyConfig keyConfig;
        private byte[] hmacSharedSecret;
        private String principalName;
        private SubjectType subjectType = SubjectType.SERVICE;

        private Builder() {
        }

        /**
         * The principal name of the client, defaults to keyId if not configured.
         *
         * @param name name of security principal
         * @return updated builder instance
         */
        public Builder principalName(String name) {
            this.principalName = name;
            return this;
        }

        /**
         * The key id of this client to map to this signature validation configuration.
         *
         * @param keyId key id as provided in inbound signature
         * @return updated builder instance
         */
        public Builder keyId(String keyId) {
            this.keyId = keyId;
            if (this.principalName == null) {
                this.principalName = keyId;
            }
            return this;
        }

        /**
         * The type of principal we have authenticated (either user or service, defaults to service).
         *
         * @param type principal type
         * @return updated builder instance
         */
        public Builder subjectType(SubjectType type) {
            this.subjectType = type;
            return this;
        }

        /**
         * Algorithm of signature used by this client.
         * Currently supported:
         * <ul>
         * <li>rsa-sha256 - asymmetric based on public/private keys</li>
         * <li>hmac-sha256 - symmetric based on a shared secret</li>
         * </ul>
         *
         * @param algorithm algorithm used
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * For algorithms based on public/private key (such as rsa-sha256), this provides access to the public key of the client.
         *
         * @param keyConfig keys configured to access a public key to validate signature
         * @return updated builder instance
         */
        public Builder publicKeyConfig(KeyConfig keyConfig) {
            if (null == algorithm) {
                algorithm = HttpSignProvider.ALGORITHM_RSA;
            }
            // make sure this is a public key (validation of inbound signatures)
            keyConfig.publicKey()
                    .orElseThrow(() -> new HttpSignatureException("Configuration must contain a public key"));

            this.keyConfig = keyConfig;
            return this;
        }

        /**
         * For hmac-sha256 algorithm, this provides access to a secret shared with the client.
         *
         * @param secret shared secret to validate signature
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
         * Helper method to configure a password-like secret (instead of byte based {@link #hmacSecret(byte[])}.
         * The password is transformed to bytes with {@link StandardCharsets#UTF_8} charset.
         *
         * @param secret shared secret to validate signature
         * @return updated builder instance
         */
        public Builder hmacSecret(String secret) {
            return hmacSecret(secret.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InboundClientDefinition build() {
            return new InboundClientDefinition(this);
        }

        /**
         * Create a builder instance from configuration.
         *
         * @param config config instance
         * @return builder instance initialized from config
         */
        public Builder config(Config config) {
            keyId(config.get("key-id").asString().get());
            config.get("principal-name").asString().ifPresent(this::principalName);
            config.get("principal-type").asString().as(SubjectType::valueOf).ifPresent(this::subjectType);
            config.get("public-key").as(KeyConfig::create).ifPresent(this::publicKeyConfig);
            config.get("hmac.secret").asString().ifPresent(this::hmacSecret);
            config.get("algorithm").asString().ifPresent(this::algorithm);

            return this;
        }
    }
}
