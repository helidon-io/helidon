/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.lang.System.Logger.Level;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.config.encryption.ConfigEncryptionException;
import io.helidon.config.encryption.ConfigProperties;
import io.helidon.config.encryption.EncryptionUtil;
import io.helidon.config.mp.spi.MpConfigFilter;

import org.eclipse.microprofile.config.Config;

/**
 * Provides possibility to decrypt passwords from configuration sources.
 * Configuration can be used to enforce encryption (e.g. we will fail on clear-text value).
 * <p>
 * Password in properties must be stored as follows:
 * <ul>
 * <li>${GCM=base64} - encrypted password using a master password (must be provided to prime through configuration, system
 * property or environment variable)</li>
 * <li>${RSA=base64} - encrypted password using a public key (private key must be available to Prime instance,
 * its location must be provided to prime through configuration, system property or environment variable)</li>
 * <li>${ALIAS=alias_name} - no longer needed, please use {@code ${alias_name}}</li>
 * <li>${CLEAR=text} - clear-text password. Intentionally denoting this value as a protectable one, so we can enforce encryption
 * (e.g. in prod)</li>
 * </ul>
 * Example:
 * <pre>
 * google_client_secret=${GCM=mYRkg+4Q4hua1kvpCCI2hg==}
 * service_password=${RSA-P=mYRkg+4Q4hua1kvpCCI2hg==}
 * another_password=${service_password}
 * cleartext_password=${CLEAR=known_password}
 * </pre>
 *
 * @see io.helidon.config.encryption.ConfigProperties#PRIVATE_KEYSTORE_PATH_ENV_VARIABLE
 * @see io.helidon.config.encryption.ConfigProperties#MASTER_PASSWORD_ENV_VARIABLE
 * @see io.helidon.config.encryption.ConfigProperties#MASTER_PASSWORD_CONFIG_KEY
 * @see io.helidon.config.encryption.ConfigProperties#REQUIRE_ENCRYPTION_ENV_VARIABLE
 */
public final class MpEncryptionFilter implements MpConfigFilter {
    static final String PREFIX_GCM = "${GCM=";
    static final String PREFIX_RSA = "${RSA-P=";
    private static final System.Logger LOGGER = System.getLogger(MpEncryptionFilter.class.getName());
    private static final String PREFIX_ALIAS = "${ALIAS=";
    private static final String PREFIX_CLEAR = "${CLEAR=";

    private PrivateKey privateKey;
    private char[] masterPassword;

    private boolean requireEncryption;

    private MpConfigFilter clearFilter;
    private MpConfigFilter rsaFilter;
    private MpConfigFilter aesFilter;
    private MpConfigFilter aliasFilter;

    /**
     * This constructor is only for use by {@link io.helidon.config.mp.spi.MpConfigFilter} service loader.
     */
    @Deprecated
    public MpEncryptionFilter() {
    }

    @Override
    public void init(org.eclipse.microprofile.config.Config config) {
        this.requireEncryption = getEnv(ConfigProperties.REQUIRE_ENCRYPTION_ENV_VARIABLE)
                .map(Boolean::parseBoolean)
                .or(() -> config.getOptionalValue(ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY, Boolean.class))
                .orElse(true);

        this.masterPassword = resolveMasterPassword(requireEncryption, config)
                .orElse(null);
        this.privateKey = resolvePrivateKey(config)
                .orElse(null);

        if (null != privateKey && !(privateKey instanceof RSAPrivateKey)) {
            throw new ConfigEncryptionException("Private key must be an RSA private key, but is: "
                                                        + privateKey.getClass().getName());
        }

        MpConfigFilter noOp = (key, stringValue) -> stringValue;

        aesFilter = (null == masterPassword ? noOp : (key, stringValue) -> decryptAes(masterPassword, stringValue));
        rsaFilter = (null == privateKey ? noOp : (key, stringValue) -> decryptRsa(privateKey, stringValue));
        clearFilter = this::clearText;
        aliasFilter = (key, stringValue) -> aliased(stringValue, config);

    }

    @Override
    public String apply(String propertyName, String value) {
        return maybeDecode(propertyName, value);
    }

    private static String removePlaceholder(String prefix, String value) {
        return value.substring(prefix.length(), value.length() - 1);
    }

    private String maybeDecode(String propertyName, String value) {
        Set<String> processedValues = new HashSet<>();

        do {
            processedValues.add(value);
            if (!value.startsWith("${") && !value.endsWith("}")) {
                //this is not encoded, safely return
                return value;
            }
            value = aliasFilter.apply(propertyName, value);
            value = clearFilter.apply(propertyName, value);
            value = rsaFilter.apply(propertyName, value);
            value = aesFilter.apply(propertyName, value);
        } while (!processedValues.contains(value));

        return value;
    }

    private String clearText(String propertyName, String value) {
        // cleartext_password=${CLEAR=known_password}
        if (value.startsWith(PREFIX_CLEAR)) {
            if (requireEncryption) {
                throw new ConfigEncryptionException("Key \"" + propertyName + "\" is a clear text password, yet encryption is "
                                                            + "required");
            }
            return removePlaceholder(PREFIX_CLEAR, value);
        }

        return value;
    }

    private String aliased(String value, Config config) {

        if (value.startsWith(PREFIX_ALIAS)) {
            // another_password=${ALIAS=service_password}
            String alias = removePlaceholder(PREFIX_ALIAS, value);

            return config.getOptionalValue(alias, String.class)
                    .orElseThrow(() -> new NoSuchElementException("Aliased key not found. Value: " + value));
        }

        return value;
    }

    private String decryptRsa(PrivateKey privateKey, String value) {
        // service_password=${RSA=mYRkg+4Q4hua1kvpCCI2hg==}
        if (value.startsWith(PREFIX_RSA)) {
            String b64Value = removePlaceholder(PREFIX_RSA, value);
            try {
                return EncryptionUtil.decryptRsa(privateKey, b64Value);
            } catch (ConfigEncryptionException e) {
                LOGGER.log(Level.TRACE, () -> "Failed to decrypt " + value, e);
                return value;
            }
        }

        return value;
    }

    private String decryptAes(char[] masterPassword, String value) {
        // google_client_secret=${GCM=mYRkg+4Q4hua1kvpCCI2hg==}

        if (value.startsWith(PREFIX_GCM)) {
            String b64Value = value.substring(PREFIX_GCM.length(), value.length() - 1);
            try {
                return EncryptionUtil.decryptAes(masterPassword, b64Value);
            } catch (ConfigEncryptionException e) {
                LOGGER.log(Level.TRACE, () -> "Failed to decrypt " + value, e);
                return value;
            }
        }

        return value;
    }

    static Optional<String> getEnv(String envVariable) {
        return Optional.ofNullable(System.getenv(envVariable));
    }

    static Optional<PrivateKey> resolvePrivateKey(org.eclipse.microprofile.config.Config config) {
        return resolvePrivateKey(MpConfig.toHelidonConfig(config).get("security.config.rsa"));
    }

    static Optional<PrivateKey> resolvePrivateKey(io.helidon.config.Config config) {
        // load configuration values
        Keys.Builder builder = Keys.builder();
        builder.config(config);

        builder.pem(pemBuilder -> {
            getEnv(ConfigProperties.PRIVATE_KEY_PEM_PATH_ENV_VARIABLE)
                    .map(Paths::get)
                    .ifPresent(path -> pemBuilder.key(Resource.create(path)));

            getEnv(ConfigProperties.PRIVATE_KEY_PASS_ENV_VARIABLE)
                    .map(String::toCharArray)
                    .ifPresent(pemBuilder::keyPassphrase);
        });

        // override the ones defined in environment variables
        getEnv(ConfigProperties.PRIVATE_KEYSTORE_PATH_ENV_VARIABLE)
                .map(Paths::get)
                .ifPresent(path -> builder.keystore(keystoreBuilder -> {
                    keystoreBuilder.keystore(Resource.create(path));
                    getEnv(ConfigProperties.PRIVATE_KEYSTORE_TYPE_ENV_VARIABLE)
                            .ifPresent(keystoreBuilder::type);

                    getEnv(ConfigProperties.PRIVATE_KEYSTORE_PASS_ENV_VARIABLE)
                            .map(String::toCharArray)
                            .ifPresent(keystoreBuilder::passphrase);

                    getEnv(ConfigProperties.PRIVATE_KEY_PASS_ENV_VARIABLE)
                            .map(String::toCharArray)
                            .ifPresent(keystoreBuilder::keyPassphrase);

                    getEnv(ConfigProperties.PRIVATE_KEY_ALIAS_ENV_VARIABLE)
                            .ifPresent(keystoreBuilder::keyAlias);
                }));

        Optional<PrivateKey> result = builder.build().privateKey();

        if (result.isEmpty()) {
            LOGGER.log(Level.DEBUG, "Securing properties using asymmetric cipher is not available, as private key is not "
                    + "configured");
        }

        return result;
    }

    static Optional<char[]> resolveMasterPassword(boolean requireEncryption, org.eclipse.microprofile.config.Config config) {
        Optional<char[]> result = getEnv(ConfigProperties.MASTER_PASSWORD_ENV_VARIABLE)
                .or(() -> {
                    Optional<String> value = config.getOptionalValue(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY, String.class);
                    if (value.isPresent()) {
                        if (requireEncryption) {
                            LOGGER.log(Level.WARNING,
                                       "Master password is configured as clear text in configuration when encryption is "
                                               + "required. "
                                               + "This value will be ignored. System property or environment variable "
                                               + "expected!!!");
                            return Optional.empty();
                        }
                    }
                    return value;
                })
                .map(String::toCharArray);

        if (result.isEmpty()) {
            LOGGER.log(Level.DEBUG, "Securing properties using master password is not available, as master password is not "
                    + "configured");
        }

        return result;
    }

}
