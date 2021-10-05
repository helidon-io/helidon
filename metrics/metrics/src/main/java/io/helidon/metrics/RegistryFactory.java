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

package io.helidon.metrics;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsSettings;

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
public class RegistryFactory implements io.helidon.metrics.api.RegistryFactory {
//    private static final RegistryFactory INSTANCE = create();

    private final EnumMap<Type, Registry> registries = new EnumMap<>(Type.class);
    private final AtomicReference<MetricsSettings> metricsSettings;

    protected RegistryFactory(MetricsSettings metricsSettings, Registry appRegistry, Registry vendorRegistry) {
        this.metricsSettings = new AtomicReference<>(metricsSettings);
        registries.put(Type.APPLICATION, appRegistry);
        registries.put(Type.VENDOR, vendorRegistry);
    }

    private RegistryFactory(MetricsSettings metricsSettings) {
        this(metricsSettings, Registry.create(Type.APPLICATION), Registry.create(Type.VENDOR));
    }


    /**
     * Create a new factory with default configuration, with pre-filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @return a new registry factory
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#create()}
     */
    @Deprecated
    public static RegistryFactory create() {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.create());
    }

    /**
     * Create a new factory with provided configuration, with pre filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @param config configuration to use
     * @return a new registry factory
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#create(Config)}
     */
    @Deprecated
    public static RegistryFactory create(Config config) {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.create(config));
    }

    static RegistryFactory create(MetricsSettings metricsSettings) {
        return new RegistryFactory(metricsSettings);
    }

    /**
     * Get a singleton instance of the registry factory.
     *
     * @return registry factory singleton
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#getInstance()}
     */
    @Deprecated
    public static RegistryFactory getInstance() {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.getInstance());
    }

    /**
     * Get a singleton instance of the registry factory for and update it with provided configuration.
     * Note that the config is used only if nobody access the base registry.
     *
     * @param config configuration of the registry factory used to update behavior of the instance returned
     * @return registry factory singleton
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#getInstance(MetricsSettings)}
     */
    @Deprecated
    public static RegistryFactory getInstance(Config config) {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.getInstance(config));
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
    @Override
    public MetricRegistry getRegistry(Type type) {
        if (type == Type.BASE) {
            ensureBase();
        }
        return registries.get(type);
    }

    @Override
    public void update(MetricsSettings metricsSettings) {
        this.metricsSettings.set(metricsSettings);
    }

    private synchronized void ensureBase() {
        if (null == registries.get(Type.BASE)) {
            Registry registry = BaseRegistry.create(metricsSettings.get());
            registries.put(Type.BASE, registry);
        }
    }
}

