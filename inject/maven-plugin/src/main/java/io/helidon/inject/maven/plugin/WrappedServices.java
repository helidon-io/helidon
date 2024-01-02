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

package io.helidon.inject.maven.plugin;

import java.lang.System.Logger.Level;
import java.util.List;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.common.config.Config;
import io.helidon.inject.codegen.InjectCodegenTypes;
import io.helidon.inject.service.Lookup;

class WrappedServices implements AutoCloseable {
    private final ClassLoader classLoader;
    private final CodegenLogger logger;
    private final Class<?> injectionServicesType;
    private final Class<?> servicesType;
    private final Class<?> providerServicesType;
    private final Class<?> registryServiceProviderType;
    private final Class<?> serviceProviderBindableType;
    private final Class<?> injectionResolverType;
    private final Object injectionServices;
    private final Object services;
    private final Object providerServices;

    private WrappedServices(ClassLoader classLoader,
                            CodegenLogger logger,
                            Class<?> injectionServicesType,
                            Class<?> servicesType,
                            Class<?> providerServicesType,
                            Class<?> registryServiceProviderType,
                            Class<?> serviceProviderBindableType,
                            Class<?> injectionResolverType,
                            Object injectionServices,
                            Object services,
                            Object providerServices) {
        this.classLoader = classLoader;
        this.logger = logger;
        this.injectionServicesType = injectionServicesType;
        this.servicesType = servicesType;
        this.providerServicesType = providerServicesType;
        this.registryServiceProviderType = registryServiceProviderType;
        this.serviceProviderBindableType = serviceProviderBindableType;
        this.injectionResolverType = injectionResolverType;
        this.injectionServices = injectionServices;
        this.services = services;
        this.providerServices = providerServices;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static WrappedServices create(ClassLoader classLoader, CodegenLogger logger, boolean useApplications) {
        try {
            /*
            Phase.GATHERING_DEPENDENCIES
             */
            Class injectPhaseType = classLoader.loadClass(InjectCodegenTypes.INJECT_PHASE.fqName());
            Object gatheringDependenciesPhase = Enum.valueOf(injectPhaseType, "GATHERING_DEPENDENCIES");

            /*
            InjectionConfig.builder()....build();
             */
            Class<?> injectConfigType = classLoader.loadClass(InjectCodegenTypes.INJECTION_CONFIG.fqName());
            Object injectConfigBuilder = injectConfigType.getMethod("builder")
                    .invoke(null);
            Class<?> injectConfigBuilderType = injectConfigBuilder.getClass();

            injectConfigBuilderType.getMethod("useApplication", boolean.class)
                    .invoke(injectConfigBuilder, useApplications);
            injectConfigBuilderType.getMethod("permitsDynamic", boolean.class)
                    .invoke(injectConfigBuilder, true);
            injectConfigBuilderType.getMethod("limitRuntimePhase", injectPhaseType)
                    .invoke(injectConfigBuilder, gatheringDependenciesPhase);
            injectConfigBuilderType.getMethod("serviceConfig", Config.class)
                    .invoke(injectConfigBuilder, Config.empty());
            Object injectionConfig = injectConfigBuilderType.getMethod("build")
                    .invoke(injectConfigBuilder);

            /*
            InjectionServices injectionServices = InjectionServices.create(injectionConfig)
            Services services = injectionServices.services();
             */
            Class<?> injectionServicesType = classLoader.loadClass(InjectCodegenTypes.INJECT_INJECTION_SERVICES.fqName());
            Object injectionServices = injectionServicesType.getMethod("create", injectConfigType)
                    .invoke(null, injectionConfig);
            Class<?> servicesType = classLoader.loadClass(InjectCodegenTypes.INJECT_SERVICES.fqName());
            Object services = injectionServicesType.getMethod("services")
                    .invoke(injectionServices);
            Class<?> providerServicesType = classLoader.loadClass(InjectCodegenTypes.INJECT_PROVIDER_SERVICES.fqName());
            Object providerServices = servicesType.getMethod("serviceProviders")
                    .invoke(services);

            return new WrappedServices(classLoader,
                                       logger,
                                       injectionServicesType,
                                       servicesType,
                                       providerServicesType,
                                       classLoader.loadClass(InjectCodegenTypes.REGISTRY_SERVICE_PROVIDER.fqName()),
                                       classLoader.loadClass(InjectCodegenTypes.INJECT_SERVICE_PROVIDER_BINDABLE.fqName()),
                                       classLoader.loadClass(InjectCodegenTypes.INJECT_INJECTION_RESOLVER.fqName()),
                                       injectionServices,
                                       services,
                                       providerServices);
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

    List<WrappedProvider> all() {
        return all(Lookup.EMPTY);
    }

    @SuppressWarnings("unchecked")
    List<WrappedProvider> all(Lookup lookup) {
        try {
            // retrieves all the services in the registry
            List<Object> providers = (List<Object>) providerServicesType.getMethod("all", Lookup.class)
                    .invoke(providerServices, lookup);

            return providers.stream()
                    .map(providerObject -> new WrappedProvider(classLoader,
                                                               servicesType,
                                                               registryServiceProviderType,
                                                               serviceProviderBindableType,
                                                               injectionResolverType,
                                                               services,
                                                               providerObject))
                    .toList();
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get providers from service registry using reflection", e);
        }
    }

    WrappedProvider get(Lookup lookup) {
        try {
            // retrieves all the services in the registry
            Object provider = providerServicesType.getMethod("get", Lookup.class)
                    .invoke(providerServices, lookup);

            return new WrappedProvider(classLoader,
                                       servicesType,
                                       registryServiceProviderType,
                                       serviceProviderBindableType,
                                       injectionResolverType,
                                       services,
                                       provider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get providers from service registry using reflection", e);
        }
    }
}
