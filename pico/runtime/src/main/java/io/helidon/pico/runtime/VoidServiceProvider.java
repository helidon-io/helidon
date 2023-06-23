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

package io.helidon.pico.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceProvider;

import jakarta.inject.Singleton;

/**
 * A proxy service provider created internally by the framework.
 */
class VoidServiceProvider extends AbstractServiceProvider<Void> {
    static final VoidServiceProvider INSTANCE = new VoidServiceProvider() {};
    static final List<ServiceProvider<?>> LIST_INSTANCE = List.of(INSTANCE);

    private VoidServiceProvider() {
        serviceInfo(ServiceInfo.builder()
                .serviceTypeName(Void.class)
                .addContractImplemented(serviceTypeName())
                .activatorTypeName(VoidServiceProvider.class)
                .addScopeTypeName(Singleton.class)
                .declaredWeight(DEFAULT_WEIGHT)
                .build());
    }

    public static TypeName serviceTypeName() {
        return TypeName.create(Void.class);
    }

    @Override
    protected Void createServiceProvider(Map<String, Object> resolvedDeps) {
        // this must return null by definition
        return null;
    }

    @Override
    public Optional<Void> first(ContextualServiceQuery query) {
        return Optional.empty();
    }

    @Override
    public Class<?> serviceType() {
        return Void.class;
    }
}
