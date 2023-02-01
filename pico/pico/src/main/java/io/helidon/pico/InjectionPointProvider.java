/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Provider;

/**
 * Provides ability to contextualize the injected service by the target receiver of the injection point dynamically
 * at runtime. This API will provide service instances of type {@code T}. These services may be singleton, or created based upon
 * scoping cardinality that is defined by the provider implementation of the given type. This is why the javadoc reads "get (or
 * create)".
 * <p>
 * The ordering of services, and the preferred service itself, is determined by the same as documented for
 * {@link io.helidon.pico.Services}.
 *
 * @param <T> the type that the provider produces
 */
public interface InjectionPointProvider<T> extends Provider<T> {

    /**
     * Get (or create) an instance of this service type using default/empty criteria and context.
     *
     * @return the best service provider matching the criteria
     * @throws io.helidon.pico.PicoException if resolution fails to resolve a match
     */
    @Override
    default T get() {
        return first(PicoServices.SERVICE_QUERY_REQUIRED)
                .orElseThrow(() -> new PicoException("Could not resolve a match for " + this));
    }

    /**
     * Get (or create) an instance of this service type for the given injection point context. This is logically the same
     * as using the first element of the result from calling {@link #list(ContextualServiceQuery)}.
     *
     * @param query the service query
     * @return the best service provider matching the criteria
     * @throws io.helidon.pico.PicoException if expected=true and resolution fails to resolve a match
     */
    Optional<T> first(ContextualServiceQuery query);

    /**
     * Get (or create) a list of instances matching the criteria for the given injection point context.
     *
     * @param query the service query
     * @return the resolved services matching criteria for the injection point in order of weight, or null if the context is not
     *         supported
     */
    default List<T> list(ContextualServiceQuery query) {
        return first(query).map(List::of).orElseGet(List::of);
    }

}
