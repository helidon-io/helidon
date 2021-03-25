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

public class OciVaultSecurityProvider implements SecretsProvider<OciVaultSecurityProvider.OciVaultSecretConfig>,
                                                 EncryptionProvider<OciVaultSecurityProvider.OciVaultEncryptionConfig>,
                                                 DigestProvider<OciVaultSecurityProvider.OciVaultDigestConfig> {
    private final OciVault ociVault;

    OciVaultSecurityProvider(OciVault ociVault) {
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

    public static class OciVaultDigestConfig implements ProviderConfig {
        private final String keyOcid;
        private final String algorithm;
        private final Optional<String> keyVersionOcid;

        private OciVaultDigestConfig(Builder builder) {
            this.keyOcid = builder.keyOcid;
            this.algorithm = builder.algorithm;
            this.keyVersionOcid = Optional.ofNullable(builder.keyVersionOcid);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static OciVaultDigestConfig create(Config config) {
            return builder().config(config).build();
        }

        Sign.Request signRequest() {
            Sign.Request request = Sign.Request.builder()
                    .keyId(keyOcid)
                    .algorithm(algorithm);

            keyVersionOcid.ifPresent(request::keyVersionId);

            return request;
        }

        Verify.Request verifyRequest() {
            Verify.Request request = Verify.Request.builder()
                    .keyId(keyOcid)
                    .algorithm(algorithm);

            keyVersionOcid.ifPresent(request::keyVersionId);

            return request;
        }

        public static class Builder implements io.helidon.common.Builder<OciVaultDigestConfig> {
            private static final String CONFIG_KEY_KEY_OCID = "key-ocid";

            private String keyOcid;
            private String algorithm = Sign.Request.ALGORITHM_SHA_256_RSA_PKCS_PSS;
            private String keyVersionOcid;

            private Builder() {
            }

            @Override
            public OciVaultDigestConfig build() {
                Objects.requireNonNull(keyOcid, "Key ID must be defined for digest, configuration key \""
                        + CONFIG_KEY_KEY_OCID + "\"");

                return new OciVaultDigestConfig(this);
            }

            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_OCID).asString().ifPresent(this::keyOcid);
                config.get("key-version-ocid").asString().ifPresent(this::keyVersionOcid);
                config.get("algorithm").asString().ifPresent(this::algorithm);

                return this;
            }

            public Builder keyOcid(String keyOcid) {
                this.keyOcid = keyOcid;
                return this;
            }

            public Builder algorithm(String algorithm) {
                this.algorithm = algorithm;
                return this;
            }

            public Builder keyVersionOcid(String keyVersionOcid) {
                this.keyVersionOcid = keyVersionOcid;
                return this;
            }
        }
    }

    public static class OciVaultEncryptionConfig implements ProviderConfig {
        private final String keyId;
        private final Optional<String> keyVersionId;
        private final Optional<String> algorithm;
        private final Optional<String> context;

        private OciVaultEncryptionConfig(Builder builder) {
            this.keyId = builder.keyId;
            this.keyVersionId = Optional.ofNullable(builder.keyVersionId);
            this.algorithm = Optional.ofNullable(builder.algorithm);
            this.context = Optional.ofNullable(builder.context);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static OciVaultEncryptionConfig create(Config config) {
            return builder().config(config).build();
        }

        public Encrypt.Request encryptionRequest() {
            Encrypt.Request builder = Encrypt.Request.builder()
                    .keyId(keyId);

            keyVersionId.ifPresent(builder::keyVersionId);
            algorithm.ifPresent(builder::algorithm);
            context.ifPresent(builder::context);

            return builder;
        }

        public Decrypt.Request decryptionRequest() {
            Decrypt.Request builder = Decrypt.Request.builder()
                    .keyId(keyId);

            keyVersionId.ifPresent(builder::keyVersionId);
            algorithm.ifPresent(builder::algorithm);
            context.ifPresent(builder::context);

            return builder;
        }

        public static class Builder implements io.helidon.common.Builder<OciVaultEncryptionConfig> {
            private static final String CONFIG_KEY_KEY_ID = "key-ocid";

            public String keyId;
            public String keyVersionId;
            public String algorithm;
            public String context;

            private Builder() {
            }

            @Override
            public OciVaultEncryptionConfig build() {
                Objects.requireNonNull(keyId, "Key ID must be defined for encryption, configuration key \""
                        + CONFIG_KEY_KEY_ID + "\"");

                return new OciVaultEncryptionConfig(this);
            }

            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_ID).asString().ifPresent(this::keyId);
                config.get("key-version-ocid").asString().ifPresent(this::keyVersionId);
                config.get("algorithm").asString().ifPresent(this::algorithm);
                config.get("context").asString().ifPresent(this::context);
                return this;
            }

            public Builder keyId(String keyId) {
                this.keyId = keyId;
                return this;
            }

            public Builder keyVersionId(String keyVersionId) {
                this.keyVersionId = keyVersionId;
                return this;
            }

            public Builder algorithm(String algorithm) {
                this.algorithm = algorithm;
                return this;
            }

            public Builder context(String context) {
                this.context = context;
                return this;
            }
        }
    }

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

        public static Builder builder() {
            return new Builder();
        }

        public static OciVaultSecretConfig create(Config config) {
            return builder().config(config).build();
        }

        public GetSecretBundle.Request request() {
            GetSecretBundle.Request request = GetSecretBundle.Request.builder()
                    .secretId(secretId);

            stage.ifPresent(request::stage);
            versionName.ifPresent(request::versionName);
            versionNumber.ifPresent(request::versionNumber);

            return request;
        }

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

            public Builder config(Config config) {
                config.get(SECRET_OCID_CONFIG_KEY).asString().ifPresent(this::secretId);
                config.get("stage").asString().map(SecretStage::valueOf).ifPresent(this::stage);
                config.get("version-name").asString().ifPresent(this::versionName);
                config.get("version-number").asInt().ifPresent(this::versionNumber);
                ;

                return this;
            }

            public Builder secretId(String secretId) {
                this.secretId = secretId;
                return this;
            }

            public Builder stage(SecretStage stage) {
                this.stage = stage;
                return this;
            }

            public Builder versionName(String versionName) {
                this.versionName = versionName;
                return this;
            }

            public Builder versionNumber(Integer versionNumber) {
                this.versionNumber = versionNumber;
                return this;
            }
        }
    }
}
