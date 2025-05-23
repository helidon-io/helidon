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
package io.helidon.data.sql.datasource;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Data config factory.
 */
@Service.Singleton
class DataSourceConfigFactory implements Service.ServicesFactory<DataSourceConfig> {

    private static final String SQL_DATA_SOURCES_CONFIG_KEY = "data-sources.sql";

    private final Supplier<Config> config;

    @Service.Inject
    DataSourceConfigFactory(Supplier<Config> config) {
        Objects.requireNonNull(config, "config must not be null");
        this.config = config;
    }

    @Override
    public List<Service.QualifiedInstance<DataSourceConfig>> services() {
        Config dataConfig = config.get()
                .get(SQL_DATA_SOURCES_CONFIG_KEY);

        if (dataConfig.get("provider").exists()) {
            return List.of(mapSingleConfig(dataConfig));
        }

        return fromList(dataConfig);
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

        return Service.QualifiedInstance.create(dataConfig, Qualifier.createNamed(dataConfig.name()));
    }

}
