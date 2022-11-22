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

package io.helidon.common.config;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.spi.ConfigProvider;

/**
 * Provides access to the global {@link io.helidon.common.config.Config} singleton instance.
 * <p>
 * Most callers are simply expected to call the {@link #config()} method to resolve the global configuration. Note, however,
 * that the global configuration instance needs to be made available prior to calling the {@link #config()} method. There are
 * two techniques for establishing the global configuration instance: (1) via the Java {@link java.util.ServiceLoader} and
 * (2) programmatically via the {@link #config(io.helidon.common.config.Config)} method. The {@link #config()} method will
 * apply the following strategy to resolve and cache the global config instance:
 * <ol>
 *     <li>if the instance has already been established and cached then use it.</li>
 *     <li>if the instance has programmatically been set then use it - this is the same as the cached instance.</li>
 *     <li>use the service loader to resolve the config instance, and if found then cache it.</li>
 * </ol>
 * Note also that the {@link #reset()} method can be used to clear the cached instance. However, doing so should not be expected
 * to alter any callers that have previously obtained the global configuration instance prior to calling the {@link #reset()}
 * method.
 * <p>
 * Note that this class is not thread safe. If the global configuration must be set programmatically then it should therefore
 * be set on the main thread, and typically early in the jvm lifecycle.
 *
 * @see io.helidon.common.config.spi.ConfigProvider
 */
public class ConfigHolder {
    private static final AtomicReference<Optional<Config>> INSTANCE = new AtomicReference<>();

    private ConfigHolder() {
    }

    /**
     * Returns the global {@link io.helidon.common.config.Config} instance.
     *
     * @return the global instance
     */
    public static Optional<Config> config() {
        if (Objects.isNull(INSTANCE.get())) {
            INSTANCE.set(load());
        }
        return INSTANCE.get();
    }

    /**
     * Proactively set the global configuration instance. Callers are reminded that {@link #reset()} must be called in order
     * to clear any previously set global instance. Failure to do so will result in an exception being thrown.
     *
     * @param cfg the configuration
     * @throws java.lang.IllegalStateException called if the config instance was previously been set
     */
    public static void config(Config cfg) {
        boolean set = INSTANCE.compareAndSet(null,
                                             Optional.of(Objects.requireNonNull(cfg)));
        if (!set) {
            throw new IllegalStateException(Config.class.getSimpleName() + " already set");
        }
    }

    /**
     * Clears the config instance; not recommended for common use.
     */
    public static void reset() {
        System.Logger.Level level = Objects.nonNull(INSTANCE.get()) ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
        System.Logger logger = System.getLogger(ConfigHolder.class.getName());
        logger.log(level, "resetting...");
        INSTANCE.set(null);
    }

    private static Optional<Config> load() {
        Optional<ConfigProvider> provider = HelidonServiceLoader
                .create(ServiceLoader.load(ConfigProvider.class, ConfigProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst();
        if (provider.isEmpty()) {
            return Optional.empty();
        }

        return provider.get().__config();
    }

}
