/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.sql.datasource.common;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.config.Config;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Data config factory.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
class DataSourceConfigFactory implements Service.ServicesFactory<DataSourceConfig> {

    private final Supplier<Config> config;
    private final List<DataSourceConfig> explicitConfig;

    @Service.Inject
    DataSourceConfigFactory(Supplier<Config> config) {
        Objects.requireNonNull(config, "config must not be null");
        this.config = config;
        this.explicitConfig = null;
    }

    private DataSourceConfigFactory(List<DataSourceConfig> explicitConfig) {
        Objects.requireNonNull(explicitConfig, "explicitConfig must not be null");
        this.config = null;
        this.explicitConfig = explicitConfig;
    }

    /**
     * Create a new instance with a list of configurations.
     *
     * @param configurations {@link javax.sql.DataSource} configs to use
     * @return a new factory instance to registry with
     *         {@link
     *         io.helidon.service.registry.ServiceConfig.Builder#putServiceInstance(io.helidon.service.registry.ServiceDescriptor,
     *         Object)}
     */
    public static DataSourceConfigFactory create(List<DataSourceConfig> configurations) {
        return new DataSourceConfigFactory(configurations);
    }

    @Override
    public List<Service.QualifiedInstance<DataSourceConfig>> services() {
        if (explicitConfig == null) {
            Config dataConfig = config.get().get("data");
            if (dataConfig.isList()) {
                return fromList(dataConfig);
            }
            return List.of(Service.QualifiedInstance.create(DataSourceConfig.create(dataConfig)));
        } else {
            return explicitConfig.stream()
                    .map(this::toQualifiedInstance)
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    private Service.QualifiedInstance<DataSourceConfig> toQualifiedInstance(DataSourceConfig dataConfig) {
        return Service.QualifiedInstance.create(dataConfig, Qualifier.createNamed(dataConfig.name()));
    }

    private List<Service.QualifiedInstance<DataSourceConfig>> fromList(Config dataConfig) {
        return dataConfig.asNodeList()
                .stream()
                .flatMap(List::stream)
                .map(this::mapSingleConfig)
                .collect(Collectors.toUnmodifiableList());
    }

    private Service.QualifiedInstance<DataSourceConfig> mapSingleConfig(Config config) {
        DataSourceConfig dataConfig = DataSourceConfig.create(config);

        return Service.QualifiedInstance.create(DataSourceConfig.create(config), Qualifier.createNamed(dataConfig.name()));
    }

}
