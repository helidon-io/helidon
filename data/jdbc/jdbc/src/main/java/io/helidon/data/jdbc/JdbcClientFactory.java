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
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

/**
 * Creates registry managed JDBC clients from the effective client
 * configurations.
 */
@Service.Singleton
final class JdbcClientFactory implements Service.ServicesFactory<JdbcClient> {

    private static final Qualifier PROVIDER_QUALIFIER = Qualifier.builder()
            .typeName(Data.ProviderType.TYPE)
            .value(Jdbc.PROVIDER)
            .build();

    private final Supplier<List<JdbcClientConfig>> configurations;
    private final Supplier<List<ServiceInstance<DataSource>>> dataSources;
    private final JdbcTransactionConnectionManager connectionManager;

    /**
     * Creates the registry managed client factory.
     *
     * @param configurations effective client configurations
     * @param dataSources available SQL data sources
     * @param connectionManager transaction aware connection manager
     */
    @Service.Inject
    JdbcClientFactory(Supplier<List<JdbcClientConfig>> configurations,
                      Supplier<List<ServiceInstance<DataSource>>> dataSources,
                      JdbcTransactionConnectionManager connectionManager) {
        this.configurations = configurations;
        this.dataSources = dataSources;
        this.connectionManager = connectionManager;
    }

    @Override
    public List<Service.QualifiedInstance<JdbcClient>> services() {
        List<JdbcClientConfig> configs = List.copyOf(configurations.get());
        JdbcClientConfigSupport.validateAll(configs);

        List<PreparedClient> preparedClients = new ArrayList<>(configs.size());
        for (JdbcClientConfig config : configs) {
            JdbcClientImpl.CachePolicy cachePolicy = JdbcProviderPropertiesSupport.create(
                    Objects.requireNonNull(config.properties(), "The JDBC client properties must not be null."));
            preparedClients.add(new PreparedClient(config, cachePolicy));
        }

        List<ServiceInstance<DataSource>> availableDataSources = List.of();
        if (configs.stream().anyMatch(config -> config.dataSource().isPresent())) {
            try {
                availableDataSources = List.copyOf(dataSources.get());
            } catch (RuntimeException failure) {
                throw new DataException("The JDBC client factory could not inspect registered SQL data sources.",
                                        JdbcExceptionTranslator.sanitize("inspecting registered SQL data sources",
                                                                         failure));
            }
        }

        List<PlannedClient> plannedClients = new ArrayList<>(preparedClients.size());
        for (PreparedClient prepared : preparedClients) {
            JdbcClientConfig config = prepared.config();
            DataSource dataSource;
            ServiceInstance<DataSource> dataSourceService = null;
            if (config.dataSourceInstance().isPresent()) {
                dataSource = config.dataSourceInstance().get();
            } else if (config.dataSource().isPresent()) {
                dataSource = null;
                String dataSourceName = config.dataSource().get();
                Qualifier named = Qualifier.createNamed(dataSourceName);
                List<ServiceInstance<DataSource>> matches = availableDataSources.stream()
                        .filter(instance -> instance.qualifiers().contains(named))
                        .toList();
                if (matches.size() != 1) {
                    throw dataSourceResolutionFailure(config.name(), dataSourceName, null);
                }
                dataSourceService = matches.getFirst();
            } else {
                dataSource = JdbcConnectionSourceSupport.directDataSource(
                        JdbcClientConfigSupport.clientDescription(config.name()),
                        config.connection().orElseThrow());
            }
            plannedClients.add(new PlannedClient(config, prepared.cachePolicy(), dataSource, dataSourceService));
        }

        List<ResolvedClient> resolvedClients = new ArrayList<>(plannedClients.size());
        for (PlannedClient planned : plannedClients) {
            DataSource dataSource = planned.dataSource();
            if (dataSource == null) {
                JdbcClientConfig config = planned.config();
                try {
                    dataSource = Objects.requireNonNull(planned.dataSourceService().get(),
                                                        "The registered SQL data source must not be null.");
                } catch (RuntimeException failure) {
                    throw dataSourceResolutionFailure(config.name(), config.dataSource().orElseThrow(), failure);
                }
            }
            resolvedClients.add(new ResolvedClient(planned.config(), planned.cachePolicy(), dataSource));
        }

        List<Service.QualifiedInstance<JdbcClient>> clients = new ArrayList<>(resolvedClients.size());
        for (ResolvedClient resolved : resolvedClients) {
            JdbcClientConfig config = resolved.config();
            JdbcClient client = new JdbcClientImpl(config,
                                                   resolved.dataSource(),
                                                   connectionManager,
                                                   resolved.cachePolicy());
            clients.add(Service.QualifiedInstance.create(client,
                                                         Qualifier.createNamed(config.name()),
                                                         PROVIDER_QUALIFIER));
        }
        return List.copyOf(clients);
    }

    private static DataException dataSourceResolutionFailure(String clientName,
                                                             String dataSourceName,
                                                             RuntimeException cause) {
        String message = JdbcClientConfigSupport.clientDescription(clientName) + " could not resolve SQL data source '"
                + dataSourceName + "'.";
        if (cause == null) {
            return new DataException(message);
        }
        return new DataException(message,
                                 JdbcExceptionTranslator.sanitize("resolving a SQL data source", cause));
    }

    /**
     * A validated client configuration paired with its runtime cache policy.
     *
     * @param config client configuration
     * @param cachePolicy cache policy
     */
    private record PreparedClient(JdbcClientConfig config,
                                  JdbcClientImpl.CachePolicy cachePolicy) {
    }

    /**
     * A client configuration paired with a resolved source or an inactive
     * named data source service.
     *
     * @param config client configuration
     * @param cachePolicy cache policy
     * @param dataSource resolved data source when one is already available
     * @param dataSourceService inactive named data source service when needed
     */
    private record PlannedClient(JdbcClientConfig config,
                                 JdbcClientImpl.CachePolicy cachePolicy,
                                 DataSource dataSource,
                                 ServiceInstance<DataSource> dataSourceService) {
    }

    /**
     * A client configuration paired with every resolved runtime dependency.
     *
     * @param config client configuration
     * @param cachePolicy cache policy
     * @param dataSource resolved data source
     */
    private record ResolvedClient(JdbcClientConfig config,
                                  JdbcClientImpl.CachePolicy cachePolicy,
                                  DataSource dataSource) {
    }
}
