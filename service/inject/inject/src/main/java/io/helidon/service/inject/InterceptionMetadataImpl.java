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

package io.helidon.service.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.inject.ServiceSupplies.ServiceSupply;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.InvocationContext;
import io.helidon.service.inject.api.Invoker;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

class InterceptionMetadataImpl implements InterceptionMetadata {
    private final InjectServiceRegistryImpl registry;

    private InterceptionMetadataImpl(InjectServiceRegistryImpl registry) {
        this.registry = registry;
    }

    static InterceptionMetadata create(InjectServiceRegistryImpl registry) {
        return new InterceptionMetadataImpl(registry);
    }

    static InterceptionMetadata noop() {
        return new NoopMetadata();
    }

    @Override
    public <T> Invoker<T> createInvoker(InjectServiceInfo descriptor,
                                        Set<Qualifier> typeQualifiers,
                                        List<Annotation> typeAnnotations,
                                        TypedElementInfo element,
                                        Invoker<T> targetInvoker,
                                        Set<Class<? extends Throwable>> checkedExceptions) {
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(typeQualifiers);
        Objects.requireNonNull(typeAnnotations);
        Objects.requireNonNull(element);
        Objects.requireNonNull(targetInvoker);
        Objects.requireNonNull(checkedExceptions);

        var interceptors = interceptors(typeAnnotations, element);
        if (interceptors.isEmpty()) {
            return targetInvoker;
        } else {
            return params -> Invocation.createInvokeAndSupply(InvocationContext.builder()
                                                                      .serviceInfo(descriptor)
                                                                      .typeAnnotations(typeAnnotations)
                                                                      .elementInfo(element)
                                                                      .build(),
                                                              interceptors,
                                                              targetInvoker,
                                                              params,
                                                              checkedExceptions);
        }
    }

    @Override
    public <T> Invoker<T> createInvoker(Object serviceInstance,
                                        InjectServiceInfo descriptor,
                                        Set<Qualifier> typeQualifiers,
                                        List<Annotation> typeAnnotations,
                                        TypedElementInfo element,
                                        Invoker<T> targetInvoker,
                                        Set<Class<? extends Throwable>> checkedExceptions) {
        Objects.requireNonNull(serviceInstance);
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(typeQualifiers);
        Objects.requireNonNull(typeAnnotations);
        Objects.requireNonNull(element);
        Objects.requireNonNull(targetInvoker);
        Objects.requireNonNull(checkedExceptions);

        var interceptors = interceptors(typeAnnotations, element);
        if (interceptors.isEmpty()) {
            return targetInvoker;
        } else {
            return params -> Invocation.createInvokeAndSupply(InvocationContext.builder()
                                                                      .serviceInstance(serviceInstance)
                                                                      .serviceInfo(descriptor)
                                                                      .typeAnnotations(typeAnnotations)
                                                                      .elementInfo(element)
                                                                      .build(),
                                                              interceptors,
                                                              targetInvoker,
                                                              params,
                                                              checkedExceptions);
        }
    }

    private List<Supplier<io.helidon.service.inject.api.Interception.Interceptor>> interceptors(List<Annotation> typeAnnotations,
                                                                                                TypedElementInfo element) {
        // need to find all interceptors for the providers (ordered by weight)
        List<ServiceManager<Interception.Interceptor>> allInterceptors = registry.interceptors();

        List<Supplier<Interception.Interceptor>> result = new ArrayList<>();

        for (ServiceManager<Interception.Interceptor> interceptor : allInterceptors) {
            if (applicable(typeAnnotations, interceptor.injectDescriptor())) {
                result.add(new ServiceSupply<>(Lookup.EMPTY, List.of(interceptor)));
                continue;
            }
            if (applicable(element.annotations(), interceptor.injectDescriptor())) {
                result.add(new ServiceSupply<>(Lookup.EMPTY, List.of(interceptor)));
            }
        }

        return result;
    }

    private boolean applicable(List<Annotation> typeAnnotations, InjectServiceInfo serviceInfo) {
        for (Annotation typeAnnotation : typeAnnotations) {
            if (serviceInfo.qualifiers().contains(Qualifier.createNamed(typeAnnotation.typeName().fqName()))) {
                return true;
            }
        }
        return false;
    }

    private static class NoopMetadata implements InterceptionMetadata {
        @Override
        public <T> Invoker<T> createInvoker(InjectServiceInfo descriptor,
                                            Set<Qualifier> typeQualifiers,
                                            List<Annotation> typeAnnotations,
                                            TypedElementInfo element,
                                            Invoker<T> targetInvoker,
                                            Set<Class<? extends Throwable>> checkedExceptions) {
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(typeQualifiers);
            Objects.requireNonNull(typeAnnotations);
            Objects.requireNonNull(element);
            Objects.requireNonNull(targetInvoker);
            Objects.requireNonNull(checkedExceptions);

            return targetInvoker;
        }

        @Override
        public <T> Invoker<T> createInvoker(Object serviceInstance,
                                            InjectServiceInfo descriptor,
                                            Set<Qualifier> typeQualifiers,
                                            List<Annotation> typeAnnotations,
                                            TypedElementInfo element,
                                            Invoker<T> targetInvoker,
                                            Set<Class<? extends Throwable>> checkedExceptions) {
            Objects.requireNonNull(serviceInstance);
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(typeQualifiers);
            Objects.requireNonNull(typeAnnotations);
            Objects.requireNonNull(element);
            Objects.requireNonNull(targetInvoker);
            Objects.requireNonNull(checkedExceptions);

            return targetInvoker;
        }
    }
}
