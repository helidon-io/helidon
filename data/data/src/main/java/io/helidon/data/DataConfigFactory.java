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
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Data config factory.
 * This type is usually automatically created in the service registry based on configuration.
 * It can be explicitly set to limit the available data configs.
 *
 * @see #create(java.util.List)
 */
@Weight(10)
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
public class DataConfigFactory implements Service.ServicesFactory<DataConfig> {
    private final Supplier<Config> config;
    private final List<DataConfig> explicitConfig;

    @Service.Inject
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
     * @return a new factory instance to register with
     *         {@link
     *         io.helidon.service.registry.ServiceRegistryConfig.Builder#putServiceInstance(
     *         io.helidon.service.registry.ServiceDescriptor, Object)}
     */
    public static DataConfigFactory create(List<DataConfig> configurations) {
        return new DataConfigFactory(configurations);
    }

    @Override
    public List<Service.QualifiedInstance<DataConfig>> services() {
        if (explicitConfig == null) {
            Config dataConfig = config.get().get("data");
            if (dataConfig.isList()) {
                return fromList(dataConfig);
            }
            return List.of(Service.QualifiedInstance.create(DataConfig.create(dataConfig)));
        } else {
            return explicitConfig.stream()
                    .map(this::toQualifiedInstance)
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    private Service.QualifiedInstance<DataConfig> toQualifiedInstance(DataConfig dataConfig) {
        return Service.QualifiedInstance.create(dataConfig, Qualifier.createNamed(dataConfig.name()));
    }

    private List<Service.QualifiedInstance<DataConfig>> fromList(Config dataConfig) {
        return dataConfig.asNodeList()
                .stream()
                .flatMap(List::stream)
                .map(this::mapSingleConfig)
                .collect(Collectors.toUnmodifiableList());
    }

    private Service.QualifiedInstance<DataConfig> mapSingleConfig(Config config) {
        DataConfig dataConfig = DataConfig.create(config);

        return Service.QualifiedInstance.create(DataConfig.create(config), Qualifier.createNamed(dataConfig.name()));
    }
}
