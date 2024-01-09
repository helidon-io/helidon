/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;

import io.helidon.common.types.TypeName;

/**
 * Provides ability to contextualize the injected service by the target receiver of the injection point dynamically
 * at runtime. This API will provide service instances of type {@code T}. These services may be singleton, or created based upon
 * scoping cardinality that is defined by the provider implementation of the given type. This is why the javadoc reads "get (or
 * create)".
 * <p>
 * The ordering of services, and the preferred service itself, is determined by the service registry implementation.
 * <p>
 * The service registry does not make any assumptions about qualifiers of the instances being created, though they should
 * be either the same as the injection point provider itself, or a subset of it, so the service can be discovered through
 * one of the lookup methods (i.e. the injection point provider may be annotated with a
 * {@link io.helidon.inject.service.Injection.Named} with {@link io.helidon.inject.service.Injection.Named#WILDCARD_NAME} value,
 * and each instance provided may use a more specific name qualifier).
 *
 * @param <T> the type that the provider produces
 */
public interface InjectionPointProvider<T> {
    /**
     * Type name of this interface.
     */
    TypeName TYPE_NAME = TypeName.create(InjectionPointProvider.class);

    /**
     * Get (or create) an instance of this service type for the given injection point context. This is logically the same
     * as using the first element of the result from calling {@link #list(io.helidon.inject.service.Lookup)}.
     *
     * @param query the service query
     * @return the best service provider matching the criteria, if any matched, with qualifiers (if any)
     */
    Optional<QualifiedInstance<T>> first(Lookup query);

    /**
     * Get (or create) a list of instances matching the criteria for the given injection point context.
     *
     * @param query the service query
     * @return the resolved services matching criteria for the injection point in order of weight, or empty if none matching
     */
    default List<QualifiedInstance<T>> list(Lookup query) {
        return first(query).map(List::of).orElseGet(List::of);
    }
}
