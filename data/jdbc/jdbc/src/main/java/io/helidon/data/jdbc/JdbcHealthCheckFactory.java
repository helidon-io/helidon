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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.health.HealthCheck;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Creates enabled health checks over the clients selected by the owning registry.
 */
@Service.Singleton
final class JdbcHealthCheckFactory implements Service.ServicesFactory<HealthCheck> {
    private static final Logger LOGGER = System.getLogger(JdbcHealthCheckFactory.class.getName());
    // Observer startup runs outside a request. Both discovery passes use these scopes.
    private static final Lookup CLIENTS = Lookup.builder()
            .addContract(JdbcClient.class)
            .addScope(Service.Singleton.TYPE)
            .addScope(Service.PerLookup.TYPE)
            .build();

    private final ServiceRegistry registry;

    @Service.Inject
    JdbcHealthCheckFactory(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<Service.QualifiedInstance<HealthCheck>> services() {
        Set<ServiceInfo> unavailable = new HashSet<>();
        List<Service.QualifiedInstance<JdbcClient>> candidates = new ArrayList<>();
        boolean selectUnqualified = true;
        for (ServiceInfo provider : providers(CLIENTS)) {
            var instances = clients(provider, CLIENTS, unavailable);
            if (instances.isEmpty()) {
                // This provider might have supplied the first unqualified client.
                selectUnqualified = false;
                continue;
            }
            for (var instance : instances.orElseThrow()) {
                if (!instance.qualifiers().isEmpty()) {
                    candidates.add(instance);
                } else if (selectUnqualified) {
                    candidates.addFirst(instance);
                    selectUnqualified = false;
                }
            }
        }
        List<Service.QualifiedInstance<HealthCheck>> checks = new ArrayList<>();
        Set<Set<Qualifier>> lookups = new HashSet<>();
        Set<Set<Qualifier>> registrations = new HashSet<>();
        for (Service.QualifiedInstance<JdbcClient> candidate : candidates) {
            Set<Qualifier> qualifiers = candidate.qualifiers();
            if (!lookups.add(qualifiers)) {
                continue;
            }
            // An empty lookup also matches qualified services. Keep the first unqualified registration separate.
            var selected = candidate;
            if (!qualifiers.isEmpty()) {
                Lookup lookup = Lookup.builder(CLIENTS).qualifiers(qualifiers).build();
                selected = null;
                for (ServiceInfo provider : providers(lookup)) {
                    var instances = clients(provider, lookup, unavailable);
                    if (instances.isEmpty()) {
                        // This failure can hide the preferred registration. A fallback could publish a shadowed client.
                        break;
                    }
                    var matches = instances.orElseThrow();
                    if (!matches.isEmpty()) {
                        selected = matches.getFirst();
                        break;
                    }
                }
                if (selected == null) {
                    continue;
                }
            }
            // Different lookup criteria can select the same registration when qualifiers overlap.
            if (!registrations.add(selected.qualifiers())) {
                continue;
            }
            JdbcClient client;
            try {
                client = selected.get();
            } catch (RuntimeException _) {
                LOGGER.log(Level.WARNING, "JDBC health discovery could not resolve a selected client.");
                continue;
            }
            // Custom clients expose no supported independent execution contract, including through their prototypes.
            if (!(client instanceof JdbcClientImpl)) {
                continue;
            }
            // A replacement client brings its own policy, including whether its check is enabled.
            JdbcClientConfig configuration = client.prototype();
            JdbcClientHealthConfig health = configuration.health();
            if (!health.enabled()) {
                continue;
            }
            String registrationName = selected.qualifiers().stream()
                    .filter(qualifier -> qualifier.typeName().equals(Service.Named.TYPE))
                    .flatMap(qualifier -> qualifier.value().stream())
                    .filter(name -> !Service.Named.WILDCARD_NAME.equals(name))
                    .min(Comparator.naturalOrder())
                    .orElse(configuration.name());
            JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client)
                    .query(health.query()).queryTimeout(health.queryTimeout()).type(health.type())
                    .name(registrationName).build();
            checks.add(Service.QualifiedInstance.create(check));
        }
        return List.copyOf(checks);
    }

    private List<ServiceInfo> providers(Lookup lookup) {
        try {
            return registry.lookupServices(lookup).stream()
                    .filter(provider -> CLIENTS.scopes().contains(provider.scope()))
                    .toList();
        } catch (RuntimeException _) {
            LOGGER.log(Level.WARNING, "JDBC health discovery could not inspect the service registry.");
            return List.of();
        }
    }

    private Optional<List<? extends Service.QualifiedInstance<JdbcClient>>> clients(ServiceInfo provider,
                                                                                   Lookup lookup,
                                                                                   Set<ServiceInfo> unavailable) {
        FactoryType factoryType = provider.factoryType();
        if (factoryType == FactoryType.SERVICE) {
            // Resolve direct registrations only after selection. Several can share a service type.
            return Optional.of(List.of(new RegisteredClient(registry, provider)));
        }
        if (unavailable.contains(provider)) {
            return Optional.empty();
        }
        try {
            return Optional.of(registry.<JdbcClient>lookupInstances(
                    Lookup.builder(lookup).serviceType(provider.serviceType()).build()));
        } catch (RuntimeException _) {
            // These providers supply the same registrations for every lookup.
            if (factoryType == FactoryType.SERVICES || factoryType == FactoryType.SUPPLIER) {
                unavailable.add(provider);
            }
            LOGGER.log(Level.WARNING, "JDBC health discovery could not inspect the provider {0}.",
                       provider.serviceType().fqName());
            return Optional.empty();
        }
    }

    private record RegisteredClient(ServiceRegistry registry, ServiceInfo descriptor)
            implements Service.QualifiedInstance<JdbcClient> {
        @Override
        public JdbcClient get() {
            return registry.<JdbcClient>get(descriptor)
                    .orElseThrow(() -> new ServiceRegistryException("The selected JDBC client is unavailable."));
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return descriptor.qualifiers();
        }
    }
}
