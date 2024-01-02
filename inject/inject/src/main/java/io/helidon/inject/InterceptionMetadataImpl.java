/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.InvocationContext;
import io.helidon.inject.service.Invoker;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;

class InterceptionMetadataImpl implements InterceptionMetadata {
    private final Services services;

    InterceptionMetadataImpl(Services services) {
        this.services = services;
    }

    @Override
    public <T> Invoker<T> createInvoker(ServiceInfo serviceInfo,
                                        Set<Qualifier> typeQualifiers,
                                        List<Annotation> typeAnnotations,
                                        TypedElementInfo element,
                                        Invoker<T> targetInvoker,
                                        Set<Class<? extends Throwable>> checkedExceptions) {
        var interceptors = interceptors(
                typeAnnotations,
                element);
        if (interceptors.isEmpty()) {
            return targetInvoker;
        } else {
            return params -> Invocation.createInvokeAndSupply(InvocationContext.builder()
                                                                      .serviceInfo(serviceInfo)
                                                                      .typeAnnotations(typeAnnotations)
                                                                      .elementInfo(element)
                                                                      .build(),
                                                              interceptors,
                                                              targetInvoker,
                                                              params,
                                                              checkedExceptions);
        }
    }

    private List<Supplier<Interception.Interceptor>> interceptors(List<Annotation> typeAnnotations,
                                                                  TypedElementInfo element) {
        if (!services.injectionServices().config().interceptionEnabled()) {
            return List.of();
        }

        // need to find all interceptors for the providers (ordered by weight)
        List<RegistryServiceProvider<Interception.Interceptor>> allInterceptors;

        if (services instanceof ServicesImpl si) {
            allInterceptors = si.interceptors();
        } else {
            allInterceptors = services.serviceProviders().all(Lookup.builder()
                                                                      .addContract(Interception.Interceptor.class)
                                                                      .addQualifier(Qualifier.WILDCARD_NAMED)
                                                                      .build());
        }

        List<Supplier<Interception.Interceptor>> result = new ArrayList<>();

        for (RegistryServiceProvider<Interception.Interceptor> interceptor : allInterceptors) {
            if (applicable(typeAnnotations, interceptor)) {
                result.add(interceptor);
                continue;
            }
            if (applicable(element.annotations(), interceptor)) {
                result.add(interceptor);
            }
        }

        return result;
    }

    private boolean applicable(List<Annotation> typeAnnotations, RegistryServiceProvider<Interception.Interceptor> interceptor) {
        for (Annotation typeAnnotation : typeAnnotations) {
            if (interceptor.qualifiers().contains(Qualifier.createNamed(typeAnnotation.typeName().fqName()))) {
                return true;
            }
        }
        return false;
    }
}
