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

import io.helidon.data.DataException;
import io.helidon.health.HealthCheck;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Creates enabled health checks over the clients selected by the owning registry.
 */
@Service.Singleton
final class JdbcHealthCheckFactory implements Service.ServicesFactory<HealthCheck> {
    private final ServiceRegistry registry;

    @Service.Inject
    JdbcHealthCheckFactory(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<Service.QualifiedInstance<HealthCheck>> services() {
        List<ServiceInstance<JdbcClient>> candidates;
        try {
            // Enumeration can activate client factories even when their clients disable health checks.
            candidates = registry.lookupInstances(Lookup.create(JdbcClient.class));
        } catch (RuntimeException _) {
            throw new DataException("The registered JDBC clients could not be inspected for health checks.");
        }
        List<Service.QualifiedInstance<HealthCheck>> checks = new ArrayList<>();
        Set<Set<Qualifier>> registrations = new HashSet<>();
        for (ServiceInstance<JdbcClient> candidate : candidates) {
            Set<Qualifier> qualifiers = candidate.qualifiers();
            if (!registrations.add(qualifiers)) {
                continue;
            }
            JdbcClient client;
            try {
                // An empty lookup also matches qualified services. Keep the first unqualified registration separate.
                // Qualified lookup order can differ from the unrestricted candidate list order.
                client = qualifiers.isEmpty() ? candidate.get()
                        : registry.<JdbcClient>first(Lookup.builder().addContract(JdbcClient.class)
                                .qualifiers(qualifiers).build()).orElse(null);
            } catch (RuntimeException _) {
                throw new DataException("The JDBC client for the health check could not be resolved.");
            }
            // Custom clients expose no supported independent execution contract, including through their prototypes.
            if (!(client instanceof JdbcClientImpl)) {
                continue;
            }
            JdbcClientConfig configuration = client.prototype();
            JdbcClientHealthConfig health = configuration.health();
            if (!health.enabled()) {
                continue;
            }
            String registrationName = qualifiers.stream()
                    .filter(qualifier -> qualifier.typeName().equals(Service.Named.TYPE))
                    .map(qualifier -> qualifier.value().orElse(Service.Named.DEFAULT_NAME))
                    .findFirst().orElse(configuration.name());
            JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client)
                    .query(health.query()).queryTimeout(health.queryTimeout()).type(health.type())
                    .name(registrationName).build();
            checks.add(Service.QualifiedInstance.create(check));
        }
        return List.copyOf(checks);
    }
}
