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

import io.helidon.common.Errors.ErrorMessagesException;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
     * Verifies a configuration service failure does not retain registry or
     * source details.
     */
    @Test
    void sanitizesConfigurationServiceFailure() {
        String sensitiveDetail = "private-configuration-source-detail";
        JdbcClientConfigFactory factory = new JdbcClientConfigFactory(
                () -> {
                    throw new ServiceRegistryException(sensitiveDetail,
                                                       new IllegalStateException("private-configuration-cause"));
                });

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("JDBC client configuration could not be obtained."));
        assertThat(diagnostic(failure), not(containsString(sensitiveDetail)));
        assertThat(diagnostic(failure), not(containsString("private-configuration-cause")));
    }

    /**
     * Verifies an unrelated runtime failure from a configuration supplier is
     * not reclassified as a configuration or registry failure.
     */
    @Test
    void propagatesUnexpectedConfigurationSupplierFailure() {
        IllegalStateException unexpected = new IllegalStateException("unexpected application failure");
        JdbcClientConfigFactory factory = new JdbcClientConfigFactory(() -> {
            throw unexpected;
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, factory::services);

        assertThat(failure, sameInstance(unexpected));
    }

    /**
     * Verifies a configuration parsing failure is sanitized before it crosses
     * the JDBC client configuration boundary.
     */
    @Test
    void sanitizesConfigurationParsingFailure() {
        String sensitiveDetail = "private-configuration-value";
        Config rootConfig = mock(Config.class);
        Config clientsConfig = mock(Config.class);
        when(rootConfig.get(JdbcClientConfigFactory.CONFIG_KEY)).thenReturn(clientsConfig);
        when(clientsConfig.asNodeList()).thenThrow(
                new ConfigException(sensitiveDetail, new IllegalStateException("private-configuration-cause")));

        DataException failure = assertThrows(
                DataException.class,
                () -> new JdbcClientConfigFactory(() -> rootConfig).services());

        assertThat(failure.getMessage(), is("JDBC client configuration could not be read."));
        assertThat(diagnostic(failure), not(containsString(sensitiveDetail)));
        assertThat(diagnostic(failure), not(containsString("private-configuration-cause")));
    }

    /**
     * Verifies generated builder validation failures remain actionable
     * without exposing confidential client configuration values.
     */
    @Test
    void propagatesInvalidYamlConfigurationFailure() {
        String sensitiveUser = "private-database-user";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.connection.username", sensitiveUser,
                "data.clients.jdbc.0.connection.password", "private-database-password")));

        ErrorMessagesException failure = assertThrows(
                ErrorMessagesException.class,
                () -> new JdbcClientConfigFactory(() -> config).services());

        assertThat(failure.getMessage(), containsString("Property \"url\" must not be null, but not set"));
        assertThat(diagnostic(failure), not(containsString(sensitiveUser)));
        assertThat(diagnostic(failure), not(containsString("private-database-password")));
    }

    private static String diagnostic(Throwable failure) {
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            diagnostic.append(current).append('\n');
            current = current.getCause();
        }
        return diagnostic.toString();
    }
}
