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
package io.helidon.data.sql.datasource.hikari;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import io.helidon.common.config.Config;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.data.sql.datasource.ProviderConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
class HikariDataSourceProviderService implements Service.ServicesFactory<DataSource> {

    static final String PROVIDER_TYPE = "hikari";
    private final Supplier<Config> configSupplier;

    @Service.Inject
    HikariDataSourceProviderService(Supplier<Config> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {
        Config config = configSupplier.get().get("data-source");
        if (config.exists()) {
            if (config.isList()) {
                return fromList(config);
            }
            Service.QualifiedInstance<DataSource> qualifiedInstance = mapSingleConfig(config);
            return qualifiedInstance != null ? List.of(qualifiedInstance) : List.of();
        }
        return List.of();
    }

    /**
     * Transforms {@link Config} to the {@link Service.QualifiedInstance} containing {@link DataSource}.
     *
     * @param config the {@link DataSourceConfig} config
     * @return target {@link Service.QualifiedInstance} with Hikari {@link DataSource}
     *         or {@code null} when provider config is not {@link HikariDataSourceConfig}
     */
    Service.QualifiedInstance<DataSource> mapSingleConfig(Config config) {
        DataSourceConfig dataSourceConfig = DataSourceConfig.create(config);
        ProviderConfig providerConfig = dataSourceConfig.provider();
        if (providerConfig instanceof HikariDataSourceConfig hikariDataSourceConfig) {
            return Service.QualifiedInstance.create(HikariDataSourceFactory.create(hikariDataSourceConfig),
                                                    Qualifier.createNamed(dataSourceConfig.name()));
        }
        return null;
    }

    /**
     * Transforms {@link List} of {@link Config} to the {@link List} of {@link Service.QualifiedInstance}
     * containing {@link DataSource}.
     *
     * @param configList the {@link List} of {@link DataSourceConfig} config
     * @return the {@link List} of {@link Service.QualifiedInstance} containing {@link DataSource}
     */
    private List<Service.QualifiedInstance<DataSource>> fromList(Config configList) {
        return configList.asNodeList()
                .stream()
                .flatMap(List::stream)
                .map(this::mapSingleConfig)
                // Unsupported configs will appear as null values and must be removed
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

}
