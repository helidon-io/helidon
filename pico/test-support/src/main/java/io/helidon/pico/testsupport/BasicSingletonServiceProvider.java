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

import java.lang.reflect.Constructor;
import java.util.Map;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionException;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.ext.AbstractServiceProvider;

/**
 * Creates a simple reflection based service provider - for testing purposes mainly.
 *
 * @param <T> the service type
 */
public class BasicSingletonServiceProvider<T> extends AbstractServiceProvider<T> {
    private final Class<T> serviceType;

    /**
     * Ctor.
     *
     * @param serviceType the service type
     * @param siBasics the basic service info describing the service.eibcccu
     */
    protected BasicSingletonServiceProvider(Class<T> serviceType, ServiceInfoBasics siBasics) {
        this.serviceType = serviceType;
        DefaultServiceInfo serviceInfo = DefaultServiceInfo.toServiceInfoFromClass(serviceType, siBasics);
        setServiceInfo(serviceInfo);
        assert (serviceInfo.serviceTypeName().equals(serviceType.getName()));
    }

    @Override
    public boolean isCustom() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected T createServiceProvider(Map<String, Object> deps) {
        try {
            Constructor<T> ctor = serviceType.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new InjectionException("failed to create instance for " + this, e, this);
        }
    }

    /**
     * Generates a service provider eligible for binding into the service registry with the following proviso:
     * <li>The service type will be of {@link jakarta.inject.Singleton} scope</li>
     * <li>The service type will be created reflectively, and will expect to have an empty constructor</li>
     * <li>The service type will not be able to provide its dependencies, nor will it be able to accept injection</li>
     * <li>The service type will not be able to participate in lifecycle -
     * {@link io.helidon.pico.PostConstructMethod} or {@link io.helidon.pico.PreDestroyMethod}</li>
     * <p/>
     *
     * Note: Generally it is encouraged for users to rely on the annotation processors and other built in compile-
     * time tooling to generate the appropriate service providers and modules. This method is an alternative to that
     * mechanism and therefore is discouraged from production use.  This method is not used in normal processing by
     * the reference pico provider implementation.
     *
     * @param serviceType       the service type
     * @param serviceInfoBasics the service info basic descriptor, or null to generate a default (empty) placeholder
     * @param <T> the class of the service type
     *
     * @return the service provider that is eligible to be bound via
     * {@link io.helidon.pico.ServiceBinder#bind(io.helidon.pico.ServiceProvider)}.
     */
    public static <T> ServiceProvider<T> createBasicServiceProvider(Class<T> serviceType, ServiceInfoBasics serviceInfoBasics) {
        return new BasicSingletonServiceProvider<>(serviceType, serviceInfoBasics);
    }
}
