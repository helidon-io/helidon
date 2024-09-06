/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.config;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.spi.ConfigProvider;
import io.helidon.common.context.ContextSingleton;

/**
 * Global configuration can be set by a user before any Helidon code is invoked, to override default discovery
 * of configuration done by Helidon components.
 * <p>
 * If method {@link #config(java.util.function.Supplier)} is called before Helidon is started, Helidon will only use that
 * configuration.
 * <p>
 * You may still use custom instances of configuration when using configurable APIs directly.
 */
public final class GlobalConfig {
    private static final Config EMPTY = Config.empty();
    private static final LazyValue<Config> DEFAULT_CONFIG = LazyValue.create(GlobalConfig::create);
    private static final ContextSingleton<Config> CONTEXT_VALUE = ContextSingleton.create(GlobalConfig.class,
                                                                                          Config.class);

    private GlobalConfig() {
    }

    /**
     * Whether a global configuration has already been configured.
     *
     * @return {@code true} if there is a global configuration set already, {@code false} otherwise
     */
    public static boolean configured() {
        return CONTEXT_VALUE.isPresent();
    }

    /**
     * Global configuration instance.
     *
     * @return Helidon shared configuration instance if configured, or an empty configuration if not
     * @see #config(java.util.function.Supplier)
     * @see #config(java.util.function.Supplier, boolean)
     */
    public static Config config() {
        return CONTEXT_VALUE.value().orElseGet(DEFAULT_CONFIG);
    }

    /**
     * Set global configuration if not yet configured.
     *
     * @param config configuration supplier to use if config is not yet configured
     * @return used global configuration instance
     */
    public static Config config(Supplier<Config> config) {
        return config(config, false);
    }

    /**
     * Set global configuration.
     *
     * @param config    configuration to use
     * @param overwrite whether to overwrite an existing configured value
     * @return current global config
     */
    public static Config config(Supplier<Config> config, boolean overwrite) {
        Objects.requireNonNull(config);

        if (overwrite || !configured()) {
            // there is a certain risk we may do this twice, if two components try to set global config in parallel.
            // as the result was already unclear (as order matters), we do not need to be 100% thread safe here
            CONTEXT_VALUE.set(config.get());
            return config();
        } else {
            return CONTEXT_VALUE.get(config);
        }
    }

    static Config create() {
        List<ConfigProvider> providers = HelidonServiceLoader.create(ServiceLoader.load(ConfigProvider.class))
                .asList();
        // no implementations available, use empty configuration
        if (providers.isEmpty()) {
            return EMPTY;
    }
        // there is a valid provider, let's use its default configuration
        return providers.getFirst()
                .create();
    }
}
