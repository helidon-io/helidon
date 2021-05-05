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

package io.helidon.config.encryption;

/**
 * Properties and their default values.
 */
public final class ConfigProperties {

    /**
     * Environment variable to set master password ({@value}).
     */
    public static final String MASTER_PASSWORD_ENV_VARIABLE = "SECURE_CONFIG_AES_MASTER_PWD";

    /**
     * Configuration property key to set master password ({@value}).
     */
    public static final String MASTER_PASSWORD_CONFIG_KEY = "security.config.aes.insecure-passphrase";

    /**
     * Environment variable to set location of private key ({@value}) keystore.
     * Absolute path or path relative to working directory.
     */
    public static final String PRIVATE_KEYSTORE_PATH_ENV_VARIABLE = "SECURE_CONFIG_RSA_PRIVATE_KEY";

    /**
     * Environment variable to set location of private key ({@value}) PEM file.
     */
    public static final String PRIVATE_KEY_PEM_PATH_ENV_VARIABLE = "SECURE_CONFIG_RSA_PEM_KEY";

    /**
     * Environment variable to set whether to require encryption of secrets or not (<code>{@value}</code>).
     * If set to true, an exception will be thrown in the following cases:
     * <ul>
     * <li>Password is stored in clear text</li>
     * <li>Master password is stored in configuration</li>
     * </ul>
     */
    public static final String REQUIRE_ENCRYPTION_ENV_VARIABLE = "SECURE_CONFIG_REQUIRE_ENCRYPTION";

    /**
     * Environment variable to set key type to use.
     * Allowed values:
     * <ul>
     * <li>{@code RSA} - default value, unix-like non-encrypted private key</li>
     * <li>{@code PKCS12} - keystore, password protected store and/or private key</li>
     * </ul>
     */
    public static final String PRIVATE_KEYSTORE_TYPE_ENV_VARIABLE = "SECURE_CONFIG_PRIVATE_KEY_TYPE";

    /**
     * Environment variable to set private key alias within a keystore.
     */
    public static final String PRIVATE_KEY_ALIAS_ENV_VARIABLE = "SECURE_CONFIG_PRIVATE_KEY_ALIAS";

    /**
     * Environment variable to set pass phrase for keystore.
     */
    public static final String PRIVATE_KEYSTORE_PASS_ENV_VARIABLE = "SECURE_CONFIG_PRIVATE_KEYSTORE_PASSPHRASE";

    /**
     * Environment variable to set pass phrase for private key.
     */
    public static final String PRIVATE_KEY_PASS_ENV_VARIABLE = "SECURE_CONFIG_PRIVATE_KEY_PASSPHRASE";

    /**
     * Configuration key to set
     * whether to require encryption of secrets or not (<code>{@value}</code>).
     * If set to true, an exception will be thrown in the following cases:
     * <ul>
     * <li>Password is stored in clear text</li>
     * <li>Master password is stored in configuration</li>
     * </ul>
     */
    public static final String REQUIRE_ENCRYPTION_CONFIG_KEY = "security.config.require-encryption";

    private ConfigProperties() {
        throw new IllegalStateException("Utility class");
    }
}
