/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.types.TypeName;
import io.helidon.inject.InjectionException;
import io.helidon.inject.InjectionServiceProviderException;
import io.helidon.inject.Resettable;
import io.helidon.inject.ResettableHandler;
import io.helidon.inject.configdriven.service.ConfigBeanFactory;
import io.helidon.inject.configdriven.service.NamedInstance;
import io.helidon.inject.service.Qualifier;

/**
 * The default implementation for {@link ConfigBeanRegistry}.
 */
@SuppressWarnings("unchecked")
class ConfigBeanRegistryImpl implements ConfigBeanRegistry, Resettable {
    private static final System.Logger LOGGER = System.getLogger(ConfigBeanRegistryImpl.class.getName());

    private final AtomicBoolean registeredForReset = new AtomicBoolean();
    private final AtomicBoolean initializing = new AtomicBoolean();

    // map of config bean types to their factories (only for used config beans, that have a config driven service associated)
    private final Map<TypeName, ConfigBeanFactory<?>> configBeanFactories = new ConcurrentHashMap<>();
    // map of config bean types to the config driven types
    private final Map<TypeName, Set<TypeName>> configDrivenByConfigBean = new ConcurrentHashMap<>();
    private final Map<TypeName, ConfiguredServiceProvider<?, ?>> configDrivenFactories = new ConcurrentHashMap<>();
    // map of config bean types to instances (list may be empty if no instance exists)
    private final Map<TypeName, List<NamedInstance<?>>> configBeanInstances = new ConcurrentHashMap<>();

    private CountDownLatch initialized = new CountDownLatch(1);

    ConfigBeanRegistryImpl() {
    }

    @Override
    public Map<TypeName, List<NamedInstance<?>>> allConfigBeans() {
        return Map.copyOf(configBeanInstances);
    }

    @Override
    public void reset(boolean deep) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Resetting");
        }
        configBeanFactories.clear();
        configDrivenByConfigBean.clear();
        configDrivenFactories.clear();
        configBeanInstances.clear();
        initializing.set(false);
        initialized = new CountDownLatch(1);
        registeredForReset.set(false);
    }

    @Override
    public boolean ready() {
        return isInitialized();
    }

    /**
     * Binds a {@link io.helidon.inject.configdriven.ConfiguredServiceProvider} to the
     * {@link io.helidon.inject.configdriven.service.ConfigBean} annotation it is configured by.
     *
     * @param configuredServiceProvider the configured service provider
     * @param configuredByQualifier     the qualifier associated with the
     *                                  {@link io.helidon.inject.configdriven.service.ConfigBean}
     * @throws io.helidon.common.config.ConfigException if the bind operation encountered an error
     */
    void bind(ConfiguredServiceProvider<?, ?> configuredServiceProvider,
              Qualifier configuredByQualifier) {
        Objects.requireNonNull(configuredServiceProvider);
        Objects.requireNonNull(configuredByQualifier);

        if (initializing.get()) {
            throw new ConfigException("Unable to bind config post initialization: "
                                              + configuredServiceProvider.description());
        }

        TypeName configBeanType = configuredServiceProvider.configBeanType();
        TypeName configDrivenType = configuredServiceProvider.serviceType();

        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Binding " + configDrivenType
                    + " with " + configuredByQualifier.value());
        }

        if (!configDrivenByConfigBean.computeIfAbsent(configBeanType, it -> new LinkedHashSet<>())
                .add(configDrivenType)) {
            assert (true) : "duplicate service provider initialization occurred: " + configBeanType + ", " + configDrivenType;
        }
        configDrivenFactories.put(configDrivenType, configuredServiceProvider);
        configBeanFactories.put(configBeanType, configuredServiceProvider);
    }

    void initialize(Config config) {
        Objects.requireNonNull(config);

        if (registeredForReset.compareAndSet(false, true)) {
            CbrResettableHandler.addRegistry(this);
        }
        try {
            if (initializing.getAndSet(true)) {
                // all threads should wait for the leader (and the config bean registry) to have been fully initialized
                initialized.await();
                return;
            }

            LOGGER.log(Level.DEBUG, "Initializing");
            doInitialize(config);
            // we are now ready and initialized
            initialized.countDown();
        } catch (InjectionException e) {
            throw e;
        } catch (Throwable t) {
            LOGGER.log(Level.ERROR, "Failed to initialize ConfigDrivenRegistry", t);
            throw new InjectionServiceProviderException("Error while initializing config bean registry", t);
        }
    }

    protected boolean isInitialized() {
        return (0 == initialized.getCount());
    }

    @SuppressWarnings("rawtypes")
    private void doInitialize(Config rootConfiguration) {
        if (configBeanFactories.isEmpty()) {
            LOGGER.log(Level.DEBUG, "No config driven services found");
            return;
        }

        for (ConfigBeanFactory<?> beanFactory : configBeanFactories.values()) {
            TypeName configBeanType = beanFactory.configBeanType();
            List<? extends NamedInstance<?>> configBeans = beanFactory.createConfigBeans(rootConfiguration);
            for (NamedInstance<?> configBean : configBeans) {
                configBeanInstances.computeIfAbsent(configBeanType, type -> new ArrayList<>())
                        .add(configBean);
            }

            Set<TypeName> configDrivenTypes = configDrivenByConfigBean.get(configBeanType);
            if (configDrivenTypes == null) {
                LOGGER.log(Level.WARNING, "Unexpected state of config bean registry, "
                        + "config bean does not have any config driven types. Config bean type: " + configBeanType);
                continue;
            }

            // for each config driven type, create new instances for each discovered config bean
            for (TypeName configDrivenType : configDrivenTypes) {
                ConfiguredServiceProvider<?, ?> configuredServiceProvider = configDrivenFactories.get(configDrivenType);
                if (configuredServiceProvider == null) {
                    LOGGER.log(Level.WARNING, "Unexpected state of config bean registry, "
                            + "config driven does not have associated service provider. Config bean type: "
                            + configBeanType);
                    continue;
                }
                for (NamedInstance configBean : configBeans) {
                    // now we have a tuple of a config bean instance and a config driven service provider
                    configuredServiceProvider.registerConfigBean(configBean);
                }
            }
        }
    }

    private static final class CbrResettableHandler extends ResettableHandler {
        @Deprecated
        private CbrResettableHandler() {
        }

        private static void addRegistry(Resettable resettable) {
            ResettableHandler.addResettable(resettable);
        }
    }
}
