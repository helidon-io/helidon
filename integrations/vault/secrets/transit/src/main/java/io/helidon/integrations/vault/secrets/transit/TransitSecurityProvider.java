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

package io.helidon.integrations.vault.secrets.transit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.vault.Vault;
import io.helidon.security.SecurityException;
import io.helidon.security.spi.DigestProvider;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.ProviderConfig;

public class TransitSecurityProvider implements EncryptionProvider<TransitSecurityProvider.TransitEncryptionConfig>,
                                                DigestProvider<TransitSecurityProvider.TransitDigestConfig> {
    private final TransitSecrets transit;

    TransitSecurityProvider(Vault vault) {
        this.transit = vault.secrets(TransitSecrets.ENGINE);
    }

    @Override
    public EncryptionSupport encryption(Config config) {
        return encryption(TransitEncryptionConfig.create(config));
    }

    @Override
    public EncryptionSupport encryption(TransitEncryptionConfig providerConfig) {
        Function<byte[], Single<String>> encrypt = bytes -> transit.encrypt(providerConfig.encryptionRequest()
                                                                                    .data(Base64Value.create(bytes)))
                .map(Encrypt.Response::encrypted)
                .map(Encrypt.Encrypted::encrypted);

        Function<String, Single<byte[]>> decrypt = encrypted -> transit.decrypt(providerConfig.decryptionRequest()
                                                                                        .data(encrypted))
                .map(Decrypt.Response::decrypted)
                .map(Base64Value::toBytes);

        return EncryptionSupport.create(encrypt, decrypt);
    }

    @Override
    public DigestSupport digest(Config config) {
        return digest(TransitDigestConfig.create(config));
    }

    @Override
    public DigestSupport digest(TransitDigestConfig providerConfig) {
        if (providerConfig.isSignature) {
            return signature(providerConfig);
        } else {
            return hmac(providerConfig);
        }
    }

    private DigestSupport signature(TransitDigestConfig providerConfig) {
        DigestFunction digestFunction = (data, preHashed) -> {
            Sign.Request request = providerConfig.signRequest()
                    .data(Base64Value.create(data))
                    .preHashed(preHashed);

            return transit.sign(request)
                    .map(Sign.Response::signature);
        };

        VerifyFunction verifyFunction = (data, preHashed, digest) -> {
            Verify.Request verifyRequest = providerConfig.verifyRequest()
                    .data(Base64Value.create(data))
                    .preHashed(preHashed)
                    .signature(digest);

            return transit.verify(verifyRequest)
                    .map(Verify.Response::isValid);
        };

        return DigestSupport.create(digestFunction, verifyFunction);
    }

    private DigestSupport hmac(TransitDigestConfig providerConfig) {
        DigestFunction digestFunction = (data, preHashed) -> {
            Hmac.Request request = providerConfig.hmacRequest()
                    .data(Base64Value.create(data));

            return transit.hmac(request)
                    .map(Hmac.Response::hmac);
        };

        VerifyFunction verifyFunction = (data, preHashed, digest) -> {
            Verify.Request verifyRequest = providerConfig.verifyRequest()
                    .data(Base64Value.create(data))
                    .preHashed(preHashed)
                    .hmac(digest);

            return transit.verify(verifyRequest)
                    .map(Verify.Response::isValid);
        };

        return DigestSupport.create(digestFunction, verifyFunction);
    }

    public static class TransitDigestConfig implements ProviderConfig {
        private final String keyName;
        private final Optional<Integer> keyVersion;
        private final Optional<Base64Value> context;
        private final Optional<String> signatureAlgorithm;
        private final Optional<String> marshalingAlgorithm;
        private final Optional<String> hashAlgorithm;

        private final boolean isSignature;

        private TransitDigestConfig(Builder builder) {
            this.keyName = builder.keyName;
            this.keyVersion = Optional.ofNullable(builder.keyVersion);
            this.context = Optional.ofNullable(builder.context);
            this.signatureAlgorithm = Optional.ofNullable(builder.signatureAlgorithm);
            this.marshalingAlgorithm = Optional.ofNullable(builder.marshalingAlgorithm);
            this.hashAlgorithm = Optional.ofNullable(builder.hashAlgorithm);
            this.isSignature = builder.isSignature;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static TransitDigestConfig create(Config config) {
            return builder().config(config).build();
        }

        public boolean isSignature() {
            return isSignature;
        }

        Sign.Request signRequest() {
            Sign.Request request = Sign.Request.builder()
                    .signatureKeyName(keyName);

            keyVersion.ifPresent(request::signatureKeyVersion);
            context.ifPresent(request::context);
            signatureAlgorithm.ifPresent(request::signatureAlgorithm);
            marshalingAlgorithm.ifPresent(request::marshalingAlgorithm);
            hashAlgorithm.ifPresent(request::hashAlgorithm);

            return request;
        }

        Verify.Request verifyRequest() {
            Verify.Request request = Verify.Request.builder()
                    .digestKeyName(keyName);

            context.ifPresent(request::context);
            signatureAlgorithm.ifPresent(request::signatureAlgorithm);
            marshalingAlgorithm.ifPresent(request::marshalingAlgorithm);
            hashAlgorithm.ifPresent(request::hashAlgorithm);

            return request;
        }

        Hmac.Request hmacRequest() {
            Hmac.Request request = Hmac.Request.builder()
                    .hmacKeyName(keyName);

            keyVersion.ifPresent(request::hmacKeyVersion);
            hashAlgorithm.ifPresent(request::hashAlgorithm);

            return request;
        }

        public static class Builder implements io.helidon.common.Builder<TransitDigestConfig> {
            private static final String CONFIG_KEY_KEY_NAME = "key-name";
            public static final String TYPE_SIGNATURE = "signature";
            public static final String TYPE_HMAC = "hmac";

            private boolean isSignature = true;
            private String keyName;
            private Integer keyVersion;
            private Base64Value context;
            private String signatureAlgorithm;
            private String marshalingAlgorithm;
            private String hashAlgorithm;

            private Builder() {
            }

            @Override
            public TransitDigestConfig build() {
                Objects.requireNonNull(keyName, "Key ID must be defined for digest, configuration key \""
                        + CONFIG_KEY_KEY_NAME + "\"");

                return new TransitDigestConfig(this);
            }

            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_NAME).asString().ifPresent(this::keyName);
                config.get("key-version").asInt().ifPresent(this::keyVersion);
                config.get("context").asString().map(Base64Value::create).ifPresent(this::context);
                config.get("signature-algorithm").asString().ifPresent(this::signatureAlgorithm);
                config.get("marshaling-algorithm").asString().ifPresent(this::marshalingAlgorithm);
                config.get("hash-algorithm").asString().ifPresent(this::hashAlgorithm);
                config.get("type").asString().ifPresent(this::type);

                return this;
            }

            public Builder type(String type) {
                switch (type) {
                case TYPE_HMAC:
                    isSignature = false;
                    break;
                case TYPE_SIGNATURE:
                    isSignature = true;
                    break;
                default:
                    throw new SecurityException("Only " + TYPE_SIGNATURE + ", and " + TYPE_HMAC + " digest types are supported");
                }
                return this;
            }

            public Builder keyName(String keyName) {
                this.keyName = keyName;
                return this;
            }

            public Builder keyVersion(Integer keyVersion) {
                this.keyVersion = keyVersion;
                return this;
            }

            public Builder context(Base64Value context) {
                this.context = context;
                return this;
            }

            public Builder signatureAlgorithm(String signatureAlgorithm) {
                this.signatureAlgorithm = signatureAlgorithm;
                return this;
            }

            public Builder marshalingAlgorithm(String marshalingAlgorithm) {
                this.marshalingAlgorithm = marshalingAlgorithm;
                return this;
            }

            public Builder hashAlgorithm(String hashAlgorithm) {
                this.hashAlgorithm = hashAlgorithm;
                return this;
            }
        }
    }

    public static class TransitEncryptionConfig implements ProviderConfig {
        private final String keyName;
        private final Optional<Integer> keyVersion;
        private final Optional<String> encryptionKeyType;
        private final Optional<String> convergentEncryption;
        private final Optional<String> context;

        private TransitEncryptionConfig(Builder builder) {
            this.keyName = builder.keyName;
            this.keyVersion = Optional.ofNullable(builder.keyVersion);
            this.encryptionKeyType = Optional.ofNullable(builder.encryptionKeyType);
            this.convergentEncryption = Optional.ofNullable(builder.convergentEncryption);
            this.context = Optional.ofNullable(builder.context);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static TransitEncryptionConfig create(Config config) {
            return builder().config(config).build();
        }

        public Encrypt.Request encryptionRequest() {
            Encrypt.Request builder = Encrypt.Request.builder()
                    .encryptionKeyName(keyName);

            keyVersion.ifPresent(builder::encryptionKeyVersion);
            encryptionKeyType.ifPresent(builder::encryptionKeyType);
            context.map(Base64Value::createFromEncoded).ifPresent(builder::context);
            convergentEncryption.ifPresent(builder::convergentEncryption);

            return builder;
        }

        public Decrypt.Request decryptionRequest() {
            Decrypt.Request builder = Decrypt.Request.builder()
                    .encryptionKeyName(keyName);

            context.map(Base64Value::createFromEncoded).ifPresent(builder::context);

            return builder;
        }

        public static class Builder implements io.helidon.common.Builder<TransitEncryptionConfig> {
            private static final String CONFIG_KEY_KEY_NAME = "key-name";

            private String keyName;
            private String context;
            private Integer keyVersion;
            private String encryptionKeyType;
            private String convergentEncryption;

            private Builder() {
            }

            @Override
            public TransitEncryptionConfig build() {
                Objects.requireNonNull(keyName, "Key ID must be defined for encryption, configuration key \""
                        + CONFIG_KEY_KEY_NAME + "\"");

                return new TransitEncryptionConfig(this);
            }

            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_NAME).asString().ifPresent(this::keyName);
                config.get("context").asString().ifPresent(this::context);
                config.get("key-version").asInt().ifPresent(this::keyVersion);
                config.get("key-type").asString().ifPresent(this::keyType);
                config.get("convergent").asString().ifPresent(this::convergent);
                return this;
            }

            public Builder keyName(String keyName) {
                this.keyName = keyName;
                return this;
            }

            public Builder context(String context) {
                this.context = context;
                return this;
            }

            public Builder keyVersion(Integer keyVersion) {
                this.keyVersion = keyVersion;
                return this;
            }

            public Builder keyType(String encryptionKeyType) {
                this.encryptionKeyType = encryptionKeyType;
                return this;
            }

            public Builder convergent(String convergentEncryption) {
                this.convergentEncryption = convergentEncryption;
                return this;
            }
        }
    }
}
