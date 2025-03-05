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

package io.helidon.data;

import java.util.ArrayList;
import java.util.List;

import io.helidon.data.api.DataConfig;
import io.helidon.data.api.DataRegistry;
import io.helidon.data.spi.DataProvider;
import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

/**
 * Implementation of Helidon data, a {@link java.util.ServiceLoader} provider implementation of
 * {@link io.helidon.data.spi.DataProvider}.
 */
public class HelidonDataProvider implements DataProvider {
    /**
     * No-arg constructor is required by {@link java.util.ServiceLoader}.
     */
    public HelidonDataProvider() {
    }

    @Override
    public List<DataRegistry> create(List<DataConfig> config) {
        var injectConfig = InjectConfig.builder()
                .putServiceInstance(DataConfigFactory__ServiceDescriptor.INSTANCE, DataConfigFactory.create(config))
                .build();
        InjectRegistryManager manager = InjectRegistryManager.create(injectConfig);
        InjectRegistry registry = manager.registry();

        List<DataRegistry> result = new ArrayList<>();

        for (DataConfig dataConfig : config) {
            if (dataConfig.name().equals(Injection.Named.DEFAULT_NAME)) {
                result.add(registry.get(DataRegistry.class));
            } else {
                result.add(registry.get(Lookup.builder()
                                                .addContract(DataRegistry.class)
                                                .addQualifier(Qualifier.createNamed(dataConfig.name()))
                                                .build()));
            }
        }

        return List.copyOf(result);
    }
}
