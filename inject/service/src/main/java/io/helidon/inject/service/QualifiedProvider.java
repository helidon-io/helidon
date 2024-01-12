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

package io.helidon.inject.service;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;

import io.helidon.common.types.TypeName;

/**
 * A provider to resolve qualified injection points of any type.
 * <p>
 * As compared to {@link io.helidon.inject.service.InjectionPointProvider}, this type is capable of resolving ANY injection
 * point as long as it is annotated by the qualifier. The contract of the injection point depends on how the implementation
 * service declares the type parameters of this interface. If you use any type other than {@link java.lang.Object}, that will
 * be the only supported contract, otherwise any type is expected to be supported.
 * <p>
 * A good practise is to create an accompanying codegen extension that validates injection points at build time.
 *
 * @param <A> type of qualifier supported by this provider
 * @param <T> type of the provided instance, the special case is {@link java.lang.Object} - if used, we consider this
 *            provider to be capable of handling ANY type, and will allow injection points with any type as long as it is
 *            qualified by the qualifier
 */
public interface QualifiedProvider<A extends Annotation, T> {
    /**
     * Type name of this interface.
     */
    TypeName TYPE_NAME = TypeName.create(QualifiedProvider.class);

    /**
     * Get the first instance (if any) matching the qualifier and type.
     *
     * @param qualifier the qualifier this type supports (same type as the {@code A} type this type implements)
     * @param lookup    full lookup used to obtain the value, may contain the actual injection point
     * @param type      type to be injected (or type requested)
     * @return the qualified instance matching the request, or an empty optional if none match
     */
    Optional<QualifiedInstance<T>> first(Qualifier qualifier, Lookup lookup, TypeName type);

    /**
     * Get all instances matching the qualifier and type.
     *
     * @param qualifier the qualifier this type supports (same type as the {@code A} type this type implements)
     * @param lookup    full lookup used to obtain the value, may contain the actual injection point
     * @param type      type to be injected (or type requested)
     * @return the qualified instance matching the request, or an empty optional if none match
     */
    default List<QualifiedInstance<T>> list(Qualifier qualifier, Lookup lookup, TypeName type) {
        return first(qualifier, lookup, type)
                .map(List::of)
                .orElseGet(List::of);
    }
}
