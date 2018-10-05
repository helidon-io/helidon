/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Utility methods to help with loading of java services (mostly SPI related).
 */
public final class SpiHelper {
    private static final Map<Class<?>, List<ServiceWrapper>> SERVICES = new ConcurrentHashMap<>();

    private SpiHelper() {
    }

    /**
     * Loads the first service implementation or throw an exception if nothing found.
     *
     * @param serviceInterface the service class to load
     * @param <T>              service type
     * @return the loaded service
     * @throws IllegalStateException if none implementation found
     */
    public static <T> T loadSpi(Class<T> serviceInterface) {

        List<T> instances = getServiceInstances(serviceInterface);

        if (instances.isEmpty()) {
            throw new IllegalStateException("No implementation found for SPI: " + serviceInterface.getName());
        }

        return instances.iterator().next();
    }

    public static <T> List<T> loadServices(Class<T> serviceInterface) {
        return getServiceInstances(serviceInterface);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getServiceInstances(Class<T> serviceInterface) {
        List<T> result = new LinkedList<>();
        List<ServiceWrapper> serviceWrappers = SERVICES.getOrDefault(serviceInterface, CollectionsHelper.listOf());

        ServiceLoader<T> loader = ServiceLoader.load(serviceInterface);

        for (T next : loader) {
            boolean add = true;

            // iterate through SERVICES and make sure this is not disabled
            for (ServiceWrapper wrapper : serviceWrappers) {
                if (next.getClass().equals(wrapper.serviceImplClass)) {
                    if (wrapper.serviceInstance != null) {
                        // do not add services that have an explicit service instance
                        add = false;
                    } else {
                        add = wrapper.enabled;
                    }
                }
            }

            if (add) {
                result.add(next);
            }
        }

        // now let`s add services that were explicitly configured
        for (ServiceWrapper serviceWrapper : serviceWrappers) {
            if (serviceWrapper.enabled && (null != serviceWrapper.serviceInstance)) {
                result.add((T) serviceWrapper.serviceInstance);
            }
        }

        return result;
    }

    public static <T> void registerService(Class<T> serviceInterface, T instance) {
        register(serviceInterface, new ServiceWrapper(instance.getClass(), instance, true));
    }

    public static <T> void disableService(Class<T> serviceInterface, Class<? extends T> serviceImpl) {
        register(serviceInterface, new ServiceWrapper(serviceImpl, null, false));
    }

    private static void register(Class<?> serviceInterface, ServiceWrapper wrapper) {
        SERVICES.computeIfAbsent(serviceInterface, aClass -> new CopyOnWriteArrayList<>()).add(wrapper);
    }

    private static final class ServiceWrapper {
        private final Class<?> serviceImplClass;
        private final Object serviceInstance;
        private final boolean enabled;

        private ServiceWrapper(Class<?> serviceImplClass, Object serviceInstance, boolean enabled) {
            this.serviceImplClass = serviceImplClass;
            this.serviceInstance = serviceInstance;
            this.enabled = enabled;
        }
    }
}
