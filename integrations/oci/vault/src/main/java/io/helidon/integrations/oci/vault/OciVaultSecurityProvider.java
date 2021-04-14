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

package io.helidon.integrations.oci.vault;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.security.spi.DigestProvider;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.ProviderConfig;
import io.helidon.security.spi.SecretsProvider;

/**
 * Security provider to retrieve secrets from OCI Vault, and to use OCI KMS for encryption,
 * decryption and signatures.
 */
public class OciVaultSecurityProvider implements SecretsProvider<OciVaultSecurityProvider.OciVaultSecretConfig>,
                                                 EncryptionProvider<OciVaultSecurityProvider.OciVaultEncryptionConfig>,
                                                 DigestProvider<OciVaultSecurityProvider.OciVaultDigestConfig> {
    private final OciVaultRx ociVault;

    OciVaultSecurityProvider(OciVaultRx ociVault) {
        this.ociVault = ociVault;
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Config config) {
        return secret(OciVaultSecretConfig.create(config));
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(OciVaultSecretConfig providerConfig) {
        return () -> ociVault.getSecretBundle(providerConfig.request())
                .map(ApiOptionalResponse::entity)
                .map(it -> it.flatMap(GetSecretBundle.Response::secretString));
    }

    @Override
    public EncryptionSupport encryption(Config config) {
        return encryption(OciVaultEncryptionConfig.create(config));
    }

    @Override
    public EncryptionSupport encryption(OciVaultEncryptionConfig providerConfig) {
        Function<byte[], Single<String>> encrypt = bytes -> ociVault.encrypt(providerConfig.encryptionRequest()
                                                                                     .data(Base64Value.create(bytes)))
                .map(Encrypt.Response::cipherText);

        Function<String, Single<byte[]>> decrypt = encrypted -> ociVault.decrypt(providerConfig.decryptionRequest()
                                                                                         .cipherText(encrypted))
                .map(Decrypt.Response::decrypted)
                .map(Base64Value::toBytes);

        return EncryptionSupport.create(encrypt, decrypt);
    }

    @Override
    public DigestSupport digest(Config config) {
        return digest(OciVaultDigestConfig.create(config));
    }

    @Override
    public DigestSupport digest(OciVaultDigestConfig providerConfig) {
        DigestFunction digestFunction = (data, preHashed) -> {
            Sign.Request request = providerConfig.signRequest()
                    .message(Base64Value.create(data))
                    .messageType(preHashed ? Sign.Request.MESSAGE_TYPE_DIGEST : Sign.Request.MESSAGE_TYPE_RAW);

            return ociVault.sign(request)
                    .map(Sign.Response::signature)
                    .map(Base64Value::toBase64);
        };

        VerifyFunction verifyFunction = (data, preHashed, digest) -> {
            Verify.Request verifyRequest = providerConfig.verifyRequest()
                    .message(Base64Value.create(data))
                    .messageType(preHashed ? Sign.Request.MESSAGE_TYPE_DIGEST : Sign.Request.MESSAGE_TYPE_RAW)
                    .signature(Base64Value.createFromEncoded(digest));

            return ociVault.verify(verifyRequest)
                    .map(Verify.Response::isValid);
        };

        return DigestSupport.create(digestFunction, verifyFunction);
    }

    /**
     * Configuration for a signature.
     */
    public static class OciVaultDigestConfig implements ProviderConfig {
        private final String keyOcid;
        private final String algorithm;
        private final Optional<String> keyVersionOcid;
        private final Optional<String> cryptographicEndpoint;

        private OciVaultDigestConfig(Builder builder) {
            this.keyOcid = builder.keyOcid;
            this.algorithm = builder.algorithm;
            this.keyVersionOcid = Optional.ofNullable(builder.keyVersionOcid);
            this.cryptographicEndpoint = Optional.ofNullable(builder.cryptographicEndpoint);
        }

        /**
         * Builder to set up configuration required to sign data using OCI KMS.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a new configuration from config.
         *
         * @param config config
         * @return a new instance
         * @see Builder#config(io.helidon.config.Config)
         */
        public static OciVaultDigestConfig create(Config config) {
            return builder().config(config).build();
        }

        Sign.Request signRequest() {
            Sign.Request request = Sign.Request.builder()
                    .keyId(keyOcid)
                    .algorithm(algorithm);

            keyVersionOcid.ifPresent(request::keyVersionId);
            cryptographicEndpoint.ifPresent(request::endpoint);

            return request;
        }

        Verify.Request verifyRequest() {
            Verify.Request request = Verify.Request.builder()
                    .keyId(keyOcid)
                    .algorithm(algorithm);

            keyVersionOcid.ifPresent(request::keyVersionId);
            cryptographicEndpoint.ifPresent(request::endpoint);

            return request;
        }

        /**
         * Fluent API builder for {@link io.helidon.integrations.oci.vault.OciVaultSecurityProvider.OciVaultDigestConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<OciVaultDigestConfig> {
            private static final String CONFIG_KEY_KEY_OCID = "key-ocid";

            private String keyOcid;
            private String algorithm = Sign.Request.ALGORITHM_SHA_256_RSA_PKCS_PSS;
            private String keyVersionOcid;
            private String cryptographicEndpoint;

            private Builder() {
            }

            @Override
            public OciVaultDigestConfig build() {
                Objects.requireNonNull(keyOcid, "Key ID must be defined for digest, configuration key \""
                        + CONFIG_KEY_KEY_OCID + "\"");

                return new OciVaultDigestConfig(this);
            }

            /**
             * Update this builder from configuration.
             * Configuration options:
             * <table class="config">
             * <caption>Secret configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>description</th>
             *     <th>builder method</th>
             * </tr>
             * <tr>
             *     <td>{@code key-ocid}</td>
             *     <td>OCID of the vault key to use for signatures, must be RSA</td>
             *     <td>{@link #keyId(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code key-version-ocid}</td>
             *     <td>OCID of the key version</td>
             *     <td>{@link #keyVersionId(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code algorithm}</td>
             *     <td>Signature algorithm</td>
             *     <td>{@link #algorithm(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code cryptographic-endpoint}</td>
             *     <td>Cryptographic endpoint to use for signatures (available in console)</td>
             *     <td>{@link #cryptographicEndpoint(String)}</td>
             * </tr>
             * </table>
             *
             * @param config config to use
             * @return updated builder
             */
            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_OCID).asString().ifPresent(this::keyId);
                config.get("key-version-ocid").asString().ifPresent(this::keyVersionId);
                config.get("algorithm").asString().ifPresent(this::algorithm);
                config.get("cryptographic-endpoint").asString().ifPresent(this::cryptographicEndpoint);

                return this;
            }

            /**
             * OCID of the key to use for signature.
             * @param keyOcid OCID of the key
             * @return updated builder
             * @see Sign.Request#keyId(String)
             */
            public Builder keyId(String keyOcid) {
                this.keyOcid = keyOcid;
                return this;
            }

            /**
             * Algorithm to sign with.
             *
             * @param algorithm algorithm
             * @return updated builder
             * @see Sign.Request#algorithm(String)
             */
            public Builder algorithm(String algorithm) {
                this.algorithm = algorithm;
                return this;
            }

            /**
             * OCID of the key version.
             *
             * @param keyVersionOcid version OCID
             * @return updated builder
             * @see Sign.Request#keyVersionId(String)
             */
            public Builder keyVersionId(String keyVersionOcid) {
                this.keyVersionOcid = keyVersionOcid;
                return this;
            }

            /**
             * Crypto endpoint to use.
             *
             * @param cryptographicEndpoint endpoint
             * @return udpated builder
             * @see io.helidon.integrations.oci.vault.OciVaultRx.Builder#cryptographicEndpoint(String)
             */
            public Builder cryptographicEndpoint(String cryptographicEndpoint) {
                this.cryptographicEndpoint = cryptographicEndpoint;
                return this;
            }
        }
    }

    /**
     * Configuration for encryption/decryption.
     */
    public static class OciVaultEncryptionConfig implements ProviderConfig {
        private final String keyId;
        private final Optional<String> keyVersionId;
        private final Optional<String> algorithm;
        private final Optional<String> context;
        private final Optional<String> cryptographicEndpoint;

        private OciVaultEncryptionConfig(Builder builder) {
            this.keyId = builder.keyId;
            this.keyVersionId = Optional.ofNullable(builder.keyVersionId);
            this.algorithm = Optional.ofNullable(builder.algorithm);
            this.context = Optional.ofNullable(builder.context);
            this.cryptographicEndpoint = Optional.ofNullable(builder.cryptographicEndpoint);
        }

        /**
         * A new builder for encryption configuration.
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create encryption configuration from config.
         *
         * @param config configuration
         * @return a new encryption config
         * @see Builder#config(io.helidon.config.Config)
         */
        public static OciVaultEncryptionConfig create(Config config) {
            return builder().config(config).build();
        }

        Encrypt.Request encryptionRequest() {
            Encrypt.Request builder = Encrypt.Request.builder()
                    .keyId(keyId);

            keyVersionId.ifPresent(builder::keyVersionId);
            algorithm.ifPresent(builder::algorithm);
            context.ifPresent(builder::context);
            cryptographicEndpoint.ifPresent(builder::endpoint);

            return builder;
        }

        Decrypt.Request decryptionRequest() {
            Decrypt.Request builder = Decrypt.Request.builder()
                    .keyId(keyId);

            keyVersionId.ifPresent(builder::keyVersionId);
            algorithm.ifPresent(builder::algorithm);
            context.ifPresent(builder::context);
            cryptographicEndpoint.ifPresent(builder::endpoint);

            return builder;
        }

        /**
         * Fluent API builder for {@link io.helidon.integrations.oci.vault.OciVaultSecurityProvider.OciVaultEncryptionConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<OciVaultEncryptionConfig> {
            private static final String CONFIG_KEY_KEY_ID = "key-ocid";

            private String keyId;
            private String keyVersionId;
            private String algorithm;
            private String context;
            private String cryptographicEndpoint;

            private Builder() {
            }

            @Override
            public OciVaultEncryptionConfig build() {
                Objects.requireNonNull(keyId, "Key ID must be defined for encryption, configuration key \""
                        + CONFIG_KEY_KEY_ID + "\"");

                return new OciVaultEncryptionConfig(this);
            }

            /**
             * Update this builder from configuration.
             * Configuration options:
             * <table class="config">
             * <caption>Secret configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>description</th>
             *     <th>builder method</th>
             * </tr>
             * <tr>
             *     <td>{@code key-ocid}</td>
             *     <td>OCID of the vault key to use for encryption</td>
             *     <td>{@link #keyId(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code key-version-ocid}</td>
             *     <td>OCID of the key version</td>
             *     <td>{@link #keyVersionId(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code algorithm}</td>
             *     <td>Encryption algorithm</td>
             *     <td>{@link #algorithm(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code cryptographic-endpoint}</td>
             *     <td>Cryptographic endpoint to use for encryption (available in console)</td>
             *     <td>{@link #cryptographicEndpoint(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code context}</td>
             *     <td>Contextual data</td>
             *     <td>{@link #context(String)}</td>
             * </tr>
             * </table>
             *
             * @param config config to use
             * @return updated builder
             */
            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_ID).asString().ifPresent(this::keyId);
                config.get("key-version-ocid").asString().ifPresent(this::keyVersionId);
                config.get("algorithm").asString().ifPresent(this::algorithm);
                config.get("context").asString().ifPresent(this::context);
                config.get("cryptographic-endpoint").asString().ifPresent(this::cryptographicEndpoint);
                return this;
            }

            /**
             * Configure the cryptographic endpoint to use.
             *
             * @param endpoint crypto endpoint
             * @return updated builder
             */
            public Builder cryptographicEndpoint(String endpoint) {
                this.cryptographicEndpoint = endpoint;
                return this;
            }

            /**
             * OCID of the key to use for encryption.
             *
             * @param keyId OCID of the key
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.Encrypt.Request#keyId(String)
             * @see io.helidon.integrations.oci.vault.Decrypt.Request#keyId(String)
             */
            public Builder keyId(String keyId) {
                this.keyId = keyId;
                return this;
            }

            /**
             * OCID of the key version.
             *
             * @param keyVersionId version OCID
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.Encrypt.Request#keyVersionId(String)
             * @see io.helidon.integrations.oci.vault.Decrypt.Request#keyVersionId(String)
             */
            public Builder keyVersionId(String keyVersionId) {
                this.keyVersionId = keyVersionId;
                return this;
            }

            /**
             * Algorithm to use for encryption.
             *
             * @param algorithm algorithm
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.Encrypt.Request#algorithm(String)
             * @see io.helidon.integrations.oci.vault.Decrypt.Request#algorithm(String)
             */
            public Builder algorithm(String algorithm) {
                this.algorithm = algorithm;
                return this;
            }

            /**
             * Contextual data.
             *
             * @param context context
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.Encrypt.Request#context(String)
             * @see io.helidon.integrations.oci.vault.Decrypt.Request#context(String)
             */
            public Builder context(String context) {
                this.context = context;
                return this;
            }
        }
    }

    /**
     * Configuration of an OCI Vault secret.
     */
    public static class OciVaultSecretConfig implements ProviderConfig {
        private final String secretId;
        private final Optional<SecretStage> stage;
        private final Optional<String> versionName;
        private final Optional<Integer> versionNumber;

        private OciVaultSecretConfig(Builder builder) {
            this.secretId = builder.secretId;
            this.stage = Optional.ofNullable(builder.stage);
            this.versionName = Optional.ofNullable(builder.versionName);
            this.versionNumber = Optional.ofNullable(builder.versionNumber);
        }

        /**
         * A new builder.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create secret configuration from config.
         *
         * @param config config
         * @return a new secret configuration
         */
        public static OciVaultSecretConfig create(Config config) {
            return builder().config(config).build();
        }

        GetSecretBundle.Request request() {
            GetSecretBundle.Request request = GetSecretBundle.Request.builder()
                    .secretId(secretId);

            stage.ifPresent(request::stage);
            versionName.ifPresent(request::versionName);
            versionNumber.ifPresent(request::versionNumber);

            return request;
        }

        /**
         * Fluent API builder for
         * {@link io.helidon.integrations.oci.vault.OciVaultSecurityProvider.OciVaultSecretConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<OciVaultSecretConfig> {
            private static final String SECRET_OCID_CONFIG_KEY = "ocid";

            private String secretId;
            private SecretStage stage;
            private String versionName;
            private Integer versionNumber;

            private Builder() {
            }

            @Override
            public OciVaultSecretConfig build() {
                Objects.requireNonNull(secretId, "Secret OCID must be defined. Configuration key \""
                        + SECRET_OCID_CONFIG_KEY + "\"");
                return new OciVaultSecretConfig(this);
            }

            /**
             * Update this builder from configuration.
             * Configuration options:
             * <table class="config">
             * <caption>Secret configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>description</th>
             *     <th>builder method</th>
             * </tr>
             * <tr>
             *     <td>{@code ocid}</td>
             *     <td>OCID of the secret</td>
             *     <td>{@link #secretId(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code stage}</td>
             *     <td>Stage of the secret</td>
             *     <td>{@link #stage(SecretStage)}</td>
             * </tr>
             * <tr>
             *     <td>{@code version-name}</td>
             *     <td>Name of the secret version</td>
             *     <td>{@link #versionName(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code version-number}</td>
             *     <td>Version of the secret</td>
             *     <td>{@link #versionNumber(Integer)}</td>
             * </tr>
             * </table>
             *
             * @param config config to use
             * @return updated builder
             */
            public Builder config(Config config) {
                config.get(SECRET_OCID_CONFIG_KEY).asString().ifPresent(this::secretId);
                config.get("stage").asString().map(SecretStage::valueOf).ifPresent(this::stage);
                config.get("version-name").asString().ifPresent(this::versionName);
                config.get("version-number").asInt().ifPresent(this::versionNumber);

                return this;
            }

            /**
             * Secret OCID.
             *
             * @param secretId secret OCID
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.GetSecretBundle.Request#secretId(String)
             */
            public Builder secretId(String secretId) {
                this.secretId = secretId;
                return this;
            }

            /**
             * Secret stage.
             *
             * @param stage stage
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.GetSecretBundle.Request#stage(SecretStage)
             */
            public Builder stage(SecretStage stage) {
                this.stage = stage;
                return this;
            }

            /**
             * Secret version name.
             *
             * @param versionName version name
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.GetSecretBundle.Request#versionName(String)
             */
            public Builder versionName(String versionName) {
                this.versionName = versionName;
                return this;
            }

            /**
             * Secret version number.
             *
             * @param versionNumber version number
             * @return updated builder
             *
             * @see io.helidon.integrations.oci.vault.GetSecretBundle.Request#versionNumber(int)
             */
            public Builder versionNumber(Integer versionNumber) {
                this.versionNumber = versionNumber;
                return this;
            }
        }
    }
}
