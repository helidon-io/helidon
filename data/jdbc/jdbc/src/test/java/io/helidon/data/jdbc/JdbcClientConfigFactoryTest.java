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

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcClientConfigFactoryTest {

    /**
     * Verifies YAML client definitions are published in their configured list
     * order with the default name applied when it is omitted.
     */
    @Test
    void createsOrderedConfigurationsFromYaml() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "inventory",
                "data.clients.jdbc.0.data-source", "inventory-source",
                "data.clients.jdbc.1.data-source", "default-source")));
        JdbcClientConfigFactory factory = new JdbcClientConfigFactory(() -> config);

        List<JdbcClientConfig> configurations = factory.services()
                .stream()
                .map(Service.QualifiedInstance::get)
                .toList();

        assertThat(configurations.stream().map(JdbcClientConfig::name).toList(),
                   is(List.of("inventory", Service.Named.DEFAULT_NAME)));
        assertThat(configurations.stream().map(value -> value.dataSource().orElseThrow()).toList(),
                   is(List.of("inventory-source", "default-source")));
    }

    /**
     * Verifies duplicate YAML names fail before client publication.
     */
    @Test
    void rejectsDuplicateYamlNames() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "inventory",
                "data.clients.jdbc.0.data-source", "first-source",
                "data.clients.jdbc.1.name", "inventory",
                "data.clients.jdbc.1.data-source", "second-source")));

        DataException failure = assertThrows(
                DataException.class,
                () -> new JdbcClientConfigFactory(() -> config).services());

        assertThat(failure.getMessage(),
                   is("More than one JDBC client configuration uses the name 'inventory'."));
    }

    /**
     * Verifies duplicate default definitions use the public client name in
     * the diagnostic.
     */
    @Test
    void reportsDuplicateDefaultClient() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.data-source", "first-source",
                "data.clients.jdbc.1.data-source", "second-source")));

        DataException failure = assertThrows(
                DataException.class,
                () -> new JdbcClientConfigFactory(() -> config).services());

        assertThat(failure.getMessage(), is("The Default JDBC Client is configured more than once."));
    }

    /**
     * Verifies a configuration source failure does not retain source details.
     */
    @Test
    void sanitizesConfigurationSourceFailure() {
        String sensitiveDetail = "private-configuration-source-detail";
        JdbcClientConfigFactory factory = new JdbcClientConfigFactory(
                () -> {
                    throw new IllegalStateException(sensitiveDetail);
                });

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("JDBC client configuration could not be read."));
        assertThat(failure.getCause().toString(), not(containsString(sensitiveDetail)));
    }

    /**
     * Verifies configuration creation failures identify only the list
     * position and do not expose direct connection settings.
     */
    @Test
    void sanitizesInvalidYamlConfiguration() {
        String sensitiveUrl = "jdbc:example://private-host/database?token=private-token";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.connection.url", sensitiveUrl,
                "data.clients.jdbc.0.connection.jdbc-driver-class-name", "  ")));

        DataException failure = assertThrows(
                DataException.class,
                () -> new JdbcClientConfigFactory(() -> config).services());

        assertThat(failure.getMessage(), is("JDBC client configuration at position 1 is invalid."));
        assertThat(failure.toString(), not(containsString(sensitiveUrl)));
        assertThat(failure.toString(), not(containsString("private-token")));
    }
}
