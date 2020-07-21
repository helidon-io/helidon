/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;

/**
 * Access point to all registries.
 *
 * There are two options to use the factory:
 * <ol>
 *     <li>A singleton instance, obtained through {@link #getInstance()} or {@link #getInstance(io.helidon.config.Config)}.
 *     This instance is lazily initialized - the latest call that provides a config instance before a
 *     {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} registry is obtained would be used to configure
 *     the base registry (as that is the only configurable registry in current implementation)
 *     </li>
 *     <li>A custom instance, obtained through {@link #create(Config)} or {@link #create()}. This would create a
 *     new instance of a registry factory (in case multiple instances are desired), independent on the singleton instance
 *     and on other instances provided by these methods.</li>
 * </ol>
 */
// this class is not immutable, as we may need to update registries with configuration post creation
// see Github issue #360
public final class RegistryFactory {
    private static final RegistryFactory INSTANCE = create();

    private final EnumMap<Type, Registry> registries = new EnumMap<>(Type.class);
    private final AtomicReference<Config> config;

    private RegistryFactory(Config config) {
        Registry registry = Registry.create(Type.APPLICATION);
        registries.put(Type.APPLICATION, registry);

        registry = Registry.create(Type.VENDOR);
        registries.put(Type.VENDOR, registry);

        this.config = new AtomicReference<>(config);
    }


    /**
     * Create a new factory with default configuration, with pre-filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @return a new registry factory
     */
    public static RegistryFactory create() {
        return create(Config.empty());
    }

    /**
     * Create a new factory with provided configuration, with pre filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @param config configuration to use
     * @return a new registry factory
     */
    public static RegistryFactory create(Config config) {
        return new RegistryFactory(config);
    }

    /**
     * Get a singleton instance of the registry factory.
     *
     * @return registry factory singleton
     */
    public static RegistryFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Get a singleton instance of the registry factory for and update it with provided configuration.
     * Note that the config is used only if nobody access the base registry.
     *
     * @param config configuration of the registry factory used to update behavior of the instance returned
     * @return registry factory singleton
     */
    public static RegistryFactory getInstance(Config config) {
        INSTANCE.update(config);
        return INSTANCE;
    }

    Registry getARegistry(Type type) {
        if (type == Type.BASE) {
            ensureBase();
        }
        return registries.get(type);
    }

    /**
     * Get a registry based on its type.
     * For {@link Type#APPLICATION} and {@link Type#VENDOR} returns a modifiable registry,
     * for {@link Type#BASE} returns a final registry (cannot register new metrics).
     *
     * @param type type of registry
     * @return MetricRegistry for the type defined.
     */
    public MetricRegistry getRegistry(Type type) {
        if (type == Type.BASE) {
            ensureBase();
        }
        return registries.get(type);
    }

    private void update(Config config) {
        this.config.set(config);
    }

    private synchronized void ensureBase() {
        if (null == registries.get(Type.BASE)) {
            Registry registry = BaseRegistry.create(config.get());
            registries.put(Type.BASE, registry);
        }
    }
}

