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
package io.helidon.data.jdbc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.config.ConfigException;
import io.helidon.data.DataException;
import io.helidon.health.HealthCheck;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Creates enabled health checks over the clients selected by the owning registry.
 */
@Service.Singleton
final class JdbcHealthCheckFactory implements Service.ServicesFactory<HealthCheck> {
    private final Supplier<List<JdbcClientConfig>> configurations;
    private final Supplier<List<ServiceInstance<JdbcClient>>> clients;

    @Service.Inject
    JdbcHealthCheckFactory(Supplier<List<JdbcClientConfig>> configurations,
                          Supplier<List<ServiceInstance<JdbcClient>>> clients) {
        this.configurations = configurations;
        this.clients = clients;
    }

    @Override
    public List<Service.QualifiedInstance<HealthCheck>> services() {
        List<JdbcClientConfig> definitions;
        try {
            definitions = configurations.get();
        } catch (ConfigException | ServiceRegistryException _) {
            throw new DataException("JDBC health definitions could not be read.");
        }
        List<Service.QualifiedInstance<HealthCheck>> checks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JdbcClientConfig definition : definitions) {
            // Registry order selects the preferred definition without merging independent health paths.
            if (!names.add(definition.name())) {
                continue;
            }
            JdbcClientHealthConfig health = definition.health();
            if (!health.enabled()) {
                continue;
            }
            JdbcClient client;
            try {
                Qualifier qualifier = Qualifier.createNamed(definition.name());
                client = clients.get().stream()
                        .filter(instance -> instance.qualifiers().contains(qualifier))
                        .findFirst()
                        .orElseThrow(() -> new DataException("An enabled JDBC health definition has no matching client."))
                        .get();
            } catch (ServiceRegistryException _) {
                throw new DataException("The JDBC client for the health check could not be resolved.");
            }
            JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client)
                    .query(health.query()).queryTimeout(health.queryTimeout()).type(health.type())
                    .name(health.name().orElse(definition.name())).build();
            checks.add(Service.QualifiedInstance.create(check));
        }
        return List.copyOf(checks);
    }
}
