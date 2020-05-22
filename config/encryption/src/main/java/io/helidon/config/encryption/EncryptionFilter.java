/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.encryption;

import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigFilter;

/**
 * Provides possibility to decrypt passwords from configuration sources.
 * Configuration can be used to enforce encryption (e.g. we will fail on clear-text value).
 * <p>
 * Password in properties must be stored as follows:
 * <ul>
 * <li>${AES=base64} - encrypted password using a master password (must be provided to prime through configuration, system
 * property or environment variable)</li>
 * <li>${RSA=base64} - encrypted password using a public key (private key must be available to Prime instance,
 * its location must be provided to prime through configuration, system property or environment variable)</li>
 * <li>${ALIAS=alias_name} - reference to another property, that is encrypted</li>
 * <li>${CLEAR=text} - clear-text password. Intentionally denoting this value as a protectable one, so we can enforce encryption
 * (e.g. in prod)</li>
 * </ul>
 * Example:
 * <pre>
 * google_client_secret=${AES=mYRkg+4Q4hua1kvpCCI2hg==}
 * service_password=${RSA=mYRkg+4Q4hua1kvpCCI2hg==}
 * another_password=${ALIAS=service_password}
 * cleartext_password=${CLEAR=known_password}
 * </pre>
 *
 * @see ConfigProperties#PRIVATE_KEYSTORE_PATH_ENV_VARIABLE
 * @see ConfigProperties#MASTER_PASSWORD_ENV_VARIABLE
 * @see ConfigProperties#MASTER_PASSWORD_CONFIG_KEY
 * @see ConfigProperties#REQUIRE_ENCRYPTION_ENV_VARIABLE
 */
public final class EncryptionFilter implements ConfigFilter {
    private static final String PREFIX_LEGACY_AES = "${AES=";
    private static final String PREFIX_LEGACY_RSA = "${RSA=";
    static final String PREFIX_GCM = "${GCM=";
    static final String PREFIX_RSA = "${RSA-P=";
    private static final Logger LOGGER = Logger.getLogger(EncryptionFilter.class.getName());
    private static final String PREFIX_ALIAS = "${ALIAS=";
    private static final String PREFIX_CLEAR = "${CLEAR=";

    static {
        HelidonFeatures.register("Config", "Encryption");
    }

    private final PrivateKey privateKey;
    private final char[] masterPassword;

    private final boolean requireEncryption;

    private final ConfigFilter clearFilter;
    private final ConfigFilter rsaFilter;
    private final ConfigFilter aesFilter;
    private final ConfigFilter aliasFilter;

    private EncryptionFilter(Builder builder, Config config) {
        if (builder.fromConfig) {

            this.requireEncryption = EncryptionUtil.getEnv(ConfigProperties.REQUIRE_ENCRYPTION_ENV_VARIABLE)
                                                                 .map(Boolean::parseBoolean)
                    .or(() -> config.get(ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY).asBoolean().asOptional())
                    .orElse(true);

            this.masterPassword = EncryptionUtil.resolveMasterPassword(requireEncryption, config).orElse(null);
            this.privateKey = EncryptionUtil.resolvePrivateKey(config.get("security.config.rsa"))
                    .orElse(null);
        } else {
            this.requireEncryption = builder.requireEncryption;
            this.privateKey = builder.privateKeyConfig.privateKey()
                    .orElseThrow(() -> new ConfigEncryptionException("Private key configuration is invalid"));
            this.masterPassword = builder.masterPassword;
        }

        if (null != privateKey && !(privateKey instanceof RSAPrivateKey)) {
            throw new ConfigEncryptionException("Private key must be an RSA private key, but is: "
                                                        + privateKey.getClass().getName());
        }

        ConfigFilter noOp = (key, stringValue) -> stringValue;

        aesFilter = (null == masterPassword ? noOp : (key, stringValue) -> decryptAes(masterPassword, stringValue));
        rsaFilter = (null == privateKey ? noOp : (key, stringValue) -> decryptRsa(privateKey, stringValue));
        clearFilter = this::clearText;
        aliasFilter = (key, stringValue) -> aliased(stringValue, config);

    }

    private static String removePlaceholder(String prefix, String value) {
        return value.substring(prefix.length(), value.length() - 1);
    }

    /**
     * Create a filter based on configuration (it takes its configuration from the configuration object it filters).
     *
     * @return ConfigFilter instance to register to config
     */
    public static Function<Config, ConfigFilter> fromConfig() {
        return builder().fromConfig().buildProvider();
    }

    /**
     * Builder to programmatically configure filter.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String apply(Config.Key key, String stringValue) {
        return maybeDecode(key, stringValue);
    }

    private String maybeDecode(Config.Key key, String value) {
        Set<String> processedValues = new HashSet<>();

        do {
            processedValues.add(value);
            if (!value.startsWith("${") && !value.endsWith("}")) {
                //this is not encoded, safely return
                return value;
            }
            value = aliasFilter.apply(key, value);
            value = clearFilter.apply(key, value);
            value = rsaFilter.apply(key, value);
            value = aesFilter.apply(key, value);
        } while (!processedValues.contains(value));

        return value;
    }

    private String clearText(Config.Key key, String value) {
        // cleartext_password=${CLEAR=known_password}

        if (value.startsWith(PREFIX_CLEAR)) {
            if (requireEncryption) {
                throw new ConfigEncryptionException("Key \"" + key + "\" is a clear text password, yet encryption is required");
            }
            return removePlaceholder(PREFIX_CLEAR, value);
        }

        return value;
    }

    private String aliased(String value, Config config) {

        if (value.startsWith(PREFIX_ALIAS)) {
            // another_password=${ALIAS=service_password}
            String alias = removePlaceholder(PREFIX_ALIAS, value);

            return config.get(alias).asString().orElseThrow(MissingValueException.createSupplier(Config.Key.create(alias)));
        }

        return value;
    }

    private String decryptRsa(PrivateKey privateKey, String value) {
        // service_password=${RSA=mYRkg+4Q4hua1kvpCCI2hg==}
        if (value.startsWith(PREFIX_LEGACY_RSA)) {
            LOGGER.log(Level.WARNING, () -> "You are using legacy RSA encryption. Please re-encrypt the value with RSA-P.");
            String b64Value = removePlaceholder(PREFIX_LEGACY_RSA, value);
            try {
                return EncryptionUtil.decryptRsaLegacy(privateKey, b64Value);
            } catch (ConfigEncryptionException e) {
                LOGGER.log(Level.FINEST, e, () -> "Failed to decrypt " + value);
                return value;
            }
        } else if (value.startsWith(PREFIX_RSA)) {
            String b64Value = removePlaceholder(PREFIX_RSA, value);
            try {
                return EncryptionUtil.decryptRsa(privateKey, b64Value);
            } catch (ConfigEncryptionException e) {
                LOGGER.log(Level.FINEST, e, () -> "Failed to decrypt " + value);
                return value;
            }
        }

        return value;
    }

    private String decryptAes(char[] masterPassword, String value) {
        // google_client_secret=${AES=mYRkg+4Q4hua1kvpCCI2hg==}

        if (value.startsWith(PREFIX_LEGACY_AES)) {
            LOGGER.log(Level.WARNING, () -> "You are using legacy AES encryption. Please re-encrypt the value with GCM.");
            String b64Value = value.substring(PREFIX_LEGACY_AES.length(), value.length() - 1);
            try {
                return EncryptionUtil.decryptAesLegacy(masterPassword, b64Value);
            } catch (ConfigEncryptionException e) {
                LOGGER.log(Level.FINEST, e, () -> "Failed to decrypt " + value);
                return value;
            }
        } else if (value.startsWith(PREFIX_GCM)) {
            String b64Value = value.substring(PREFIX_GCM.length(), value.length() - 1);
            try {
                return EncryptionUtil.decryptAes(masterPassword, b64Value);
            } catch (ConfigEncryptionException e) {
                LOGGER.log(Level.FINEST, e, () -> "Failed to decrypt " + value);
                return value;
            }
        }

        return value;
    }

    /**
     * Builder to programmatically setup {@link EncryptionFilter}.
     */
    public static class Builder {
        private boolean fromConfig = false;
        private char[] masterPassword;
        private KeyConfig privateKeyConfig;
        private boolean requireEncryption = true;

        private Builder fromConfig() {
            fromConfig = true;
            return this;
        }

        /**
         * Master password for AES based decryption.
         *
         * @param password password to use
         * @return updated builder instance
         */
        public Builder masterPassword(char[] password) {
            this.masterPassword = Arrays.copyOf(password, password.length);
            return this;
        }

        /**
         * Private key for RSA based decryption.
         *
         * @param privateKey private key to use
         * @return updated builder instance
         */
        public Builder privateKey(KeyConfig privateKey) {
            this.privateKeyConfig = privateKey;
            return this;
        }

        /**
         * Whether to require encryption of passwords in properties.
         *
         * @param require if set to true, clear text passwords will fail
         * @return updated builder instance
         */
        public Builder requireEncryption(boolean require) {
            this.requireEncryption = require;
            return this;
        }

        /**
         * Create a new {@link EncryptionFilter} provider based on this builder.
         *
         * @return filter instance
         */
        public Function<Config, ConfigFilter> buildProvider() {
            return config -> new EncryptionFilter(this, config);
        }
    }
}
