/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.spi.ConfigProvider;
import io.helidon.service.registry.Services;

/**
 * Global configuration can be set by a user before any Helidon code is invoked, to override default discovery
 * of configuration done by Helidon components.
 * <p>
 * If method {@link #config(java.util.function.Supplier)} is called before Helidon is started, Helidon will only use that
 * configuration.
 * <p>
 * You may still use custom instances of configuration when using configurable APIs directly.
 * @deprecated Use {@link io.helidon.service.registry.Services#get(Class)}, or
 * {@link io.helidon.service.registry.ServiceRegistry#get(Class)} if you have an instance ready to obtain the "global"
 * configuration; in case you are writing a registry service, simply inject the config instance; use
 * {@link io.helidon.service.registry.Services#set(Class, Object[])} to use a custom instance of configuration, just make sure
 * it is registered before it is used the first time
 */
@Deprecated(forRemoval = true, since = "4.2.0")
public final class GlobalConfig {
    private static final System.Logger LOGGER = System.getLogger(GlobalConfig.class.getName());
    private static final AtomicBoolean LOGGED_REGISTERED = new AtomicBoolean(false);
    private static final Config EMPTY = Config.empty();
    private static final LazyValue<Config> DEFAULT_CONFIG = LazyValue.create(() -> {
        List<ConfigProvider> providers = HelidonServiceLoader.create(ServiceLoader.load(ConfigProvider.class))
                .asList();
        // no implementations available, use empty configuration
        if (providers.isEmpty()) {
            return EMPTY;
        }
        // there is a valid provider, let's use its default configuration
        return providers.getFirst()
                .create();
    });
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>();

    private GlobalConfig() {
    }

    /**
     * Whether a global configuration has already been configured.
     *
     * @return {@code true} if there is a global configuration set already, {@code false} otherwise
     */
    public static boolean configured() {
        return CONFIG.get() != null;
    }

    /**
     * Global configuration instance.
     *
     * @return Helidon shared configuration instance if configured, or an empty configuration if not
     * @see #config(java.util.function.Supplier)
     * @see #config(java.util.function.Supplier, boolean)
     * @deprecated use {@link io.helidon.service.registry.Services#get(Class)} instead
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    public static Config config() {
        return configured() ? CONFIG.get() : DEFAULT_CONFIG.get();
    }

    /**
     * Set global configuration if not yet configured.
     *
     * @param config configuration supplier to use if config is not yet configured
     * @return used global configuration instance
     * @deprecated use {@link io.helidon.service.registry.Services#set(Class, Object[])} instead
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    public static Config config(Supplier<Config> config) {
        return config(config, false);
    }

    /**
     * Set global configuration.
     *
     * @param config configuration to use
     * @param overwrite whether to overwrite an existing configured value
     * @return current global config
     * @deprecated use {@link io.helidon.service.registry.Services#set(Class, Object[])} instead
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    public static Config config(Supplier<Config> config, boolean overwrite) {
        Objects.requireNonNull(config);

        if (overwrite || !configured()) {
            // there is a certain risk we may do this twice, if two components try to set global config in parallel.
            // as the result was already unclear (as order matters), we do not need to be 100% thread safe here
            Config configInstance = config.get();
            CONFIG.set(configInstance);

            try {
                Services.set(Config.class, configInstance);
            } catch (Exception e) {
                if (LOGGED_REGISTERED.compareAndSet(false, true)) {
                    // only log this once
                    LOGGER.log(System.Logger.Level.WARNING,
                               "Attempting to set a config instance when it either was already "
                                       + "set once, or it was already used by a component. "
                                       + "This will not work in future versions of Helidon",
                               e);
                }
            }
        }
        return CONFIG.get();
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
