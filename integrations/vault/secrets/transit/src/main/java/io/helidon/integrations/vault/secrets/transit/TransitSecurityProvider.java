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

import io.helidon.common.Base64Value;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.security.SecurityException;
import io.helidon.security.spi.DigestProvider;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.ProviderConfig;

/**
 * Integration with Helidon Security.
 */
public class TransitSecurityProvider implements EncryptionProvider<TransitSecurityProvider.TransitEncryptionConfig>,
                                                DigestProvider<TransitSecurityProvider.TransitDigestConfig> {
    private final TransitSecretsRx transit;

    TransitSecurityProvider(Vault vault) {
        this.transit = vault.secrets(TransitSecretsRx.ENGINE);
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
                .map(Encrypt.Encrypted::cipherText);

        Function<String, Single<byte[]>> decrypt = encrypted -> transit.decrypt(providerConfig.decryptionRequest()
                                                                                        .cipherText(encrypted))
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

    /**
     * Configuration of a digest when using programmatic setup of security digests.
     */
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

        /**
         * A new builder for {@link io.helidon.integrations.vault.secrets.transit.TransitSecurityProvider.TransitDigestConfig}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a new digest configuration from config.
         *
         * @param config config to use
         * @return a new digest configuration
         */
        public static TransitDigestConfig create(Config config) {
            return builder().config(config).build();
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

        /**
         * Fluent API builder for
         * {@link io.helidon.integrations.vault.secrets.transit.TransitSecurityProvider.TransitDigestConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<TransitDigestConfig> {
            /**
             * Digest is a signature.
             */
            public static final String TYPE_SIGNATURE = "signature";
            /**
             * Digest is an HMAC.
             */
            public static final String TYPE_HMAC = "hmac";
            private static final String CONFIG_KEY_KEY_NAME = "key-name";
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

            /**
             * Update this builder from configuration.
             * Only {@value CONFIG_KEY_KEY_NAME} is mandatory.
             *
             * Configuration options:
             * <table class="config">
             * <caption>Secret configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>description</th>
             *     <th>builder method</th>
             * </tr>
             * <tr>
             *     <td>{@value CONFIG_KEY_KEY_NAME}</td>
             *     <td>Name of the key used for this digest operation</td>
             *     <td>{@link #keyName(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code key-version}</td>
             *     <td>Version of the key to use</td>
             *     <td>{@link #keyVersion(int)}</td>
             * </tr>
             * <tr>
             *     <td>{@code context}</td>
             *     <td>Context as base64 encoded text.</td>
             *     <td>{@link #context(Base64Value)}</td>
             * </tr>
             * <tr>
             *     <td>{@code signature-algorithm}</td>
             *     <td>Signature algorithm.</td>
             *     <td>{@link #signatureAlgorithm(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code marshalling-algorithm}</td>
             *     <td>Marshalling algorithm.</td>
             *     <td>{@link #marshalingAlgorithm(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code hash-algorithm}</td>
             *     <td>Hash algorithm.</td>
             *     <td>{@link #hashAlgorithm(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code type}</td>
             *     <td>Type of digest, defaults to {@value #TYPE_SIGNATURE}.</td>
             *     <td>{@link #type(String)}</td>
             * </tr>
             * </table>
             *
             * @param config config to use
             * @return updated builder
             */
            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_NAME).asString().ifPresent(this::keyName);
                config.get("key-version").asInt().ifPresent(this::keyVersion);
                config.get("context").asString().map(Base64Value::createFromEncoded).ifPresent(this::context);
                config.get("signature-algorithm").asString().ifPresent(this::signatureAlgorithm);
                config.get("marshaling-algorithm").asString().ifPresent(this::marshalingAlgorithm);
                config.get("hash-algorithm").asString().ifPresent(this::hashAlgorithm);
                config.get("type").asString().ifPresent(this::type);

                return this;
            }

            /**
             * Type of digest, either {@link #TYPE_SIGNATURE} or {@link #TYPE_HMAC}.
             * Defaults to {@link #TYPE_SIGNATURE}.
             *
             * @param type type to use
             * @return updated builder
             */
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

            /**
             * Name of the key (Vault server side) used for this digest.
             * Note that key type must be valid for the type used. Signatures require an asymmetric key, HMAC requires
             * a symmetric key.
             *
             * @param keyName name of the key
             * @return updated builder
             */
            public Builder keyName(String keyName) {
                this.keyName = keyName;
                return this;
            }

            /**
             * Specifies the version of the key to use for digest. If not set, uses the latest version.
             * Must be greater than or equal to the key's {@code min_encryption_version}, if set.
             * Optional.
             *
             * @param keyVersion key version
             * @return updated request
             */
            public Builder keyVersion(int keyVersion) {
                this.keyVersion = keyVersion;
                return this;
            }

            /**
             * Specifies the context for key derivation. This is required if key derivation is enabled for this key; currently
             * only available with ed25519 keys.
             *
             * @param context context
             * @return updated request
             */
            public Builder context(Base64Value context) {
                this.context = context;
                return this;
            }

            /**
             * When using a RSA key, specifies the RSA signature algorithm to use for signing. Supported signature types are:
             *
             * pss
             * pkcs1v15
             *
             * See signature algorithm constants on this class.
             *
             * @param signatureAlgorithm signature algorithm to use
             * @return updated request
             * @see Sign.Request#SIGNATURE_ALGORITHM_PSS
             * @see Sign.Request#SIGNATURE_ALGORITHM_PKCS1_V15
             */
            public Builder signatureAlgorithm(String signatureAlgorithm) {
                this.signatureAlgorithm = signatureAlgorithm;
                return this;
            }

            /**
             * Specifies the way in which the signature should be marshaled. This currently only applies to ECDSA keys. Supported
             * types are:
             * asn1: The default, used by OpenSSL and X.509
             * jws: The version used by JWS (and thus for JWTs). Selecting this will also change the output encoding to URL-safe
             * Base64 encoding instead of standard Base64-encoding.
             *
             * @param marshalingAlgorithm marshaling algorithm
             * @return updated request
             * @see Sign.Request#MARSHALLING_ALGORITHM_ASN_1
             * @see Sign.Request#MARSHALLING_ALGORITHM_JWS
             */
            public Builder marshalingAlgorithm(String marshalingAlgorithm) {
                this.marshalingAlgorithm = marshalingAlgorithm;
                return this;
            }

            /**
             * Specifies the hash algorithm to use for supporting key types (notably, not including ed25519 which specifies its
             * own
             * hash algorithm).
             * See hash algorithm constants on this class.
             *
             * @param hashAlgorithm algorithm to use
             * @return updated request
             * @see Sign.Request#HASH_ALGORITHM_SHA2_224
             * @see Sign.Request#HASH_ALGORITHM_SHA2_256
             * @see Sign.Request#HASH_ALGORITHM_SHA2_384
             * @see Sign.Request#HASH_ALGORITHM_SHA2_512
             * @see Hmac.Request#HASH_ALGORITHM_SHA2_224
             * @see Hmac.Request#HASH_ALGORITHM_SHA2_256
             * @see Hmac.Request#HASH_ALGORITHM_SHA2_384
             * @see Hmac.Request#HASH_ALGORITHM_SHA2_512
             */
            public Builder hashAlgorithm(String hashAlgorithm) {
                this.hashAlgorithm = hashAlgorithm;
                return this;
            }
        }
    }

    /**
     * Configuration of encryption when using programmatic setup of security.
     */
    public static class TransitEncryptionConfig implements ProviderConfig {
        private final String keyName;
        private final Optional<Integer> keyVersion;
        private final Optional<String> encryptionKeyType;
        private final Optional<String> convergentEncryption;
        private final Optional<Base64Value> context;

        private TransitEncryptionConfig(Builder builder) {
            this.keyName = builder.keyName;
            this.keyVersion = Optional.ofNullable(builder.keyVersion);
            this.encryptionKeyType = Optional.ofNullable(builder.encryptionKeyType);
            this.convergentEncryption = Optional.ofNullable(builder.convergentEncryption);
            this.context = Optional.ofNullable(builder.context);
        }

        /**
         * A new builder for
         * {@link io.helidon.integrations.vault.secrets.transit.TransitSecurityProvider.TransitEncryptionConfig}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a new encryption configuration from config.
         *
         * @param config config to use
         * @return a new encryption configuration
         */
        public static TransitEncryptionConfig create(Config config) {
            return builder().config(config).build();
        }

        Encrypt.Request encryptionRequest() {
            Encrypt.Request builder = Encrypt.Request.builder()
                    .encryptionKeyName(keyName);

            keyVersion.ifPresent(builder::encryptionKeyVersion);
            encryptionKeyType.ifPresent(builder::encryptionKeyType);
            context.ifPresent(builder::context);
            convergentEncryption.ifPresent(builder::convergentEncryption);

            return builder;
        }

        Decrypt.Request decryptionRequest() {
            Decrypt.Request builder = Decrypt.Request.builder()
                    .encryptionKeyName(keyName);

            context.ifPresent(builder::context);

            return builder;
        }

        /**
         * Fluent API builder for
         * {@link io.helidon.integrations.vault.secrets.transit.TransitSecurityProvider.TransitEncryptionConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<TransitEncryptionConfig> {
            private static final String CONFIG_KEY_KEY_NAME = "key-name";

            private String keyName;
            private Base64Value context;
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

            /**
             * Update this builder from configuration.
             * Only {@value CONFIG_KEY_KEY_NAME} is mandatory.
             *
             * Configuration options:
             * <table class="config">
             * <caption>Secret configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>description</th>
             *     <th>builder method</th>
             * </tr>
             * <tr>
             *     <td>{@value CONFIG_KEY_KEY_NAME}</td>
             *     <td>Name of the key used for this digest operation</td>
             *     <td>{@link #keyName(String)}</td>
             * </tr>
             * <tr>
             *     <td>{@code context}</td>
             *     <td>Context as base64 encoded text.</td>
             *     <td>{@link #context(Base64Value)}</td>
             * </tr>
             * <tr>
             *     <td>{@code key-version}</td>
             *     <td>Version of the key to use</td>
             *     <td>{@link #keyVersion(int)}</td>
             * </tr>
             * <tr>
             *     <td>{@code key-type}</td>
             *     <td>Type of the key to use</td>
             *     <td>{@link #keyVersion(int)}</td>
             * </tr>
             * <tr>
             *     <td>{@code convergent}</td>
             *     <td>Convergent encryption</td>
             *     <td>{@link #convergent(String)}</td>
             * </tr>
             * </table>
             *
             * @param config config to use
             * @return updated builder
             */
            public Builder config(Config config) {
                config.get(CONFIG_KEY_KEY_NAME).asString().ifPresent(this::keyName);
                config.get("context").asString().map(Base64Value::createFromEncoded).ifPresent(this::context);
                config.get("key-version").asInt().ifPresent(this::keyVersion);
                config.get("key-type").asString().ifPresent(this::keyType);
                config.get("convergent").asString().ifPresent(this::convergent);
                return this;
            }

            /**
             * Specifies the name of the encryption key to encrypt/decrypt against.
             * Required.
             *
             * @param keyName name of the key
             * @return updated request
             */
            public Builder keyName(String keyName) {
                this.keyName = keyName;
                return this;
            }

            /**
             * Specifies the context for key derivation. This is required if key derivation is enabled for this key.
             *
             * @param context context
             * @return updated request
             */
            public Builder context(Base64Value context) {
                this.context = context;
                return this;
            }

            /**
             * Version of the key used to encrypt the data.
             *
             * @param keyVersion version of the key
             * @return updated builder
             */
            public Builder keyVersion(int keyVersion) {
                this.keyVersion = keyVersion;
                return this;
            }

            /**
             * This parameter is required when encryption key is expected to be created. When performing an upsert operation,
             * the type of key to create.
             * <p>
             * Defaults to {@code aes256-gcm96}.
             *
             * @param encryptionKeyType type of the encryption key
             * @return updated request
             */
            public Builder keyType(String encryptionKeyType) {
                this.encryptionKeyType = encryptionKeyType;
                return this;
            }

            /**
             * This parameter will only be used when a key is expected to be created. Whether to support convergent encryption.
             * This is only supported when using a key with key derivation enabled and will require all requests to carry both a
             * context and 96-bit (12-byte) nonce. The given nonce will be used in place of a randomly generated nonce. As a
             * result, when the same context and nonce are supplied, the same ciphertext is generated. It is very important when
             * using this mode that you ensure that all nonces are unique for a given context. Failing to do so will severely
             * impact the ciphertext's security.
             *
             * @param convergentEncryption convergent encryption
             * @return updated request
             */
            public Builder convergent(String convergentEncryption) {
                this.convergentEncryption = convergentEncryption;
                return this;
            }
        }
    }
}
