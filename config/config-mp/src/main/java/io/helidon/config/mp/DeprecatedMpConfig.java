/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.Config;

/**
 * A utility class to handle MicroProfile configuration properties that should no longer be used.
 * <p>
 * For one major release, the property is retrieved through this class, to warn about the usage.
 * In next major release, the deprecated property is removed (as is use of this class).
 * <p>
 * This class is closely patterned after {@code io.helidon.config.DeprecatedConfig} and works whether the MP config object
 * is proxied by CDI or not. That other class can be used in conjunction with
 * {@link io.helidon.config.mp.MpConfig#toHelidonConfig(org.eclipse.microprofile.config.Config)} for MP
 * config objects that <em>are not</em> injected but that does work for MP config objects proxied by CDI. This one does work for
 * those use cases.
 */
public final class DeprecatedMpConfig {
    private static final Logger LOGGER = Logger.getLogger(DeprecatedMpConfig.class.getName());

    private DeprecatedMpConfig() {
    }

    /**
     * Get a value from config, attempting to read both the keys.
     * Warning is logged if either the current key is not defined, or both the keys are defined.
     *
     * @param config        configuration instance
     * @param type          type of the retrieved value
     * @param currentKey    key that should be used
     * @param deprecatedKey key that should not be used
     * @param <T>           type of the retrieved value
     * @return config value of the current key if exists, or the deprecated key if it does not, an empty {@code Optional}
     *         otherwise
     */
    public static <T> Optional<T> getConfigValue(Config config, Class<T> type, String currentKey, String deprecatedKey) {
        Optional<T> deprecatedConfig = config.getOptionalValue(deprecatedKey, type);
        Optional<T> currentConfig = config.getOptionalValue(currentKey, type);

        if (deprecatedConfig.isPresent()) {
            if (currentConfig.isPresent()) {
                LOGGER.warning("You are using both a deprecated configuration and a current one. "
                                       + "Deprecated key: \"" + deprecatedKey + "\", "
                                       + "current key: \"" + currentKey + "\", "
                                       + "only the current key will be used, and deprecated will be ignored.");
                return currentConfig;
            } else {
                LOGGER.warning("You are using a deprecated configuration key. "
                                       + "Deprecated key: \"" + deprecatedKey + "\", "
                                       + "current key: \"" + currentKey + "\".");
                return deprecatedConfig;
            }
        } else {
            return currentConfig;
        }
    }
}
