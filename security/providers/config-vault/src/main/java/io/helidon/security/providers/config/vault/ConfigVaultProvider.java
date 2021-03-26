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

package io.helidon.security.providers.config.vault;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.encryption.ConfigEncryptionException;
import io.helidon.config.encryption.ConfigProperties;
import io.helidon.config.encryption.EncryptionUtil;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.ProviderConfig;
import io.helidon.security.spi.SecretsProvider;

/**
 * Security provider to retrieve secrets directly from configuration and to encrypt/decrypt data
 * using config's security setup.
 */
public class ConfigVaultProvider implements SecretsProvider<ConfigVaultProvider.SecretConfig>,
                                            EncryptionProvider<ConfigVaultProvider.EncryptionConfig> {

    private static final String CIPHER_TEXT_PREFIX_V2 = "helidon:2:";

    private final Optional<EncryptionSupport> aesEncryption;

    private ConfigVaultProvider(Builder builder) {
        this.aesEncryption = builder.aesEncryption();
    }

    /**
     * Create a new builder to configure this provider.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates the provider with default configuration, supporting encryption if it is configured
     * using environment variables or system properties.
     *
     * @return new security provider
     */
    public static ConfigVaultProvider create() {
        return builder().build();
    }

    /**
     * Creates the provider from configuration, supporting encryption if its configuration is found.
     *
     * @param config configuration of this provider
     * @return new security provider
     */
    public static ConfigVaultProvider create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Config config) {
        Supplier<Optional<String>> supplier = config.get("value").asString().optionalSupplier();
        return () -> Single.just(supplier.get());
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(SecretConfig providerConfig) {
        return providerConfig.value();
    }

    @Override
    public EncryptionSupport encryption(Config config) {
        return encryption(EncryptionConfig.create(config));
    }

    @Override
    public EncryptionSupport encryption(EncryptionConfig providerConfig) {
        return providerConfig.aesEncryption()
                .or(() -> aesEncryption)
                .orElseThrow(() -> new SecurityException("Encryption is not configured"));
    }

    /**
     * Configuration of encryption. Currently has no additional configuration options.
     */
    public static class EncryptionConfig implements ProviderConfig {
        private final Optional<char[]> password;

        private EncryptionConfig(Optional<char[]> password) {
            this.password = password;
        }

        /**
         * Create a new instance.
         *
         * @return new instance with default configuration
         */
        public static EncryptionConfig create() {
            return new EncryptionConfig(Optional.empty());
        }

        /**
         * Create a new instance with custom password.
         * @param password password to use
         * @return a new instance using the custom password
         */
        public static EncryptionConfig create(char[] password) {
            return new EncryptionConfig(Optional.ofNullable(password));
        }

        /**
         * Create a new instance from config.
         *
         * @param config config to read password from (if any)
         * @return a new instance configured from config
         */
        public static EncryptionConfig create(Config config) {
            return new EncryptionConfig(config.get("password").asString().map(String::toCharArray));
        }

        private static EncryptionSupport encryptionSupport(char[] password) {
            Function<byte[], Single<String>> encrypt = bytes -> {
                return Single.just(CIPHER_TEXT_PREFIX_V2 + EncryptionUtil.encryptAesBytes(password, bytes));
            };
            Function<String, Single<byte[]>> decrypt = cipherText -> {
                if (cipherText.startsWith(CIPHER_TEXT_PREFIX_V2)) {
                    String base64 = cipherText.substring(CIPHER_TEXT_PREFIX_V2.length());
                    return Single.just(EncryptionUtil.decryptAesBytes(password, base64));
                } else {
                    return Single.error(new ConfigEncryptionException("Invalid cipher text"));
                }
            };
            return EncryptionSupport.create(encrypt, decrypt);
        }

        Optional<EncryptionSupport> aesEncryption() {
            return password.map(EncryptionConfig::encryptionSupport);
        }
    }

    /**
     * Configuration of a secret.
     */
    public static class SecretConfig implements ProviderConfig {
        private final Supplier<Single<Optional<String>>> value;

        private SecretConfig(Supplier<Single<Optional<String>>> value) {
            this.value = value;
        }

        /**
         * Create a new secret configuration with a supplier of a future ({@link io.helidon.common.reactive.Single}),
         * such as when retrieving the secret from a remote service.
         * The supplier must be thread safe.
         *
         * @param valueSupplier supplier of a value
         * @return a new secret configuration
         */
        public static SecretConfig createSingleSupplier(Supplier<Single<Optional<String>>> valueSupplier) {
            return new SecretConfig(valueSupplier);
        }

        /**
         * Create a new secret configuration with a supplier of an {@link Optional}, such as when retrieving
         * the secret from some local information that may change.
         * The supplier must be thread safe.
         *
         * @param valueSupplier supplier of an optional value
         * @return a new secret configuration
         */
        public static SecretConfig createOptionalSupplier(Supplier<Optional<String>> valueSupplier) {
            return new SecretConfig(() -> Single.just(valueSupplier.get()));
        }

        /**
         * Create a new secret from a supplier, such as when computing the secret value.
         * The supplier must be thread safe.
         *
         * @param valueSupplier supplier of a value
         * @return a new secret configuration
         */
        public static SecretConfig create(Supplier<String> valueSupplier) {
            return new SecretConfig(() -> Single.just(Optional.of(valueSupplier.get())));
        }

        /**
         * Create a new secret from a value.
         *
         * @param value the secret value
         * @return a new secret configuration
         */
        public static SecretConfig create(String value) {
            return new SecretConfig(() -> Single.just(Optional.of(value)));
        }

        Supplier<Single<Optional<String>>> value() {
            return value;
        }
    }

    /**
     * Fluent API builder for {@link ConfigVaultProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<ConfigVaultProvider> {
        private Config config = Config.empty();
        private Optional<char[]> masterPassword = Optional.empty();

        private Builder() {
        }

        private static Optional<char[]> resolveMasterPassword() {
            Map<String, String> env = System.getenv();
            if (env.containsKey(ConfigProperties.MASTER_PASSWORD_ENV_VARIABLE)) {
                return Optional.of(env.get(ConfigProperties.MASTER_PASSWORD_ENV_VARIABLE).toCharArray());
            }
            Properties properties = System.getProperties();
            if (properties.containsKey(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY)) {
                return Optional.of(properties.getProperty(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY).toCharArray());
            }
            return Optional.empty();
        }

        @Override
        public ConfigVaultProvider build() {
            this.masterPassword = masterPassword
                    .or(() -> config.get("master-password").asString().map(String::toCharArray))
                    .or(Builder::resolveMasterPassword);

            return new ConfigVaultProvider(this);
        }

        /**
         * Update this builder from provided configuration.
         *
         * @param config configuration to use
         * @return updated builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Configure master password used for encryption/decryption.
         * If master password cannot be obtained from any source (this method, configuration, system property,
         * environment variable), encryption and decryption will not be supported.
         *
         * @param masterPassword password to use
         * @return updated builder
         */
        public Builder masterPassword(char[] masterPassword) {
            this.masterPassword = Optional.of(masterPassword);
            return this;
        }

        Optional<EncryptionSupport> aesEncryption() {
            return masterPassword.map(EncryptionConfig::encryptionSupport);
        }
    }
}
