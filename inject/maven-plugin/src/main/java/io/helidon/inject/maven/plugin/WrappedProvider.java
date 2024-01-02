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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;

class WrappedProvider {
    private final ClassLoader classLoader;
    private final Class<?> servicesType;
    private final Class<?> serviceProviderType;
    private final Class<?> serviceProviderBindableType;
    private final Class<?> injectionResolverType;
    private final Object services;
    private final Object serviceProvider;

    WrappedProvider(ClassLoader classLoader,
                    Class<?> servicesType,
                    Class<?> serviceProviderType,
                    Class<?> serviceProviderBindableType,
                    Class<?> injectionResolverType,
                    Object services,
                    Object serviceProvider) {
        this.classLoader = classLoader;
        this.servicesType = servicesType;
        this.serviceProviderType = serviceProviderType;
        this.serviceProviderBindableType = serviceProviderBindableType;
        this.injectionResolverType = injectionResolverType;
        this.services = services;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public String toString() {
        try {
            return (String) serviceProviderType.getMethod("description")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get description on " + serviceProvider + " using reflection", e);
        }
    }

    boolean isProvider() {
        try {
            return (boolean) serviceProviderType.getMethod("isProvider")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to call isProvider on " + serviceProvider + " using reflection", e);
        }
    }

    ServiceInfo serviceInfo() {
        try {
            return (ServiceInfo) serviceProviderType.getMethod("serviceInfo")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get serviceInfo on " + serviceProvider + " using reflection", e);
        }
    }

    @SuppressWarnings("unchecked")
    WrappedProvider toRootProvider() {
        try {
            Object sp = this.serviceProvider;

            Optional<Object> maybe = (Optional<Object>) serviceProviderType.getMethod("serviceProviderBindable")
                    .invoke(serviceProvider);

            if (maybe.isPresent()) {
                Object bindable = maybe.get();
                sp = (
                        (Optional<Object>) serviceProviderBindableType.getMethod("rootProvider")
                                .invoke(bindable)).orElse(sp);
            }

            if (serviceProviderBindableType.isAssignableFrom(sp.getClass())) {
                sp = (
                        (Optional<Object>) serviceProviderBindableType.getMethod("rootProvider")
                                .invoke(sp)).orElse(sp);
            }

            return new WrappedProvider(classLoader,
                                       servicesType,
                                       serviceProviderType,
                                       serviceProviderBindableType,
                                       injectionResolverType,
                                       services,
                                       sp);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to invoke toRootProvider on " + serviceProvider + " using reflection", e);
        }
    }

    TypeName serviceType() {
        try {
            return (TypeName) serviceProviderType.getMethod("serviceType")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get serviceType on " + serviceProvider + " using reflection", e);
        }
    }

    TypeName infoType() {
        try {
            return (TypeName) serviceProviderType.getMethod("infoType")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get infoType on " + serviceProvider + " using reflection", e);
        }
    }

    @SuppressWarnings("unchecked")
    List<Ip> dependencies() {
        try {
            return (List<Ip>) serviceProviderType.getMethod("dependencies")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get dependencies on " + serviceProvider + " using reflection", e);
        }
    }

    @SuppressWarnings("unchecked")
    Set<TypeName> contracts() {
        try {
            return (Set<TypeName>) serviceProviderType.getMethod("contracts")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get contracts on " + serviceProvider + " using reflection", e);
        }
    }

    @SuppressWarnings("unchecked")
    Set<Qualifier> qualifiers() {
        try {
            return (Set<Qualifier>) serviceProviderType.getMethod("qualifiers")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get qualifiers on " + serviceProvider + " using reflection", e);
        }
    }

    double weight() {
        try {
            return (double) serviceProviderType.getMethod("weight")
                    .invoke(serviceProvider);
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to get weight on " + serviceProvider + " using reflection", e);
        }
    }

    boolean isInjectionResolver() {
        return injectionResolverType.isAssignableFrom(serviceProvider.getClass());
    }

    @SuppressWarnings("unchecked")
    Optional<Object> resolve(Ip injectionPoint) {
        try {
            if (isInjectionResolver()) {
                return (Optional<Object>) injectionResolverType.getMethod("resolve",
                                                                          Ip.class,
                                                                          servicesType,
                                                                          serviceProviderType,
                                                                          boolean.class)
                        .invoke(serviceProvider, injectionPoint, services, serviceProvider, false);
            }
            return Optional.empty();
        } catch (ReflectiveOperationException e) {
            throw new CodegenException("Failed to invoke resolve(" + injectionPoint + ") on " + serviceProvider + " using "
                                               + "reflection",
                                       e);
        }
    }

    Optional<WrappedProvider> asProvider(Object object) {
        if (serviceProviderType.isAssignableFrom(object.getClass())) {
            return Optional.of(new WrappedProvider(classLoader,
                                                   servicesType,
                                                   serviceProviderType,
                                                   serviceProviderBindableType,
                                                   injectionResolverType,
                                                   services,
                                                   object));
        }
        return Optional.empty();
    }
}
