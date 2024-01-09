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

import java.util.Set;

/**
 * An instance with its qualifiers.
 * Some services are allowed to create more than one instance, and there may be a need
 * to use different qualifiers than the provider service uses.
 *
 * @see io.helidon.inject.service.ServicesProvider
 *
 * @param instance instance of a service
 * @param qualifiers qualifiers that qualify that instance
 * @param <T> type of instance, as provided by the service
 */
public record QualifiedInstance<T>(T instance, Set<Qualifier> qualifiers) {
    /**
     * Create a new qualified instance.
     *
     * @param instance the instance
     * @param qualifiers qualifiers to use
     * @return a new qualified instance
     * @param <T> type of the instance
     */
    public static <T> QualifiedInstance<T> create(T instance, Qualifier... qualifiers) {
        /*
            Developer note: this method is used from generated code of __ConfigBean
        */
        return new QualifiedInstance<>(instance, Set.of(qualifiers));
    }

    /**
     * Create a new qualified instance.
     *
     * @param instance the instance
     * @param qualifiers qualifiers to use
     * @return a new qualified instance
     * @param <T> type of the instance
     */
    public static <T> QualifiedInstance<T> create(T instance, Set<Qualifier> qualifiers) {
        return new QualifiedInstance<>(instance, Set.copyOf(qualifiers));
    }
}
