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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
class HikariDataSourceProviderService implements Service.ServicesFactory<DataSource> {

    static final String PROVIDER_TYPE = "hikari";
    private final Supplier<List<DataSourceConfig>> dsConfigs;

    @Service.Inject
    HikariDataSourceProviderService(Supplier<List<DataSourceConfig>> dsConfigs) {
        this.dsConfigs = dsConfigs;
    }

    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {
        List<Service.QualifiedInstance<DataSource>> instances = new ArrayList<>();

        for (DataSourceConfig dsConfig : dsConfigs.get()) {
            if (dsConfig.provider() instanceof HikariDataSourceConfig hikariConfig) {
                // this is a Hikari Data source
                String name = dsConfig.name();

                instances.add(createDataSource(name, hikariConfig));
            }
        }

        return instances;
    }

    private Service.QualifiedInstance<DataSource> createDataSource(String name, HikariDataSourceConfig providerConfig) {
        return Service.QualifiedInstance.create(HikariDataSourceFactory.create(providerConfig),
                                                Qualifier.createNamed(name));
    }
}
