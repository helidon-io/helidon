/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.EnumMap;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;

/**
 * No-op implementation of {@link RegistryFactory}.
 */
public class NoOpRegistryFactory implements RegistryFactory {

    /**
     * @return a new no-op registry factory
     */
    public static NoOpRegistryFactory create() {
        return new NoOpRegistryFactory();
    }

    private static final EnumMap<Type, MetricRegistry> NO_OP_REGISTRIES = Stream.of(Type.values())
            .collect(
                    () -> new EnumMap<>(Type.class),
                    (map, type) -> map.put(type, NoOpMetricRegistry.create(type)),
                    EnumMap::putAll);

    private NoOpRegistryFactory() {
    }

    @Override
    public MetricRegistry getRegistry(Type type) {
        return NO_OP_REGISTRIES.get(type);
    }
}
