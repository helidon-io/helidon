/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.testing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.ResettableHandler;
import io.helidon.inject.Services;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Supporting helper utilities unit-testing Injection Services.
 */
public class InjectionTestingSupport {
    private static LazyValue<InjectionServices> instance = lazyCreate(basicTestableConfig());

    private InjectionTestingSupport() {
    }

    /**
     * Resets all internal configuration instances, JVM global singletons, service registries, etc.
     */
    public static void resetAll() {
        Internal.reset();
    }

    /**
     * Provides a means to bind a service provider into the {@link io.helidon.inject.Services} registry.
     *
     * @param services        the services instance to bind into
     * @param serviceProvider the service provider to bind
     * @see io.helidon.inject.service.ServiceBinder
     */
    public static void bind(Services services,
                            ServiceDescriptor<?> serviceProvider) {
        services.binder().bind(serviceProvider);
    }

    /**
     * Creates a {@link InjectionServices} interface more conducive to unit and integration testing.
     *
     * @return testable services instance
     */
    public static Services testableServices() {
        return instance.get().services();
    }

    /**
     * Creates a {@link InjectionServices} interface more conducive to unit and integration testing.
     *
     * @param config the config to use
     * @return testable services instance
     * @see io.helidon.inject.InjectionConfig
     */
    public static InjectionServices testableServices(InjectionConfig config) {
        return lazyCreate(config).get();
    }

    /**
     * Basic testable configuration.
     *
     * @return testable config
     */
    public static InjectionConfig basicTestableConfig() {
        return InjectionConfig.builder()
                .permitsDynamic(true)
                .serviceLookupCaching(true)
                .build();
    }

    /**
     * Describe the provided instance or provider.
     *
     * @param providerOrInstance the instance to provider
     * @return the description of the instance
     */
    public static String toDescription(Object providerOrInstance) {
        if (providerOrInstance instanceof Optional<?> optional) {
            providerOrInstance = optional.orElse(null);
        }

        if (providerOrInstance instanceof RegistryServiceProvider<?> sp) {
            return sp.description();
        }
        return String.valueOf(providerOrInstance);
    }

    /**
     * Describe the provided instance or provider collection.
     *
     * @param coll the instance to provider collection
     * @return the description of the instance
     */
    public static List<String> toDescriptions(Collection<?> coll) {
        return coll.stream().map(InjectionTestingSupport::toDescription).collect(Collectors.toList());
    }

    private static LazyValue<InjectionServices> lazyCreate(InjectionConfig config) {
        return LazyValue.create(() -> {
            InjectionServices.configure(config);
            return InjectionServices.instance();
        });
    }

    private static class Internal extends ResettableHandler {
        public static void reset() {
            ResettableHandler.reset();
            instance = lazyCreate(basicTestableConfig());
        }
    }

}
