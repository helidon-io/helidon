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

package io.helidon.service.maven.plugin;

import java.lang.System.Logger.Level;
import java.util.List;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Lookup;

import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_CONFIG;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_REGISTRY;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_REGISTRY_MANAGER;

class WrappedServices implements AutoCloseable {
    private final ClassLoader classLoader;
    private final CodegenLogger logger;
    private final Class<?> injectionServicesType;
    private final Class<?> servicesType;
    private final Object injectionServices;
    private final Object services;

    private WrappedServices(ClassLoader classLoader,
                            CodegenLogger logger,
                            Class<?> injectionServicesType,
                            Class<?> servicesType,
                            Object injectionServices,
                            Object services) {
        this.classLoader = classLoader;
        this.logger = logger;
        this.injectionServicesType = injectionServicesType;
        this.servicesType = servicesType;
        this.injectionServices = injectionServices;
        this.services = services;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static WrappedServices create(ClassLoader classLoader, CodegenLogger logger, boolean useApplications) {
        try {
            /*
            Phase.GATHERING_DEPENDENCIES
             */

            Activator.Phase limitPhase = Activator.Phase.ACTIVATION_STARTING;

            /*
            InjectionConfig.builder()....build();
             */
            Class<?> injectConfigType = classLoader.loadClass(INJECT_CONFIG.fqName());
            Object injectConfigBuilder = injectConfigType.getMethod("builder")
                    .invoke(null);
            Class<?> injectConfigBuilderType = injectConfigBuilder.getClass();

            injectConfigBuilderType.getMethod("useApplication", boolean.class)
                    .invoke(injectConfigBuilder, useApplications);
            injectConfigBuilderType.getMethod("limitRuntimePhase", Activator.Phase.class)
                    .invoke(injectConfigBuilder, limitPhase);
            Object injectionConfig = injectConfigBuilderType.getMethod("build")
                    .invoke(injectConfigBuilder);

            /*
            InjectRegistryManager registryManager = InjectRegistryManager.create(injectionConfig)
            InjectRegistry services = injectionServices.registry();
             */
            Class<?> injectionServicesType = classLoader.loadClass(INJECT_REGISTRY_MANAGER.fqName());
            Object injectionServices = injectionServicesType.getMethod("create", injectConfigType)
                    .invoke(null, injectionConfig);
            Class<?> servicesType = classLoader.loadClass(INJECT_REGISTRY.fqName());
            Object services = injectionServicesType.getMethod("registry")
                    .invoke(injectionServices);

            return new WrappedServices(classLoader,
                                       logger,
                                       injectionServicesType,
                                       servicesType,
                                       injectionServices,
                                       services);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException(
                    "Failed to invoke Service registry related methods in user's application class loader using reflection",
                    e);
        }
    }

    @Override
    public void close() {
        try {
            injectionServicesType.getMethod("shutdown")
                    .invoke(injectionServices);
        } catch (ReflectiveOperationException e) {
            logger.log(CodegenEvent.builder()
                               .level(Level.WARNING)
                               .message("Failed to shutdown services used from Maven plugin")
                               .throwable(e)
                               .build());
        }
    }

    List<InjectServiceInfo> all() {
        return all(Lookup.EMPTY);
    }

    @SuppressWarnings("unchecked")
    List<InjectServiceInfo> all(Lookup lookup) {
        try {
            // retrieves all the services in the registry
            return (List<InjectServiceInfo>) servicesType.getMethod("lookupServices", Lookup.class)
                    .invoke(services, lookup);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get providers from service registry using reflection", e);
        }
    }

    InjectServiceInfo get(Lookup lookup) {
        List<InjectServiceInfo> services = all(lookup);
        if (services.size() == 1) {
            return services.getFirst();
        }
        if (services.isEmpty()) {
            throw new CodegenException("Expected that service registry contains service: " + lookup + ", yet none was found");
        }

        throw new CodegenException("Expected that service registry contains service: " + lookup
                                           + ", yet more than one was found: " + services);
    }
}
