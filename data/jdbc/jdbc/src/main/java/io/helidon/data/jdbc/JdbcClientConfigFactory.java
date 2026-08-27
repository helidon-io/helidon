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
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.data.DataException;
import io.helidon.service.registry.Service;

/**
 * Creates JDBC client configurations from application configuration.
 */
@Service.Singleton
final class JdbcClientConfigFactory implements Service.ServicesFactory<JdbcClientConfig> {

    static final String CONFIG_KEY = "data.clients.jdbc";

    private final Supplier<Config> config;

    /**
     * Creates the configuration factory.
     *
     * @param config application configuration
     */
    @Service.Inject
    JdbcClientConfigFactory(Supplier<Config> config) {
        this.config = config;
    }

    @Override
    public List<Service.QualifiedInstance<JdbcClientConfig>> services() {
        List<Config> configuredClients;
        try {
            configuredClients = config.get()
                    .get(CONFIG_KEY)
                    .asNodeList()
                    .orElse(List.of());
        } catch (RuntimeException failure) {
            throw new DataException("JDBC client configuration could not be read.",
                                    JdbcExceptionTranslator.sanitize("reading JDBC client configuration", failure));
        }
        List<JdbcClientConfig> clients = new ArrayList<>(configuredClients.size());
        for (int index = 0; index < configuredClients.size(); index++) {
            try {
                clients.add(JdbcClientConfig.create(configuredClients.get(index)));
            } catch (RuntimeException failure) {
                throw new DataException("JDBC client configuration at position " + (index + 1) + " is invalid.",
                                        JdbcExceptionTranslator.sanitize("creating a JDBC client configuration",
                                                                         failure));
            }
        }
        JdbcClientConfigSupport.validateAll(clients);
        return clients.stream()
                .map(Service.QualifiedInstance::create)
                .toList();
    }
}
