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

import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.TypeName;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

/**
 * Common ancestor for generated main classes.
 */
public abstract class ApplicationMain {
    static {
        LogConfig.configureRuntime();
    }

    /**
     * Default constructor with no side effects.
     */
    protected ApplicationMain() {
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
        init(config.build());
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
        boolean configured = GlobalConfig.configured();
        Config config = GlobalConfig.config();
        if (!configured) {
            // reset to empty
            GlobalConfig.config(() -> null, true);
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
     */
    protected void init(InjectConfig config) {
        InjectRegistryManager manager = InjectRegistryManager.create(config);
        InjectRegistry registry = manager.registry();
        InjectStartupProvider.registerShutdownHandler(manager);

        GlobalServiceRegistry.registry(registry);
        registry.all(Lookup.builder()
                             .runLevel(Injection.RunLevel.STARTUP)
                             .build());
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
    protected static <T> GeneratedService.Descriptor<?> serviceLoader(TypeName providerInterface,
                                                                      Class<T> implType,
                                                                      Supplier<T> instanceSupplier,
                                                                      double weight) {
        return ServiceLoader__ServiceDescriptor.create(providerInterface, implType, instanceSupplier, weight);
    }
}
