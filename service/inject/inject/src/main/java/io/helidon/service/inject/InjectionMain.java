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

package io.helidon.service.inject;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.TypeName;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

/**
 * Common ancestor for generated main classes.
 * <p>
 * The following methods are code generated to the {@code ApplicationMain} class by the Maven plugin.
 * If you do not want the generated code to be used, simply implement those methods in your custom Main class,
 * and they will NOT be generated.
 * When not using Maven plugin, the generated class will have no-op implementations nad service discovery will be enabled.
 * <ul>
 *     <li>{@link #serviceDescriptors(io.helidon.service.inject.InjectConfig.Builder)} - registers all service descriptors
 *      in the current application; to customize the config builder, you can use
 *      {@link #beforeServiceDescriptors(io.helidon.service.inject.InjectConfig.Builder)} and
 *      {@link #afterServiceDescriptors(io.helidon.service.inject.InjectConfig.Builder)} methods</li>
 *     <li>{@link #discoverServices()} - set to {@code false} when using Maven plugin, to avoid all reflection</li>
 * </ul>
 */
public abstract class InjectionMain {
    /*
    A change in this class requires change in ApplicationMainGenerator!
     */

    private static final System.Logger LOGGER = System.getLogger(InjectionMain.class.getName());

    static {
        LogConfig.initClass();
    }

    /**
     * Default constructor with no side effects.
     */
    protected InjectionMain() {
        LogConfig.configureRuntime();
    }

    /**
     * Create a service descriptor for a Java {@link java.util.ServiceLoader} based service.
     *
     * @param providerInterface provider interface of the service
     * @param implType          provider implementation
     * @param instanceSupplier  supplier of a new instance of the service implementation (to allow avoiding reflection)
     * @param weight            weight assigned to the service
     * @param <T>               type of the service implementation
     * @return a new service descriptor of the service
     */
    protected static <T> ServiceDescriptor<?> serviceLoader(TypeName providerInterface,
                                                            Class<T> implType,
                                                            Supplier<T> instanceSupplier,
                                                            double weight) {
        return ServiceLoader__ServiceDescriptor.create(providerInterface, implType, instanceSupplier, weight);
    }

    /**
     * Method that handles the startup sequence of an application.
     * This method is expected to be code generated.
     * <p>
     * The following sequence is implemented:
     * <ol>
     *     <li>{@link #configBuilder(String[])} to prepare the configuration builder from
     *              {@link io.helidon.common.config.Config}</li>
     *     <li>{@link #beforeServiceDescriptors(io.helidon.service.inject.InjectConfig.Builder)} to update the builder</li>
     *     <li>{@link #serviceDescriptors(io.helidon.service.inject.InjectConfig.Builder)} for code generated setup</li>
     *     <li>{@link #afterServiceDescriptors(io.helidon.service.inject.InjectConfig.Builder)} to update the builder</li>
     *     <li>{@link #init(InjectConfig)} to initialize the service registry</li>
     * </ol>
     *
     * @param arguments command line arguments
     */
    protected void start(String[] arguments) {
        var config = configBuilder(arguments);
        beforeServiceDescriptors(config);
        serviceDescriptors(config);
        afterServiceDescriptors(config);
        var registry = init(config.build());
        afterInit(registry);
    }

    /**
     * Method that registers all service descriptors.
     * This method is expected to be code generated.
     *
     * @param configBuilder in progress config builder
     */
    protected abstract void serviceDescriptors(InjectConfig.Builder configBuilder);

    /**
     * Create a config builder from command line arguments.
     * This method will create default configuration (but not register it as a global config, so we still use the registry
     * to create a config instance), configure the {@link io.helidon.service.inject.InjectConfig.Builder} from it,
     * then disable discovery of services.
     *
     * @param arguments command line arguments
     * @return a new config builder
     */
    protected InjectConfig.Builder configBuilder(String[] arguments) {
        Config config;
        if (GlobalConfig.configured()) {
            config = GlobalConfig.config();
        } else {
            config = Config.create();
        }

        return InjectConfig.builder()
                .config(config.get("registry"))
                .discoverServices(discoverServices())
                .discoverServicesFromServiceLoader(discoverServices());
    }

    /**
     * Whether to discover services from classpath and from service loader.
     * Defaults to {@code false}, should be overridden when
     * {@link #serviceDescriptors(io.helidon.service.inject.InjectConfig.Builder)}
     * does not configure all services.
     *
     * @return whether to discover services
     */
    protected boolean discoverServices() {
        return false;
    }

    /**
     * Called before service descriptors are configured in the generated main class.
     * This method is invoked from generated {@link #start(String[])}.
     *
     * @param configBuilder in-progress config builder
     */
    protected void beforeServiceDescriptors(InjectConfig.Builder configBuilder) {
    }

    /**
     * Called after service descriptors are configured in the generated main class, before the registry is initialized.
     * This method is invoked from generated {@link #start(String[])}
     *
     * @param configBuilder in-progress config builder
     */
    protected void afterServiceDescriptors(InjectConfig.Builder configBuilder) {
    }

    /**
     * Initialize the service registry, register it for shutdown during process shutdown, and lookup all
     * startup services.
     *
     * @param config configuration of the Inject service registry
     * @return the inject registry created within this method
     */
    protected InjectRegistry init(InjectConfig config) {
        InjectRegistryManager manager = InjectRegistryManager.create(config);
        InjectRegistry registry = manager.registry();
        InjectStartupProvider.registerShutdownHandler(manager);

        GlobalServiceRegistry.registry(registry);

        double maxRunLevel = maxRunLevel(config, registry);
        for (double runLevel : runLevels(config, registry)) {
            if (runLevel <= maxRunLevel) {
                List<Object> all = registry.all(Lookup.builder()
                                                        .addScope(Injection.Singleton.TYPE)
                                                        .runLevel(runLevel)
                                                        .build());
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.DEBUG, "Starting services in run level: " + runLevel + ": ");
                    for (Object o : all) {
                        LOGGER.log(Level.DEBUG, "\t" + o);
                    }
                } else if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.TRACE, "Starting services in run level: " + runLevel);
                }
            }
        }

        return registry;
    }

    /**
     * Maximal run level to initialize.
     * The default startup sequence will go through each
     * {@link #runLevels(InjectConfig, io.helidon.service.inject.api.InjectRegistry) run level} up to (and including) the value
     * returned by this method.
     *
     * @param config injection config
     * @param registry registry instance
     * @return maximal run level to initialize
     */
    protected double maxRunLevel(InjectConfig config,
                                 InjectRegistry registry) {
        return config.maxRunLevel();
    }

    /**
     * Run levels that should be initialized at startup.
     * Default implementation initializes all declared run levels (services with explicit
     * {@link io.helidon.service.inject.api.Injection.RunLevel} annotation).
     *
     * @param config injection config
     * @param registry registry instance
     * @return array of doubles that represent run levels to initialize, will be used in the order provided here
     */
    protected double[] runLevels(InjectConfig config,
                                 InjectRegistry registry) {
        // child classes will have this method code generated at build time
        List<Double> runLevelList = registry.lookupServices(Lookup.EMPTY)
                .stream()
                .map(InjectServiceInfo::runLevel)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
        double[] result = new double[runLevelList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = runLevelList.get(i);
        }
        return result;
    }

    /**
     * Allows to query the registry after the service is started.
     *
     * @param registry inject service registry
     */
    protected void afterInit(InjectRegistry registry) {
    }
}
