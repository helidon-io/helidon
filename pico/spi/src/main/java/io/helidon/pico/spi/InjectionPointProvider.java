/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.inject.Provider;

/**
 * Provides ability to contextualize the injected service by the target receiver of the injection point dynamically
 * at runtime.
 *
 * @param <T> the type that the provider produces
 */
public interface InjectionPointProvider<T> extends Provider<T> {

    /**
     * Get (or create) an instance of this service type.
     */
    @Override
    default T get() {
        return get(null, null, true);
    }

    /**
     * Get (or create) an instance of this service type - tailored upon its scope and the target (but optional) injection point / context.
     *
     * @param ipInfoCtx optionally, the injection point context if known
     * @param criteria  optionally, the service info required by the injection point context if known
     * @param expected the flag indicating whether the injection point is required to be furnished
     * @return the resolved service best matching the criteria for the injection point in terms of weight, or null if the context is not supported
     */
    T get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected);

    /**
     * Get (or create) a list of instance of this service type - tailored upon its scope and the target (but optional) injection point / context.
     *
     * @param ipInfoCtx optionally, the injection point context if known -
     * @param criteria  optionally, the service info required by the injection point context if known
     * @param expected the flag indicating whether the injection point is required to be furnished
     * @return the resolved services matching criteria for the injection point in order of weight, or null if the context is not supported
     */
    default List<T> getList(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        T instance = get(ipInfoCtx, criteria, expected);
        return (Objects.isNull(instance)) ? null : Collections.singletonList(instance);
    }

}
