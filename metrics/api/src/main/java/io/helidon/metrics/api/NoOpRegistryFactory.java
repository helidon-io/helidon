/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;

import io.helidon.common.media.type.MediaType;

/**
 * No-op implementation of {@link RegistryFactory}.
 * <p>
 *     This impl simply prepares app, vendor, and base registries that produce no-op metrics.
 * </p>
 */
class NoOpRegistryFactory implements RegistryFactory {

    /**
     * @return a new no-op registry factory
     */
    public static NoOpRegistryFactory create() {
        return new NoOpRegistryFactory();
    }

    private static final Map<String, Registry> NO_OP_REGISTRIES = Registry.BUILT_IN_SCOPES
            .stream()
            .collect(
                    HashMap::new,
                    (map, scope) -> map.put(scope, NoOpMetricRegistry.create(scope)),
                    Map::putAll);

    private NoOpRegistryFactory() {
    }

    @Override
    public Registry getRegistry(String scope) {
        return NO_OP_REGISTRIES.get(scope);
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Object scrape(MediaType mediaType,
                         Iterable<String> scopeSelection,
                         Iterable<String> meterNameSelection) {
        throw new UnsupportedOperationException("NoOp registry does not support output");
    }

    @Override
    public Iterable<String> scopes() {
        return NO_OP_REGISTRIES.keySet();
    }
}
