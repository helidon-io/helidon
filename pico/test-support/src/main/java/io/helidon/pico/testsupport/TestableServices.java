/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.testsupport;

import java.util.List;

import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.ExtendedServices;
import io.helidon.pico.spi.ext.Resetable;

/**
 * A version of {@link java.security.Provider.Service} that is conducive to unit testing.
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public class TestableServices implements Services, Resetable, ExtendedServices {

    final InternalDefaultServices services;
    final PicoServicesConfig config;

    /**
     * The default constructor.
     */
    public TestableServices() {
        this(new TestablePicoServicesConfig());
    }

    /**
     * The constructor taking a configuration.
     *
     * @param config the config
     */
    public TestableServices(PicoServicesConfig config) {
        this.config = config;
        this.services = new InternalDefaultServices(config);
    }

    /**
     * Bind a module.
     *
     * @param picoServices other pico services (including config, etc.)
     * @param module the module to bind
     */
    public void bind(PicoServices picoServices, Module module) {
        services.bind(picoServices, module);
    }

    /**
     * Bind a service provider.
     *
     * @param picoServices other pico services (including config, etc.)
     * @param serviceProvider the service provider to bind
     */
    public void bind(PicoServices picoServices, ServiceProvider<?> serviceProvider) {
        services.bind(picoServices, serviceProvider);
    }

    @Override
    public void reset() {
        services.reset();
    }

    @Override
    public <T> ServiceProvider<T> lookupFirst(Class<T> type, String name, boolean expected) {
        return services.lookupFirst(type, name, expected);
    }

    @Override
    public <T> ServiceProvider<T> lookupFirst(ServiceInfo criteria, boolean expected) {
        return services.lookupFirst(criteria, expected);
    }

    @Override
    public <T> List<ServiceProvider<T>> lookup(Class<T> type) {
        return services.lookup(type);
    }

    @Override
    public <T> List<ServiceProvider<T>> lookup(ServiceInfo criteria) {
        return services.lookup(criteria);
    }

    @Override
    public <T> List<ServiceProvider<T>> lookup(ServiceInfo criteria, boolean expected) {
        return services.lookup(criteria, expected);
    }

    @Override
    public int lookupCount() {
        return services.lookupCount();
    }

    @Override
    public int cacheLookupCount() {
        return services.cacheLookupCount();
    }

    @Override
    public int cacheHitCount() {
        return services.cacheHitCount();
    }
}
