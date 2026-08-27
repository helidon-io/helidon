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
package io.helidon.data.jdbc.tests;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientConfig;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

class JdbcClientRegistryTest {

    /**
     * Verifies one programmatic replacement call publishes multiple client
     * configurations in order and creates the corresponding managed clients.
     */
    @Test
    void installsMultipleProgrammaticConfigurations() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientConfig pokemon = JdbcClient.builder()
                .name("pokemon")
                .dataSource(dataSource)
                .buildPrototype();
        JdbcClientConfig audit = JdbcClient.builder()
                .name("audit")
                .dataSource(dataSource)
                .buildPrototype();
        ServiceRegistryManager manager = manager();
        try {
            Services.set(JdbcClientConfig.class, pokemon, audit);

            assertThat(Services.all(JdbcClientConfig.class), is(List.of(pokemon, audit)));
            assertThat(Services.getNamed(JdbcClient.class, "pokemon").prototype(), sameInstance(pokemon));
            assertThat(Services.getNamed(JdbcClient.class, "audit").prototype(), sameInstance(audit));
            verifyZeroInteractions(dataSource);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Verifies YAML definitions are the effective configurations when no
     * programmatic replacement is installed.
     */
    @Test
    void loadsYamlOnlyConfigurations() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "pokemon",
                "data.clients.jdbc.0.data-source", "pokemon-source",
                "data.clients.jdbc.1.name", "audit",
                "data.clients.jdbc.1.data-source", "audit-source")));
        ServiceRegistryManager manager = manager();
        try {
            Services.set(Config.class, config);

            assertThat(Services.all(JdbcClientConfig.class)
                               .stream()
                               .map(JdbcClientConfig::name)
                               .toList(),
                       is(List.of("pokemon", "audit")));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Verifies a programmatic configuration set replaces YAML candidates
     * instead of merging with them.
     */
    @Test
    void programmaticConfigurationsReplaceYamlDefinitions() {
        DataSource dataSource = mock(DataSource.class);
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "yaml-client",
                "data.clients.jdbc.0.data-source", "yaml-source")));
        JdbcClientConfig programmatic = JdbcClient.builder()
                .name("programmatic-client")
                .dataSource(dataSource)
                .buildPrototype();
        ServiceRegistryManager manager = manager();
        try {
            Services.set(Config.class, config);
            Services.set(JdbcClientConfig.class, programmatic);

            assertThat(Services.all(JdbcClientConfig.class), is(List.of(programmatic)));
            assertThat(Services.getNamed(JdbcClient.class, "programmatic-client").prototype(),
                       sameInstance(programmatic));
            verifyZeroInteractions(dataSource);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Verifies application code registers configurations rather than built
     * clients before resolving a generated repository.
     */
    @Test
    void createsGeneratedRepositoryFromProgrammaticConfiguration() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientConfig config = JdbcClient.builder()
                .dataSource(dataSource)
                .buildPrototype();
        ServiceRegistryManager manager = manager();
        try {
            Services.set(JdbcClientConfig.class, config);

            OverflowRepository repository = Services.get(OverflowRepository.class);

            assertThat(repository == null, is(false));
            verifyZeroInteractions(dataSource);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Verifies the Service Registry rejects replacement after the client
     * configuration contract has been initialized.
     */
    @Test
    void rejectsLateProgrammaticReplacement() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientConfig config = JdbcClient.builder()
                .dataSource(dataSource)
                .buildPrototype();
        ServiceRegistryManager manager = manager();
        try {
            Services.all(JdbcClientConfig.class);

            assertThrows(ServiceRegistryException.class,
                         () -> Services.set(JdbcClientConfig.class, config));
        } finally {
            manager.shutdown();
        }
    }

    private static ServiceRegistryManager manager() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        return manager;
    }
}
