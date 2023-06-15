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

package io.helidon.pico.configdriven.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesHolder;
import io.helidon.pico.api.Qualifier;
import io.helidon.pico.api.Resettable;
import io.helidon.pico.configdriven.api.ConfigBeanFactory;
import io.helidon.pico.configdriven.api.NamedInstance;

/**
 * The default implementation for {@link ConfigBeanRegistry}.
 */
@SuppressWarnings("unchecked")
class ConfigBeanRegistryImpl implements ConfigBeanRegistry, Resettable {
    static final LazyValue<ConfigBeanRegistry> CONFIG_BEAN_REGISTRY = LazyValue.create(ConfigBeanRegistryImpl::new);

    private static final System.Logger LOGGER = System.getLogger(ConfigBeanRegistryImpl.class.getName());

    private final AtomicBoolean registeredForReset = new AtomicBoolean();
    private final AtomicBoolean initializing = new AtomicBoolean();

    // map of config bean types to their factories (only for used config beans, that have a config driven service associated)
    private final Map<Class<?>, ConfigBeanFactory<?>> configBeanFactories = new ConcurrentHashMap<>();
    // map of config bean types to the config driven types
    private final Map<Class<?>, Set<Class<?>>> configDrivenByConfigBean = new ConcurrentHashMap<>();
    private final Map<Class<?>, ConfiguredServiceProvider<?, ?>> configDrivenFactories = new ConcurrentHashMap<>();
    // map of config bean types to instances (list may be empty if no instance exists)
    private final Map<Class<?>, List<NamedInstance<?>>> configBeanInstances = new ConcurrentHashMap<>();

    private CountDownLatch initialized = new CountDownLatch(1);

    ConfigBeanRegistryImpl() {
    }

    @Override
    public Map<Class<?>, List<NamedInstance<?>>> allConfigBeans() {
        return Map.copyOf(configBeanInstances);
    }

    @Override
    public boolean reset(boolean deep) {
        System.Logger.Level level = (isInitialized() && PicoServices.isDebugEnabled())
                ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
        LOGGER.log(level, "Resetting");
        configBeanFactories.clear();
        configDrivenByConfigBean.clear();
        configDrivenFactories.clear();
        configBeanInstances.clear();
        initializing.set(false);
        initialized = new CountDownLatch(1);
        registeredForReset.set(false);

        return true;
    }

    @Override
    public void bind(ConfiguredServiceProvider<?, ?> configuredServiceProvider,
                     Qualifier configuredByQualifier) {
        Objects.requireNonNull(configuredServiceProvider);
        Objects.requireNonNull(configuredByQualifier);

        if (initializing.get()) {
            throw new ConfigException("Unable to bind config post initialization: "
                                              + configuredServiceProvider.description());
        }

        Class<?> configBeanType = configuredServiceProvider.configBeanType();
        Class<?> configDrivenType = configuredServiceProvider.serviceType();

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Binding " + configDrivenType
                    + " with " + configuredByQualifier.value());
        }

        if (!configDrivenByConfigBean.computeIfAbsent(configBeanType, it -> new LinkedHashSet<>())
                .add(configDrivenType)) {
            assert (true) : "duplicate service provider initialization occurred: " + configBeanType + ", " + configDrivenType;
        }
        configDrivenFactories.put(configDrivenType, configuredServiceProvider);
        configBeanFactories.put(configBeanType, configuredServiceProvider);
    }

    @Override
    public void initialize(PicoServices ignoredPicoServices) {
        if (registeredForReset.compareAndSet(false, true)) {
            ResettableHandler.addRegistry(this);
        }
        try {
            if (initializing.getAndSet(true)) {
                // all threads should wait for the leader (and the config bean registry) to have been fully initialized
                initialized.await();
                return;
            }

            Config config = PicoServices.realizedGlobalBootStrap().config().orElse(null);
            if (config == null) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Unable to initialize - there is no config to read - be sure to initialize "
                                   + Bootstrap.class.getName() + " config prior to service activation.");
                reset(true);
                return;
            }

            LOGGER.log(System.Logger.Level.DEBUG, "Initializing");
            initialize(config);
            // we are now ready and initialized
            initialized.countDown();
        } catch (Throwable t) {
            PicoException e = new PicoServiceProviderException("Error while initializing config bean registry", t);
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage(), e);
            reset(true);
            throw e;
        }
    }

    @Override
    public boolean ready() {
        return isInitialized();
    }

    protected boolean isInitialized() {
        return (0 == initialized.getCount());
    }

    @SuppressWarnings("rawtypes")
    private void initialize(Config rootConfiguration) {
        if (configBeanFactories.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No config driven services found");
            return;
        }

        for (ConfigBeanFactory<?> beanFactory : configBeanFactories.values()) {
            Class<?> configBeanType = beanFactory.configBeanType();
            List<? extends NamedInstance<?>> configBeans = beanFactory.createConfigBeans(rootConfiguration);
            for (NamedInstance<?> configBean : configBeans) {
                configBeanInstances.computeIfAbsent(configBeanType, type -> new ArrayList<>())
                        .add(configBean);
            }

            Set<Class<?>> configDrivenTypes = configDrivenByConfigBean.get(configBeanType);
            if (configDrivenTypes == null) {
                LOGGER.log(System.Logger.Level.WARNING, "Unexpected state of config bean registry, "
                        + "config bean does not have any config driven types. Config bean type: " + configBeanType);
                continue;
            }

            // for each config driven type, create new instances for each discovered config bean
            for (Class<?> configDrivenType : configDrivenTypes) {
                ConfiguredServiceProvider<?, ?> configuredServiceProvider = configDrivenFactories.get(configDrivenType);
                if (configuredServiceProvider == null) {
                    LOGGER.log(System.Logger.Level.WARNING, "Unexpected state of config bean registry, "
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

    private static final class ResettableHandler extends PicoServicesHolder {
        @Deprecated
        private ResettableHandler() {
        }

        private static void addRegistry(Resettable resettable) {
            PicoServicesHolder.addResettable(resettable);
        }
    }
}
