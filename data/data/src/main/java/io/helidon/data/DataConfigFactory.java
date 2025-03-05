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

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.data.api.DataConfig;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Qualifier;

/**
 * Data config factory.
 * This type is usually automatically created in the service registry based on configuration.
 * It can be explicitly set to limit the available data configs.
 *
 * @see #create(java.util.List)
 */
@Weight(10)
@Injection.Singleton
@Injection.Named(Injection.Named.WILDCARD_NAME)
public class DataConfigFactory implements Injection.ServicesFactory<DataConfig> {
    private final Supplier<Config> config;
    private final List<DataConfig> explicitConfig;

    @Injection.Inject
    DataConfigFactory(Supplier<Config> config) {
        Objects.requireNonNull(config, "config must not be null");
        this.config = config;
        this.explicitConfig = null;
    }

    private DataConfigFactory(List<DataConfig> explicitConfig) {
        Objects.requireNonNull(explicitConfig, "explicitConfig must not be null");
        this.config = null;
        this.explicitConfig = explicitConfig;
    }

    /**
     * Create a new instance with a list of configurations.
     *
     * @param configurations data configs to use
     * @return a new factory instance to registry with
     *         {@link
     *         io.helidon.service.inject.InjectConfig.Builder#putServiceInstance(io.helidon.service.registry.ServiceDescriptor,
     *         Object)}
     */
    public static DataConfigFactory create(List<DataConfig> configurations) {
        return new DataConfigFactory(configurations);
    }

    @Override
    public List<Injection.QualifiedInstance<DataConfig>> services() {
        if (explicitConfig == null) {
            Config dataConfig = config.get().get("data");
            if (dataConfig.isList()) {
                return fromList(dataConfig);
            }
            return List.of(Injection.QualifiedInstance.create(DataConfig.create(dataConfig)));
        } else {
            return explicitConfig.stream()
                    .map(this::toQualifiedInstance)
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    private Injection.QualifiedInstance<DataConfig> toQualifiedInstance(DataConfig dataConfig) {
        return Injection.QualifiedInstance.create(dataConfig, Qualifier.createNamed(dataConfig.name()));
    }

    private List<Injection.QualifiedInstance<DataConfig>> fromList(Config dataConfig) {
        return dataConfig.asNodeList()
                .stream()
                .flatMap(List::stream)
                .map(this::mapSingleConfig)
                .collect(Collectors.toUnmodifiableList());
    }

    private Injection.QualifiedInstance<DataConfig> mapSingleConfig(Config config) {
        DataConfig dataConfig = DataConfig.create(config);

        return Injection.QualifiedInstance.create(DataConfig.create(config), Qualifier.createNamed(dataConfig.name()));
    }
}
