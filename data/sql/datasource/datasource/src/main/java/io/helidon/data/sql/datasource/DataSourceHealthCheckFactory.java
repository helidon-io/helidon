/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.config.ConfigException;
import io.helidon.health.HealthCheck;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Creates enabled health checks over the data sources selected by the owning registry.
 */
@Service.Singleton
final class DataSourceHealthCheckFactory implements Service.ServicesFactory<HealthCheck> {
    private final Supplier<List<DataSourceConfig>> configurations;
    private final Supplier<List<ServiceInstance<DataSource>>> sources;

    @Service.Inject
    DataSourceHealthCheckFactory(Supplier<List<DataSourceConfig>> configurations,
                                Supplier<List<ServiceInstance<DataSource>>> sources) {
        this.configurations = configurations;
        this.sources = sources;
    }

    @Override
    public List<Service.QualifiedInstance<HealthCheck>> services() {
        List<DataSourceConfig> definitions;
        try {
            definitions = configurations.get();
        } catch (ConfigException | ServiceRegistryException _) {
            throw new IllegalStateException("Datasource health definitions could not be read.");
        }
        List<Service.QualifiedInstance<HealthCheck>> checks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (DataSourceConfig definition : definitions) {
            // Registry order selects the preferred definition without merging independent health paths.
            if (!names.add(definition.name())) {
                continue;
            }
            DataSourceHealthConfig health = definition.health();
            if (!health.enabled()) {
                continue;
            }
            DataSource source;
            try {
                Qualifier qualifier = Qualifier.createNamed(definition.name());
                source = sources.get().stream()
                        .filter(instance -> instance.qualifiers().contains(qualifier))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "An enabled datasource health definition has no matching source."))
                        .get();
            } catch (ServiceRegistryException _) {
                throw new IllegalStateException("The data source for the health check could not be resolved.");
            }
            DataSourceHealthCheck check = DataSourceHealthCheck.builder().dataSource(source)
                    .query(health.query()).queryTimeout(health.queryTimeout()).type(health.type())
                    .name(health.name().orElse(definition.name())).build();
            checks.add(Service.QualifiedInstance.create(check));
        }
        return List.copyOf(checks);
    }
}
