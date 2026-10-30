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
package io.helidon.data.jdbc.health;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.health.HealthCheck;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Resolves opt in health definitions and effective clients within the owning registry.
 */
@Service.Singleton
final class JdbcHealthCheckFactory implements Service.ServicesFactory<HealthCheck> {
    private final Supplier<Config> configuration;
    private final Supplier<List<ServiceInstance<JdbcClient>>> clients;

    @Service.Inject
    JdbcHealthCheckFactory(Supplier<Config> configuration, Supplier<List<ServiceInstance<JdbcClient>>> clients) {
        this.configuration = configuration;
        this.clients = clients;
    }

    @Override
    public List<Service.QualifiedInstance<HealthCheck>> services() {
        List<Config> definitions;
        try {
            definitions = configuration.get().get("data.clients.jdbc").asNodeList().orElse(List.of());
        } catch (ConfigException | ServiceRegistryException _) {
            throw new DataException("JDBC health definitions could not be read.");
        }
        List<Service.QualifiedInstance<HealthCheck>> checks = new ArrayList<>();
        for (Config definition : definitions) {
            Config health = definition.get("health-check");
            boolean enabled;
            try {
                enabled = health.get("enabled").asBoolean().orElse(false);
            } catch (ConfigException _) {
                throw new DataException("The JDBC health discovery setting is invalid.");
            }
            if (!enabled) {
                continue;
            }
            JdbcClient client;
            try {
                String name = definition.get("name").asString().orElse(Service.Named.DEFAULT_NAME);
                Qualifier qualifier = Qualifier.createNamed(name);
                client = clients.get().stream()
                        .filter(instance -> instance.qualifiers().contains(qualifier))
                        .findFirst()
                        .orElseThrow(() -> new DataException("An enabled JDBC health definition has no matching client."))
                        .get();
            } catch (ConfigException | ServiceRegistryException _) {
                throw new DataException("The JDBC health client could not be resolved.");
            }
            try {
                JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().config(health).client(client).build();
                checks.add(Service.QualifiedInstance.create(check));
            } catch (ConfigException _) {
                throw new DataException("The JDBC health configuration is invalid.");
            }
        }
        return List.copyOf(checks);
    }
}
