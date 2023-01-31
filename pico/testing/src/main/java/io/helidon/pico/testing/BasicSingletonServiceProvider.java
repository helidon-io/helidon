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

package io.helidon.pico.testing;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;

import io.helidon.pico.InjectionException;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.services.AbstractServiceProvider;

/**
 * Creates a simple reflection based service provider - for testing purposes only!
 *
 * @param <T> the service type
 */
public class BasicSingletonServiceProvider<T> extends AbstractServiceProvider<T> {
    private final Class<T> serviceType;

    private BasicSingletonServiceProvider(
            Class<T> serviceType,
            ServiceInfo serviceInfo) {
        this.serviceType = serviceType;
        serviceInfo(serviceInfo);
    }

    /**
     * Generates a service provider eligible for binding into the service registry with the following proviso:
     * <ul>
     * <li>The service type will be of {@code jakarta.inject.Singleton} scope</li>
     * <li>The service type will be created reflectively, and will expect to have an empty constructor</li>
     * <li>The service type will not be able to provide its dependencies, nor will it be able to accept injection</li>
     * <li>The service type will not be able to participate in lifecycle -
     * {@link io.helidon.pico.PostConstructMethod} or {@link io.helidon.pico.PreDestroyMethod}</li>
     * </ul>
     * Note: Generally it is encouraged for users to rely on the annotation processors and other built on compile-time
     * tooling to generate the appropriate service providers and modules. This method is an alternative to that
     * mechanism and therefore is discouraged from production use.  This method is not used in normal processing by
     * the reference pico provider implementation.
     *
     * @param serviceType       the service type
     * @param siBasics          the service info basic descriptor, or null to generate a default (empty) placeholder
     * @param <T> the class of the service type
     *
     * @return the service provider capable of being bound to the services registry
     * @see io.helidon.pico.testing.PicoTestingSupport#bind(io.helidon.pico.PicoServices, io.helidon.pico.ServiceProvider)
     */
    public static <T> BasicSingletonServiceProvider<T> create(
            Class<T> serviceType,
            ServiceInfoBasics siBasics) {
        Objects.requireNonNull(serviceType);
        Objects.requireNonNull(siBasics);

        if (!serviceType.getName().equals(siBasics.serviceTypeName())) {
            throw new IllegalArgumentException("mismatch in service types");
        }

        return new BasicSingletonServiceProvider<>(serviceType, ServiceInfo.toBuilder(siBasics).build());
    }

    @Override
    public boolean isCustom() {
        return true;
    }

    @Override
    protected T createServiceProvider(
            Map<String, Object> deps) {
        try {
            Constructor<T> ctor = serviceType.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new InjectionException("failed to create instance: " + this, e, this);
        }
    }

}
