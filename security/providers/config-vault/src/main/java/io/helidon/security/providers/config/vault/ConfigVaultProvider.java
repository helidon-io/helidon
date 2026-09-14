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

package io.helidon.security.providers.config.vault;

import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.config.Config;
import io.helidon.config.encryption.ConfigProperties;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.security.SecretsProviderConfig;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.ProviderConfig;
import io.helidon.security.spi.SecretsProvider;
import io.helidon.security.spi.SecurityProvider;

/**
 * Security provider to retrieve secrets directly from configuration and to encrypt/decrypt data
 * using config's security setup.
 */
public class ConfigVaultProvider implements SecretsProvider<ConfigVaultProvider.SecretConfig>,
                                            EncryptionProvider<ConfigVaultProvider.EncryptionConfig> {
    private static final System.Logger LOGGER = System.getLogger(ConfigVaultProvider.class.getName());
    private static final int LEGACY_NUMBER_OF_ITERATIONS = 10_000;
    private static final byte CURRENT_VERSION = 1;
    private static final byte[] CURRENT_VERSION_HEADER = {CURRENT_VERSION};
    private static final AtomicBoolean LEGACY_DECRYPTION_LOGGED = new AtomicBoolean();

    private final Optional<SymmetricCipher> symmetricCipher;
    private final Optional<SymmetricCipher> legacySymmetricCipher;
    private final boolean legacyEncryption;
    private final boolean legacyFallback;

    private ConfigVaultProvider(Builder builder) {
        this.symmetricCipher = builder.symmetricCipher;
        this.legacySymmetricCipher = builder.legacySymmetricCipher;
        this.legacyEncryption = builder.resolvedLegacyEncryption;
        this.legacyFallback = builder.resolvedLegacyFallback;
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
    public Supplier<Optional<String>> secret(Config config) {
        return config.get("value").asString().optionalSupplier();
    }

    @Override
    public Supplier<Optional<String>> secret(SecretConfig providerConfig) {
        return providerConfig.value();
    }

    @Override
    public EncryptionSupport encryption(Config config) {
        return encryption(EncryptionConfig.create(config));
    }

    @Override
    public EncryptionSupport encryption(EncryptionConfig providerConfig) {
        SymmetricCipher symmetricCipher = providerConfig.symmetricCipher()
                .or(() -> this.symmetricCipher)
                .orElseThrow(() -> new SecurityException("Encryption is not configured"));
        SymmetricCipher legacySymmetricCipher = providerConfig.legacySymmetricCipher()
                .or(() -> this.legacySymmetricCipher)
                .orElseThrow(() -> new SecurityException("Encryption is not configured"));
        boolean legacyEncryption = providerConfig.legacyEncryption().orElse(this.legacyEncryption);
        boolean legacyFallback = providerConfig.legacyFallback().orElse(this.legacyFallback);

        return EncryptionConfig.encryptionSupport(symmetricCipher,
                                                  legacySymmetricCipher,
                                                  legacyEncryption,
                                                  legacyFallback);
    }

    /**
     * Configuration of encryption.
     * Legacy flags configured on a named encryption entry override the provider-level defaults while still inheriting
     * the provider master password unless a password is configured on this entry.
     */
    public static class EncryptionConfig implements ProviderConfig {
        private final Optional<SymmetricCipher> symmetricCipher;
        private final Optional<SymmetricCipher> legacySymmetricCipher;
        private final Optional<Boolean> legacyEncryption;
        private final Optional<Boolean> legacyFallback;

        private EncryptionConfig(Optional<SymmetricCipher> symmetricCipher,
                                 Optional<SymmetricCipher> legacySymmetricCipher,
                                 Optional<Boolean> legacyEncryption,
                                 Optional<Boolean> legacyFallback) {
            this.symmetricCipher = symmetricCipher;
            this.legacySymmetricCipher = legacySymmetricCipher;
            this.legacyEncryption = legacyEncryption;
            this.legacyFallback = legacyFallback;
        }

        /**
         * Create a new instance.
         *
         * @return new instance with default configuration
         */
        public static EncryptionConfig create() {
            return new EncryptionConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        /**
         * Create a new instance with custom password.
         * @param password password to use
         * @return a new instance using the custom password
         */
        public static EncryptionConfig create(char[] password) {
            return create(password, Optional.empty(), Optional.empty());
        }

        /**
         * Create a new instance with custom password and legacy encryption flags.
         * These flags are temporary compatibility controls for rolling upgrades, not steady-state security settings.
         *
         * @param password password to use
         * @param legacyEncryption whether data should be written using the legacy encrypted value format
         * @param legacyFallback whether decryption should retry with the alternate encrypted value format
         * @return a new instance using the custom password and legacy flags
         */
        public static EncryptionConfig create(char[] password, boolean legacyEncryption, boolean legacyFallback) {
            return create(password, Optional.of(legacyEncryption), Optional.of(legacyFallback));
        }

        /**
         * Create a new instance from config.
         *
         * @param config config to read password and legacy flags from (if any)
         * @return a new instance configured from config
         * @deprecated use {@link #create(io.helidon.config.Config)} instead
         */
        @SuppressWarnings("removal")
        @Deprecated(since = "4.4.0", forRemoval = true)
        public static EncryptionConfig create(io.helidon.common.config.Config config) {
            return create(Config.config(config));
        }

        /**
         * Create a new instance from config.
         *
         * @param config config to read password and legacy flags from (if any)
         * @return a new instance configured from config
         */
        public static EncryptionConfig create(Config config) {
            Optional<char[]> password = config.get("password").asString().map(String::toCharArray);
            return new EncryptionConfig(password.map(EncryptionConfig::symmetricCipher),
                                        password.map(EncryptionConfig::legacySymmetricCipher),
                                        config.get("legacy-encryption").asBoolean().asOptional(),
                                        config.get("legacy-fallback").asBoolean().asOptional());
        }

        private static EncryptionConfig create(char[] password,
                                               Optional<Boolean> legacyEncryption,
                                               Optional<Boolean> legacyFallback) {
            return new EncryptionConfig(Optional.ofNullable(password).map(EncryptionConfig::symmetricCipher),
                                        Optional.ofNullable(password).map(EncryptionConfig::legacySymmetricCipher),
                                        legacyEncryption,
                                        legacyFallback);
        }

        private static EncryptionSupport encryptionSupport(SymmetricCipher symmetricCipher,
                                                           SymmetricCipher legacySymmetricCipher,
                                                           boolean legacyEncryption,
                                                           boolean legacyFallback) {
            SymmetricCipher primaryCipher = legacyEncryption ? legacySymmetricCipher : symmetricCipher;
            SymmetricCipher fallbackCipher = legacyEncryption ? symmetricCipher : legacySymmetricCipher;
            Function<byte[], String> encrypt = bytes -> encrypt(primaryCipher, !legacyEncryption, bytes);
            Function<String, byte[]> decrypt = cipherText -> decrypt(primaryCipher,
                                                                     fallbackCipher,
                                                                     legacyFallback,
                                                                     !legacyEncryption,
                                                                     legacyEncryption,
                                                                     cipherText).toBytes();
            return EncryptionSupport.create(encrypt, decrypt);
        }

        private static SymmetricCipher symmetricCipher(char[] password) {
            return SymmetricCipher.builder()
                    .password(password)
                    .additionalAuthenticatedData(CURRENT_VERSION_HEADER)
                    .build();
        }

        private static SymmetricCipher legacySymmetricCipher(char[] password) {
            return SymmetricCipher.builder()
                    .password(password)
                    .numberOfIterations(LEGACY_NUMBER_OF_ITERATIONS)
                    .build();
        }

        private static String encrypt(SymmetricCipher cipher, boolean current, byte[] bytes) {
            if (!current) {
                return cipher.encryptToString(Base64Value.create(bytes));
            }

            byte[] encrypted = cipher.encrypt(Base64Value.create(bytes)).toBytes();
            byte[] versioned = new byte[encrypted.length + 1];
            versioned[0] = CURRENT_VERSION;
            System.arraycopy(encrypted, 0, versioned, 1, encrypted.length);
            return Base64Value.create(versioned).toBase64();
        }

        private static Base64Value decrypt(SymmetricCipher primaryCipher,
                                           SymmetricCipher fallbackCipher,
                                           boolean legacyFallback,
                                           boolean primaryIsCurrent,
                                           boolean fallbackIsCurrent,
                                           String cipherText) {
            CryptoException currentFailure;
            try {
                return decrypt(primaryCipher, primaryIsCurrent, cipherText);
            } catch (CryptoException e) {
                currentFailure = e;
            }
            if (!legacyFallback) {
                throw currentFailure;
            }
            try {
                Base64Value decrypted = decrypt(fallbackCipher, fallbackIsCurrent, cipherText);
                if (!fallbackIsCurrent && LEGACY_DECRYPTION_LOGGED.compareAndSet(false, true)
                        && LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               "Config vault decrypted data using the legacy PBKDF2 iteration count. "
                                       + "New encrypted data will use the current default.");
                }
                return decrypted;
            } catch (CryptoException e) {
                currentFailure.addSuppressed(e);
                throw currentFailure;
            }
        }

        private static Base64Value decrypt(SymmetricCipher cipher, boolean current, String cipherText) {
            if (!current) {
                return cipher.decryptFromString(cipherText);
            }

            byte[] versioned;
            try {
                versioned = Base64Value.createFromEncoded(cipherText).toBytes();
            } catch (RuntimeException e) {
                throw new CryptoException("Config vault encrypted value does not use the current format", e);
            }
            if (versioned.length == 0 || versioned[0] != CURRENT_VERSION) {
                throw new CryptoException("Config vault encrypted value does not use the current format");
            }
            return cipher.decrypt(Base64Value.create(Arrays.copyOfRange(versioned, 1, versioned.length)));
        }

        Optional<SymmetricCipher> symmetricCipher() {
            return symmetricCipher;
        }

        Optional<SymmetricCipher> legacySymmetricCipher() {
            return legacySymmetricCipher;
        }

        Optional<Boolean> legacyEncryption() {
            return legacyEncryption;
        }

        Optional<Boolean> legacyFallback() {
            return legacyFallback;
        }
    }

    /**
     * Configuration of a secret.
     */
    @Configured(prefix = "config-vault",
                description = "Provider of secrets defined in configuration itself",
                provides = SecretsProviderConfig.class)
    public static class SecretConfig implements SecretsProviderConfig {
        private final Supplier<Optional<String>> value;

        private SecretConfig(Supplier<Optional<String>> value) {
            this.value = value;
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
            return new SecretConfig(valueSupplier);
        }

        /**
         * Create a new secret from a supplier, such as when computing the secret value.
         * The supplier must be thread safe.
         *
         * @param valueSupplier supplier of a value
         * @return a new secret configuration
         */
        public static SecretConfig create(Supplier<String> valueSupplier) {
            return new SecretConfig(() -> Optional.of(valueSupplier.get()));
        }

        /**
         * Create a new secret from a value.
         *
         * @param value the secret value
         * @return a new secret configuration
         */
        @ConfiguredOption(key = "value", description = "Value of the secret, can be a reference to another configuration key"
                + ", such as ${app.secret}")
        public static SecretConfig create(String value) {
            return new SecretConfig(() -> Optional.of(value));
        }

        Supplier<Optional<String>> value() {
            return value;
        }
    }

    /**
     * Fluent API builder for {@link ConfigVaultProvider}.
     */
    @Configured(prefix = "config-vault",
                description = "Secrets and Encryption provider using just configuration",
                provides = {SecurityProvider.class, SecretsProvider.class, EncryptionProvider.class})
    public static class Builder implements io.helidon.common.Builder<Builder, ConfigVaultProvider> {
        private Config config = Config.empty();
        private Optional<char[]> masterPassword = Optional.empty();
        private Optional<SymmetricCipher> symmetricCipher = Optional.empty();
        private Optional<SymmetricCipher> legacySymmetricCipher = Optional.empty();
        private Optional<Boolean> legacyEncryption = Optional.empty();
        private Optional<Boolean> legacyFallback = Optional.empty();
        private boolean resolvedLegacyEncryption;
        private boolean resolvedLegacyFallback;

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
            Optional<char[]> masterPassword = this.masterPassword
                    .or(() -> config.get("master-password").asString().map(String::toCharArray))
                    .or(Builder::resolveMasterPassword);
            this.symmetricCipher = masterPassword.map(EncryptionConfig::symmetricCipher);
            this.legacySymmetricCipher = masterPassword.map(EncryptionConfig::legacySymmetricCipher);
            this.resolvedLegacyEncryption = legacyEncryption
                    .or(() -> config.get("legacy-encryption").asBoolean().asOptional())
                    .orElse(false);
            this.resolvedLegacyFallback = legacyFallback
                    .or(() -> config.get("legacy-fallback").asBoolean().asOptional())
                    .orElse(false);

            return new ConfigVaultProvider(this);
        }

        /**
         * Update this builder from provided configuration.
         *
         * @param config configuration to use
         * @return updated builder
         * @deprecated use {@link #config(io.helidon.config.Config)} instead
         */
        @SuppressWarnings("removal")
        @Deprecated(since = "4.4.0", forRemoval = true)
        public Builder config(io.helidon.common.config.Config config) {
            return config(Config.config(config));
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
        @ConfiguredOption(required = true, type = String.class)
        public Builder masterPassword(char[] masterPassword) {
            this.masterPassword = Optional.of(masterPassword);
            return this;
        }

        /**
         * Temporary rolling-upgrade option to write encrypted data using the legacy encrypted value format.
         * Leave disabled for steady-state deployments.
         *
         * @param legacyEncryption whether to write data using legacy encryption
         * @return updated builder
         */
        @ConfiguredOption("false")
        public Builder legacyEncryption(boolean legacyEncryption) {
            this.legacyEncryption = Optional.of(legacyEncryption);
            return this;
        }

        /**
         * Temporary rolling-upgrade option to retry decryption with the alternate encrypted value format after
         * primary decryption fails. Disable after legacy encrypted values are replaced.
         *
         * @param legacyFallback whether decryption fallback should be enabled
         * @return updated builder
         */
        @ConfiguredOption("false")
        public Builder legacyFallback(boolean legacyFallback) {
            this.legacyFallback = Optional.of(legacyFallback);
            return this;
        }
    }
}
