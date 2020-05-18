/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.logging.Logger;

/**
 * A utility class to handle configuration properties that should no longer be used.
 * <p>
 * For one major release, the property is retrieved through this class, to warn about the usage.
 * In next major release, the deprecated property is removed (as is use of this class).
 */
public final class DeprecatedConfig {
    private static final Logger LOGGER = Logger.getLogger(DeprecatedConfig.class.getName());

    private DeprecatedConfig() {
    }

    /**
     * Get a value from config, attempting to read both the keys.
     * Warning is logged if either the current key is not defined, or both the keys are defined.
     *
     * @param config configuration instance
     * @param currentKey key that should be used
     * @param deprecatedKey key that should not be used
     *
     * @return config node of the current key if exists, of the deprecated key if it does not, missing node otherwise
     */
    public static Config get(Config config, String currentKey, String deprecatedKey) {
        Config deprecatedConfig = config.get(deprecatedKey);
        Config currentConfig = config.get(currentKey);

        if (deprecatedConfig.exists()) {
            if (currentConfig.exists()) {
                LOGGER.warning("You are using both a deprecated configuration and a current one. "
                                       + "Deprecated key: \"" + deprecatedConfig.key() + "\", "
                                       + "current key: \"" + currentConfig.key() + "\", "
                                       + "only the current key will be used, and deprecated will be ignored.");
                return currentConfig;
            } else {
                LOGGER.warning("You are using a deprecated configuration key. "
                                       + "Deprecated key: \"" + deprecatedConfig.key() + "\", "
                                       + "current key: \"" + currentConfig.key() + "\".");
                return deprecatedConfig;
            }
        } else {
            return currentConfig;
        }
    }
}
