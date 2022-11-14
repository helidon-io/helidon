/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.services;

import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Provides access to the global singleton {@link io.helidon.config.Config} instance.
 */
@Singleton
public class ConfigProvider implements Provider<Config>,
                                       Supplier<Config> {

    private static final System.Logger LOGGER = System.getLogger(ConfigProvider.class.getName());
//    private static final LazyValue<ConfigProvider> PROVIDER_INSTANCE = LazyValue.create(ConfigProvider::new);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicReference<Config> INSTANCE = new AtomicReference<>();

    protected ConfigProvider() {
    }

    private static Config load() {
        Config cfg = HelidonServiceLoader.create(ServiceLoader.load(Config.class))
                .asList()
                .stream()
                .findFirst().orElse(null);
        if (Objects.isNull(cfg)) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "no " + Config.class.getName() + " instance found - initializing with an empty configuration");
            cfg = Config.create();
        }
        return cfg;
    }

    @Override
    public Config get() {
        return getConfigInstance();
    }

    /**
     * @return The global {@link io.helidon.config.Config} instance.
     */
    public static Config getConfigInstance() {
        if (INITIALIZED.compareAndSet(false, true) && Objects.isNull(INSTANCE.get())) {
            INSTANCE.set(load());
        }
        return INSTANCE.get();
    }

    /**
     * Proactively sets the global configuration instance.
     *
     * @param cfg the configuration
     * @return true if the config was accepted; this can only be called once.
     */
    public static boolean setConfigInstance(Config cfg) {
        boolean set = INSTANCE.compareAndSet(null, Objects.requireNonNull(cfg,"config is required"));
        if (!set && cfg != INSTANCE.get()) {
            LOGGER.log(System.Logger.Level.ERROR,
                       Config.class + " instance has already been set; the new config will be ignored");
        }
        return set;
    }

    /**
     * Clears the config instance; not recommended for common use.
     */
    public static void reset() {
        System.Logger.Level level = Objects.nonNull(INSTANCE.get()) ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
        LOGGER.log(level, "Resetting...");
        INITIALIZED.set(false);
        INSTANCE.set(null);
    }

}
