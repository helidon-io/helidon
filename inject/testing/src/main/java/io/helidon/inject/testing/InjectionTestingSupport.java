/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.InjectionServicesConfig;
import io.helidon.inject.api.InjectionServicesHolder;
import io.helidon.inject.api.ServiceBinder;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.runtime.ServiceBinderDefault;

/**
 * Supporting helper utilities unit-testing Injection Services.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
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
     * Provides a means to bind a service provider into the {@link Services} registry.
     *
     * @param injectionServices the services instance to bind into
     * @param serviceProvider   the service provider to bind
     * @see ServiceBinder
     */
    public static void bind(InjectionServices injectionServices,
                            ServiceProvider<?> serviceProvider) {
        ServiceBinderDefault binder = ServiceBinderDefault.create(injectionServices, InjectionTestingSupport.class.getSimpleName(), true);
        binder.bind(serviceProvider);
    }

    /**
     * Creates a {@link InjectionServices} interface more conducive to unit and integration testing.
     *
     * @return testable services instance
     */
    public static InjectionServices testableServices() {
        return instance.get();
    }

    /**
     * Creates a {@link InjectionServices} interface more conducive to unit and integration testing.
     *
     * @param config the config to use
     * @return testable services instance
     * @see InjectionServicesConfig
     */
    public static InjectionServices testableServices(Config config) {
        return lazyCreate(config).get();
    }

    /**
     * Basic testable configuration.
     *
     * @return testable config
     */
    public static Config basicTestableConfig() {
        return Config.builder(
                        ConfigSources.create(
                                Map.of("inject.permits-dynamic", "true",
                                        "inject.service-lookup-caching", "true"),
                                "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    /**
     * Describe the provided instance or provider.
     *
     * @param providerOrInstance the instance to provider
     * @return the description of the instance
     */
    public static String toDescription(Object providerOrInstance) {
        if (providerOrInstance instanceof Optional) {
            providerOrInstance = ((Optional<?>) providerOrInstance).orElse(null);
        }

        if (providerOrInstance instanceof ServiceProvider) {
            return ((ServiceProvider<?>) providerOrInstance).description();
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

    private static LazyValue<InjectionServices> lazyCreate(Config config) {
        return LazyValue.create(() -> {
            InjectionServices.globalBootstrap(Bootstrap.builder().config(config).build());
            return InjectionServices.injectionServices().orElseThrow();
        });
    }

    @SuppressWarnings("deprecation")
    private static class Internal extends InjectionServicesHolder {
        public static void reset() {
            InjectionServicesHolder.reset();
            instance = lazyCreate(basicTestableConfig());
        }
    }

}
