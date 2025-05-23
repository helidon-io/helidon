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

package io.helidon.data.sql.datasource.ucp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
class UcpDataSourceProviderService implements Service.ServicesFactory<DataSource> {

    static final String PROVIDER_TYPE = "ucp";
    private final Supplier<List<DataSourceConfig>> dsConfigs;

    @Service.Inject
    UcpDataSourceProviderService(Supplier<List<DataSourceConfig>> dsConfigs) {
        this.dsConfigs = dsConfigs;
    }

    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {
        List<Service.QualifiedInstance<DataSource>> instances = new ArrayList<>();

        for (DataSourceConfig dsConfig : dsConfigs.get()) {
            if (dsConfig.provider() instanceof UcpDataSourceConfig ucpConfig) {
                // this is a UCP Data source
                String name = dsConfig.name();

                instances.add(createDataSource(name, ucpConfig));
            }
        }

        return instances;
    }

    private Service.QualifiedInstance<DataSource> createDataSource(String name, UcpDataSourceConfig ucpConfig) {
        return Service.QualifiedInstance.create(UcpDataSourceFactory.create(ucpConfig),
                                                Qualifier.createNamed(name));
    }
}
