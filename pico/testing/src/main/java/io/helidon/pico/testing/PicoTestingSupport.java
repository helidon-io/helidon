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

package io.helidon.pico.testing;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.builder.config.spi.BasicConfigBeanRegistry;
import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.DefaultBootstrap;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.PicoServicesHolder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.services.DefaultServiceBinder;
import io.helidon.pico.spi.Resetable;

/**
 * Supporting helper utilities unit-testing Pico services.
 */
public class PicoTestingSupport {
    private static LazyValue<PicoServices> instance = lazyCreate(basicTesableConfig());

    private PicoTestingSupport() {
    }

    /**
     * Resets all internal Pico configuration instances, JVM global singletons, service registries, etc.
     */
    public static void resetAll() {
        Internal.reset();
    }

    /**
     * Provides a means to bind a service provider into the {@link io.helidon.pico.Services} registry.
     *
     * @param picoServices the pico services instance to bind into
     * @param serviceProvider the service provider to bind
     * @see io.helidon.pico.ServiceBinder
     */
    public static void bind(
            PicoServices picoServices,
            ServiceProvider<?> serviceProvider) {
        DefaultServiceBinder binder = DefaultServiceBinder.create(picoServices, PicoTestingSupport.class.getSimpleName(), true);
        binder.bind(serviceProvider);
    }

    /**
     * Creates a {@link io.helidon.pico.PicoServices} interface more conducive to unit and integration testing.
     *
     * @return testable services instance
     */
    public static PicoServices testableServices() {
        return instance.get();
    }

    /**
     * Creates a {@link io.helidon.pico.PicoServices} interface more conducive to unit and integration testing.
     *
     * @param config the config to use
     * @return testable services instance
     * @see io.helidon.pico.PicoServicesConfig
     */
    public static PicoServices testableServices(
            Config config) {
        return lazyCreate(config).get();
    }

    /**
     * Basic testable configuration.
     *
     * @return testable config
     */
    public static Config basicTesableConfig() {
        return Config.create(
                    ConfigSources.create(
                            Map.of(
                                    PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true",
                                    PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_SERVICE_LOOKUP_CACHING, "true"),
                            "config-1"));
    }

    /**
     * Describe the provided instance or provider.
     *
     * @param providerOrInstance the instance to provider
     * @return the description of the instance
     */
    public static String toDescription(
            Object providerOrInstance) {
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
    public static List<String> toDescriptions(
            Collection<?> coll) {
        return coll.stream().map(PicoTestingSupport::toDescription).collect(Collectors.toList());
    }

    private static class Internal extends PicoServicesHolder {
        public static void reset() {
            PicoServicesHolder.reset();
            instance = lazyCreate(basicTesableConfig());

            BasicConfigBeanRegistry registry = ConfigBeanRegistryHolder.configBeanRegistry().orElse(null);
            if (registry instanceof Resetable) {
                ((Resetable) registry).reset(true);
            }
        }
    }

    private static LazyValue<PicoServices> lazyCreate(
            Config config) {
        return LazyValue.create(() -> {
            PicoServices.globalBootstrap(DefaultBootstrap.builder().config(config).build());
            return PicoServices.picoServices().orElseThrow();
        });
    }

}
