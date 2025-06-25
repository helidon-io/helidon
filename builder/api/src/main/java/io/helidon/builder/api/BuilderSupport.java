/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;

/**
 * Static methods used from generated prototypes and builders.
 * <p>
 * This only contains methods that can be used without additional dependencies (i.e. {@link java.util.ServiceLoader} based).
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class BuilderSupport {
    private BuilderSupport() {
    }

    /**
     * Discover services from Java {@link java.util.ServiceLoader}.
     * This method will only add instances of classes that are not already part of {@code existingInstances}.
     *
     * @param contract          service contract (or provider interface as used in {@link java.util.ServiceLoader})
     * @param discoverServices  whether to discover services from service loader, if set to false, service loader is ignored
     * @param existingInstances instances already configured on the builder
     * @param <C>               type of the contract to use
     * @return a list of new instances to add to the builder
     */
    public static <C> List<C> discoverServices(Class<C> contract,
                                               boolean discoverServices,
                                               List<C> existingInstances) {
        if (!discoverServices) {
            return List.of();
        }
        List<C> newInstances = new ArrayList<>();
        Set<Class<?>> existingServiceTypes = new HashSet<>();
        existingInstances.forEach(it -> existingServiceTypes.add(it.getClass()));
        HelidonServiceLoader.create(contract).forEach(it -> {
            if (!existingServiceTypes.contains(it.getClass())) {
                newInstances.add(it);
            }
        });

        return newInstances;
    }

    /**
     * Discover a service from {@link java.util.ServiceLoader}.
     * This method will only query the service loader if the {@code existingInstance} is empty.
     *
     * @param contract         service contract (or provider interface as used in {@link java.util.ServiceLoader})
     * @param discoverServices whether to discover services from service loader, if set to false, service loader is ignored
     * @param existingInstance an instance configured on the builder
     * @param <C>              type of the contract
     * @return value to be used by the builder (either the existing instance, an instance from {@link java.util.ServiceLoader},
     *         or empty if none found
     */
    public static <C> Optional<C> discoverService(Class<C> contract,
                                                  boolean discoverServices,
                                                  Optional<C> existingInstance) {
        if (existingInstance.isPresent() || !discoverServices) {
            return existingInstance;
        }

        return HelidonServiceLoader.create(contract)
                .stream()
                .findFirst();
    }
}
