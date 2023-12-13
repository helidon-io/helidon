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

package io.helidon.inject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.ElementKind;

/**
 * A descriptor of a service. In addition to providing service metadata, this also allows instantiation
 * and injection to the service instance.
 * <p>
 * Methods from this interface are expected to be code generated (if applicable).
 *
 * @param <T> type of the service implementation
 */
public interface ServiceDescriptor<T> extends ServiceInfo {
    /**
     * Create a new service instance.
     *
     * @param ctx                  injection context with all injection points data
     * @param interceptionMetadata interception metadata to use when the constructor should be intercepted
     * @return a new instance, must be of the type T or a subclass
     */
    // we cannot return T, as it does not allow us to correctly handle inheritance
    default Object instantiate(InjectionContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("Cannot instantiate type " + serviceType().fqName() + ", as it is either abstract,"
                                                + " or an interface.");
    }

    /**
     * Inject fields and methods.
     *
     * @param ctx                  injection context
     * @param interceptionMetadata interception metadata to support interception of field injection
     * @param injected             mutable set of already injected methods from subtypes
     * @param instance             instance to update
     */
    default void inject(InjectionContext ctx,
                        InterceptionMetadata interceptionMetadata,
                        Set<String> injected,
                        T instance) {
    }

    /**
     * Invoke {@link Injection.PostConstruct} annotated method(s).
     *
     * @param instance instance to use
     */
    default void postConstruct(T instance) {
    }

    /**
     * Invoke {@link Injection.PreDestroy} annotated method(s).
     *
     * @param instance instance to use
     */
    default void preDestroy(T instance) {
    }

    /**
     * Combine dependencies from this type with dependencies from supertype.
     * This is a utility for code generated types.
     *
     * @param myType    this type's dependencies
     * @param superType super type's dependencies
     * @return a new list without constructor dependencies from super type
     */
    default List<Ip> combineDependencies(List<Ip> myType, List<Ip> superType) {
        List<Ip> result = new ArrayList<>(myType);

        // always inject all fields
        result.addAll(superType.stream()
                              .filter(it -> it.elementKind() == ElementKind.FIELD)
                              .toList());
        // ignore constructors, as we only need to inject constructor on the instantiated type

        // and only add methods that are not already injected on existing type
        Set<String> injectedMethods = myType.stream()
                .filter(it -> it.elementKind() == ElementKind.METHOD)
                .map(Ip::method)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        result.addAll(superType.stream()
                              .filter(it -> it.elementKind() == ElementKind.METHOD)
                              .filter(it -> it.method().isPresent())
                              .filter(it -> injectedMethods.add(it.method().get())) // we check presence above
                              .toList());

        return List.copyOf(result);
    }
}
