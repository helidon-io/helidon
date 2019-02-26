/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;

/**
 * Access point to all registries.
 * Note that the first registry to be created (this is expected to be the one created by
 * Web Server integration or by Microprofile integration) is the static instance to be used
 * by all CDI integrations (and by all using the static {@link #getRegistryFactory()}.
 * <p>
 * In Helidon SE, if you obtain the registry using {@link #getInstance()} or {@link #getInstance(io.helidon.config.Config)}
 *  the configuration is not used until the first time a registry is obtained.
 *  Currently the {@link Type#BASE} registry is lazily instantiated.
 */
// this class is not immutable, as we may need to update registries with configuration post creation
// see Github issue #360
public final class RegistryFactory {
    private static RegistryFactory staticInstance = null;

    private final EnumMap<Type, Registry> registries = new EnumMap<>(Type.class);
    private final EnumMap<Type, Registry> publicRegistries = new EnumMap<>(Type.class);
    private final AtomicReference<Config> config;

    private RegistryFactory(Config config) {
        Registry registry = Registry.create(Type.APPLICATION);
        registries.put(Type.APPLICATION, registry);
        publicRegistries.put(Type.APPLICATION, registry);

        registry = Registry.create(Type.VENDOR);
        registries.put(Type.VENDOR, registry);
        publicRegistries.put(Type.VENDOR, FinalRegistry.create(registry));

        this.config = new AtomicReference<>(config);
    }

    static synchronized RegistryFactory create(Config config) {
        RegistryFactory factory = new RegistryFactory(config);

        if (null == staticInstance) {
            staticInstance = factory;
        }

        return factory;
    }

    static RegistryFactory create() {
        return create(Config.empty());
    }

    /**
     * Get a supplier for registry factory. The supplier will return the first
     * instance that is created (this is assumed to be the one created by
     * user when creating a registry factory).
     * If you decide to use multiple registry factories, make sure that the first
     * one created is the one to be accessed from a static context (e.g. from CDI).
     *
     * @return supplier of registry factory (to bind as late as possible)
     */
    public static Supplier<RegistryFactory> getRegistryFactory() {
        return () -> {
            synchronized (RegistryFactory.class) {
                if (null == staticInstance) {
                    throw new IllegalStateException("You are using RegistryFactory before it was initialized, use one of "
                                                            + "the getInstance methods");
                }
                return staticInstance;
            }
        };
    }

    /**
     * Create a registry factory for systems without CDI.
     *
     * @param config configuration to load the factory config from
     * @return a new registry factory to obtain application registry (and other registries)
     * @deprecated this method is causing a race condition - it depends on order of invocations
     *              use {@link #getInstance()} or {@link #getInstance(Config)} instead.
     */
    @Deprecated
    public static RegistryFactory createSeFactory(Config config) {
        return create(config);
    }

    /**
     * Get a singleton instance of the registry factory for Helidon SE.
     *
     * @return registry factory singleton
     */
    public static synchronized RegistryFactory getInstance() {
        if (null == staticInstance) {
            create();
        }
        return staticInstance;
    }

    /**
     * Get a singleton instance of the registry factory for Helidon SE and update it with provided configuration.
     *
     * @param config configuration of the registry factory used to update behavior of the instance returned
     * @return registry factory singleton
     */
    public static synchronized RegistryFactory getInstance(Config config) {
        if (null == staticInstance) {
            return create(config);
        }

        staticInstance.update(config);
        return staticInstance;
    }

    Registry getARegistry(Type type) {
        if (type == Type.BASE) {
            ensureBase();
        }
        return registries.get(type);
    }

    /**
     * Get a registry based on its type.
     * For {@link Type#APPLICATION} returns a modifiable registry, for other types
     * returns a final registry (cannot register new metrics).
     *
     * @param type type of registry
     * @return Registry for the type defined.
     */
    public MetricRegistry getRegistry(Type type) {
        if (type == Type.BASE) {
            ensureBase();
        }
        return publicRegistries.get(type);
    }

    private void update(Config config) {
        this.config.set(config);
    }

    private synchronized void ensureBase() {
        if (null == registries.get(Type.BASE)) {
            Registry registry = BaseRegistry.create(config.get());
            registries.put(Type.BASE, registry);
            publicRegistries.put(Type.BASE, FinalRegistry.create(registry));
        }
    }
}

