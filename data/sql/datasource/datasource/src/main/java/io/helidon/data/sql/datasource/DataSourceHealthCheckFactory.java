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
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.health.HealthCheck;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Creates enabled health checks over the data sources selected by the owning registry.
 */
@Configured(root = true, prefix = "data.sources.sql.health-check",
            description = "Configures independent datasource health discovery.",
            inherits = DataSourceHealthCheck.class,
            options = @ConfiguredOption(key = "enabled", type = Boolean.class, value = "false",
                                        description = "Enables this health definition in its owning registry."))
@Service.Singleton
final class DataSourceHealthCheckFactory implements Service.ServicesFactory<HealthCheck> {
    private final Supplier<Config> configuration;
    private final Supplier<List<ServiceInstance<DataSource>>> sources;

    @Service.Inject
    DataSourceHealthCheckFactory(Supplier<Config> configuration, Supplier<List<ServiceInstance<DataSource>>> sources) {
        this.configuration = configuration;
        this.sources = sources;
    }

    @Override
    public List<Service.QualifiedInstance<HealthCheck>> services() {
        List<Config> definitions;
        try {
            Config node = configuration.get().get("data.sources.sql");
            definitions = node.get("provider").exists() ? List.of(node) : node.asNodeList().orElse(List.of());
        } catch (ConfigException | ServiceRegistryException _) {
            throw new IllegalStateException("Datasource health definitions could not be read.");
        }
        List<Service.QualifiedInstance<HealthCheck>> checks = new ArrayList<>();
        for (Config definition : definitions) {
            Config health = definition.get("health-check");
            boolean enabled;
            try {
                enabled = health.get("enabled").asBoolean().orElse(false);
            } catch (ConfigException _) {
                throw new IllegalStateException("The datasource health discovery setting is invalid.");
            }
            if (!enabled) {
                continue;
            }
            DataSource source;
            try {
                String name = definition.get("name").asString().orElse(Service.Named.DEFAULT_NAME);
                Qualifier qualifier = Qualifier.createNamed(name);
                source = sources.get().stream()
                        .filter(instance -> instance.qualifiers().contains(qualifier))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "An enabled datasource health definition has no matching source."))
                        .get();
            } catch (ConfigException | ServiceRegistryException _) {
                throw new IllegalStateException("The data source for the health check could not be resolved.");
            }
            try {
                DataSourceHealthCheck check = DataSourceHealthCheck.builder().config(health).dataSource(source).build();
                checks.add(Service.QualifiedInstance.create(check));
            } catch (ConfigException _) {
                throw new IllegalStateException("The datasource health configuration is invalid.");
            }
        }
        return List.copyOf(checks);
    }
}
