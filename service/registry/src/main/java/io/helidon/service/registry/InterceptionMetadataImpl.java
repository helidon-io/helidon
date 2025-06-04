/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.ServiceSupplies.ServiceSupply;

class InterceptionMetadataImpl implements InterceptionMetadata {
    private static final ResolvedType ELEMENT_INTERCEPTOR = ResolvedType.create(Interception.ElementInterceptor.class);

    private final CoreServiceRegistry registry;

    private InterceptionMetadataImpl(CoreServiceRegistry registry) {
        this.registry = registry;
    }

    static InterceptionMetadata create(CoreServiceRegistry registry) {
        return new InterceptionMetadataImpl(registry);
    }

    static InterceptionMetadata noop() {
        return new NoopMetadata();
    }

    @Override
    public <T> InterceptionInvoker<T> createInvoker(ServiceInfo descriptor,
                                                    Set<Qualifier> typeQualifiers,
                                                    List<Annotation> typeAnnotations,
                                                    TypedElementInfo element,
                                                    InterceptionInvoker<T> targetInvoker,
                                                    Set<Class<? extends Throwable>> checkedExceptions) {
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(typeQualifiers);
        Objects.requireNonNull(typeAnnotations);
        Objects.requireNonNull(element);
        Objects.requireNonNull(targetInvoker);
        Objects.requireNonNull(checkedExceptions);

        var interceptors = interceptors(descriptor, typeAnnotations, element);
        if (interceptors.isEmpty()) {
            return targetInvoker;
        } else {
            return params -> Invocation.invokeInterception(InterceptionContext.builder()
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
    public <T> InterceptionInvoker<T> createInvoker(Object serviceInstance,
                                                    ServiceInfo descriptor,
                                                    Set<Qualifier> typeQualifiers,
                                                    List<Annotation> typeAnnotations,
                                                    TypedElementInfo element,
                                                    InterceptionInvoker<T> targetInvoker,
                                                    Set<Class<? extends Throwable>> checkedExceptions) {
        Objects.requireNonNull(serviceInstance);
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(typeQualifiers);
        Objects.requireNonNull(typeAnnotations);
        Objects.requireNonNull(element);
        Objects.requireNonNull(targetInvoker);
        Objects.requireNonNull(checkedExceptions);

        var interceptors = interceptors(descriptor, typeAnnotations, element);
        if (interceptors.isEmpty()) {
            return targetInvoker;
        } else {
            return params -> Invocation.invokeInterception(InterceptionContext.builder()
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

    private List<Supplier<Interception.Interceptor>> interceptors(ServiceInfo descriptor,
                                                                  List<Annotation> typeAnnotations,
                                                                  TypedElementInfo element) {
        // need to find all interceptors for the providers (ordered by weight)
        List<ServiceManager<Interception.Interceptor>> allInterceptors = registry.interceptors();

        List<Supplier<Interception.Interceptor>> result = new ArrayList<>();

        for (ServiceManager<Interception.Interceptor> interceptor : allInterceptors) {
            if (interceptor.descriptor().contracts().contains(ELEMENT_INTERCEPTOR)) {
                // interceptors of specific elements (methods, constructors)
                String elementSignature = descriptor.serviceType().fqName() + "." + element.signature().text();
                if (applicableElement(elementSignature, interceptor.descriptor())) {
                    result.add(new ServiceSupply<>(Lookup.EMPTY, List.of(interceptor)));
                }
            } else {
                if (applicable(typeAnnotations, interceptor.descriptor())) {
                    result.add(new ServiceSupply<>(Lookup.EMPTY, List.of(interceptor)));
                    continue;
                }
                if (applicable(element.annotations(), interceptor.descriptor())) {
                    result.add(new ServiceSupply<>(Lookup.EMPTY, List.of(interceptor)));
                }
            }
        }

        return result;
    }

    private boolean applicableElement(String signature, ServiceInfo serviceInfo) {
        return serviceInfo.qualifiers().contains(Qualifier.createNamed(signature));
    }

    private boolean applicable(List<Annotation> typeAnnotations, ServiceInfo serviceInfo) {
        for (Annotation typeAnnotation : typeAnnotations) {
            if (serviceInfo.qualifiers().contains(Qualifier.createNamed(typeAnnotation.typeName().fqName()))) {
                return true;
            }
        }
        return false;
    }

    private static class NoopMetadata implements InterceptionMetadata {
        @Override
        public <T> InterceptionInvoker<T> createInvoker(ServiceInfo descriptor,
                                                        Set<Qualifier> typeQualifiers,
                                                        List<Annotation> typeAnnotations,
                                                        TypedElementInfo element,
                                                        InterceptionInvoker<T> targetInvoker,
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
        public <T> InterceptionInvoker<T> createInvoker(Object serviceInstance,
                                                        ServiceInfo descriptor,
                                                        Set<Qualifier> typeQualifiers,
                                                        List<Annotation> typeAnnotations,
                                                        TypedElementInfo element,
                                                        InterceptionInvoker<T> targetInvoker,
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
