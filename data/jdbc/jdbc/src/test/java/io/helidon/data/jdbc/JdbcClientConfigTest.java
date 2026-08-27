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

import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.SqlConfig;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcClientConfigTest {

    /**
     * Verifies the public builder retains its effective immutable
     * configuration without accessing the supplied data source.
     */
    @Test
    void buildsClientFromExistingDataSource() {
        DataSource dataSource = mock(DataSource.class);

        JdbcClient client = JdbcClient.builder()
                .dataSource(dataSource)
                .build();

        assertThat(client.prototype().name(), is(Service.Named.DEFAULT_NAME));
        assertThat(client.prototype().dataSourceInstance().orElseThrow(), sameInstance(dataSource));
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies the public factory retains the exact immutable configuration
     * supplied by the application.
     */
    @Test
    void createsClientFromImmutableConfiguration() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientConfig config = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource(dataSource)
                .buildPrototype();

        JdbcClient client = JdbcClient.create(config);

        assertThat(client.prototype(), sameInstance(config));
        assertThat(client.prototype().name(), is("inventory"));
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies the runtime type consumer factory delegates through the same
     * public builder and construction path.
     */
    @Test
    void createsClientFromBuilderConsumer() {
        DataSource dataSource = mock(DataSource.class);

        JdbcClient client = JdbcClient.create(builder -> builder
                .name("reporting")
                .dataSource(dataSource));

        assertThat(client.prototype().name(), is("reporting"));
        assertThat(client.prototype().dataSourceInstance().orElseThrow(), sameInstance(dataSource));
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies the client configuration retains the shared SQL source choices
     * while a data source object remains programmatic only.
     */
    @Test
    void preservesSqlConfigurationSourcesWithoutReadingDataSourceObjects() {
        String sensitiveValue = "jdbc:example://private-host/secret-database";
        Config configNode = Config.just(ConfigSources.create(Map.of(
                "name", "reporting",
                "data-source", "reporting-source",
                "data-source-instance", sensitiveValue)));
        JdbcClientConfig namedConfig = JdbcClientConfig.create(configNode);
        JdbcClientConfig directConfig = JdbcClientConfig.builder()
                .connection(connection -> connection.url("jdbc:example:local"))
                .buildPrototype();
        SqlConfig sqlConfig = namedConfig;

        assertThat(sqlConfig, sameInstance(namedConfig));
        assertThat(namedConfig.dataSource().orElseThrow(), is("reporting-source"));
        assertThat(namedConfig.dataSourceInstance().isEmpty(), is(true));
        assertThat(directConfig.connection().orElseThrow().url(), is("jdbc:example:local"));
    }

    /**
     * Verifies invalid source selection produces a simple diagnostic without
     * including configuration values or object details.
     */
    @Test
    void rejectsInvalidSourceSelectionWithoutExposingConfiguration() {
        String sensitiveValue = "jdbc:example://private-host/secret-database";
        Config configNode = Config.just(ConfigSources.create(Map.of(
                "name", sensitiveValue,
                "data-source-instance", sensitiveValue)));
        DataSource dataSource = mock(DataSource.class);

        DataException missingFailure = assertThrows(DataException.class, () -> JdbcClientConfig.create(configNode));
        DataException conflictFailure = assertThrows(
                DataException.class,
                () -> JdbcClient.builder()
                        .dataSource("private-source")
                        .dataSource(dataSource)
                        .buildPrototype());

        assertThat(missingFailure.getMessage(), is("A JDBC client requires exactly one connection source."));
        assertThat(conflictFailure.getMessage(), is("A JDBC client requires exactly one connection source."));
        assertThat(missingFailure.getMessage(), not(containsString(sensitiveValue)));
        assertThat(conflictFailure.getMessage(), not(containsString("private-source")));
    }

    /**
     * Verifies every combination of multiple connection sources is rejected
     * before a client can be constructed.
     */
    @Test
    void rejectsEveryConflictingConnectionSourceCombination() {
        DataSource dataSource = mock(DataSource.class);

        assertSourceConflict(() -> JdbcClient.builder()
                .dataSource(dataSource)
                .connection(connection -> connection.url("jdbc:example:local"))
                .buildPrototype());
        assertSourceConflict(() -> JdbcClient.builder()
                .dataSource("inventory-source")
                .connection(connection -> connection.url("jdbc:example:local"))
                .buildPrototype());
        assertSourceConflict(() -> JdbcClient.builder()
                .dataSource(dataSource)
                .dataSource("inventory-source")
                .connection(connection -> connection.url("jdbc:example:local"))
                .buildPrototype());
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies invalid client and source names and incomplete direct
     * connection settings fail during configuration validation.
     */
    @Test
    void rejectsInvalidConnectionSourceDetails() {
        DataSource dataSource = mock(DataSource.class);

        DataException blankClientName = assertThrows(
                DataException.class,
                () -> JdbcClient.builder().name("  ").dataSource(dataSource).buildPrototype());
        DataException blankDataSourceName = assertThrows(
                DataException.class,
                () -> JdbcClient.builder().dataSource("  ").buildPrototype());
        DataException blankUrl = assertThrows(
                DataException.class,
                () -> JdbcClient.builder().connection(connection -> connection.url("  ")).buildPrototype());
        DataException blankDriver = assertThrows(
                DataException.class,
                () -> JdbcClient.builder()
                        .connection(connection -> connection
                                .url("jdbc:example:local")
                                .jdbcDriverClassName("  "))
                        .buildPrototype());

        assertThat(blankClientName.getMessage(), is("A JDBC client name must not be blank."));
        assertThat(blankDataSourceName.getMessage(), is("A JDBC data source name must not be blank."));
        assertThat(blankUrl.getMessage(), is("The direct JDBC connection URL must not be blank."));
        assertThat(blankDriver.getMessage(), is("The JDBC driver class name must not be blank."));
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies typed provider options remain part of the effective client
     * configuration when a public client is constructed.
     */
    @Test
    void preservesTypedProviderOptions() {
        DataSource dataSource = mock(DataSource.class);
        JdbcPropertiesConfig properties = JdbcPropertiesConfig.builder()
                .jdbc(JdbcProviderPropertiesConfig.builder()
                              .parameterCountCache(JdbcParameterCountCacheConfig.builder()
                                                           .capacity(17)
                                                           .maxSqlLength(2_048)
                                                           .buildPrototype())
                              .buildPrototype())
                .buildPrototype();

        JdbcClient client = JdbcClient.builder()
                .dataSource(dataSource)
                .properties(properties)
                .build();

        assertThat(client.prototype().properties(), sameInstance(properties));
        assertThat(client.prototype().properties().jdbc().parameterCountCache().capacity(), is(17));
        assertThat(client.prototype().properties().jdbc().parameterCountCache().maxSqlLength(), is(2_048));
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies direct driver resolution failures do not expose connection
     * settings through the public construction path.
     */
    @Test
    void sanitizesDirectDriverResolutionFailure() {
        String sensitiveUrl = "jdbc:missing://private-host/database?token=private-token";
        String sensitiveDriver = "example.private.PrivateTokenDriver";

        DataException failure = assertThrows(
                DataException.class,
                () -> JdbcClient.builder()
                        .name("inventory")
                        .connection(connection -> connection
                                .url(sensitiveUrl)
                                .jdbcDriverClassName(sensitiveDriver))
                        .build());

        assertThat(failure.getMessage(),
                   is("JDBC client 'inventory' could not resolve a JDBC driver for its direct connection."));
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            diagnostic.append(current.getMessage()).append('\n');
            for (Throwable suppressed : current.getSuppressed()) {
                diagnostic.append(suppressed.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        assertThat(diagnostic.toString(), not(containsString(sensitiveUrl)));
        assertThat(diagnostic.toString(), not(containsString(sensitiveDriver)));
        assertThat(diagnostic.toString(), not(containsString("private-token")));
    }

    /**
     * Verifies generated diagnostic text does not reveal details from the
     * programmatically supplied data source.
     */
    @Test
    void redactsDataSourceFromConfigurationText() {
        String sensitiveValue = "jdbc:example://private-host/secret-database";
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.toString()).thenReturn(sensitiveValue);
        JdbcClientConfig.Builder builder = JdbcClient.builder().dataSource(dataSource);
        JdbcClientConfig config = builder.buildPrototype();

        assertThat(builder.toString(), not(containsString(sensitiveValue)));
        assertThat(config.toString(), not(containsString(sensitiveValue)));
        assertThat(builder.toString(), containsString("****"));
        assertThat(config.toString(), containsString("****"));
    }

    /**
     * Verifies public construction boundaries reject null input with simple
     * diagnostics that contain no application state.
     */
    @Test
    void rejectsNullConstructionInputs() {
        NullPointerException configFailure = assertThrows(
                NullPointerException.class,
                () -> JdbcClient.create((JdbcClientConfig) null));
        NullPointerException consumerFailure = assertThrows(
                NullPointerException.class,
                () -> JdbcClient.create((Consumer<JdbcClientConfig.Builder>) null));
        NullPointerException dataSourceFailure = assertThrows(
                NullPointerException.class,
                () -> JdbcClient.builder().dataSource((DataSource) null));

        assertThat(configFailure.getMessage(), is("The JDBC client configuration must not be null."));
        assertThat(consumerFailure.getMessage(), is("The JDBC client builder consumer must not be null."));
        assertThat(dataSourceFailure.getMessage(), is("The data source must not be null."));
    }

    private static void assertSourceConflict(Runnable construction) {
        DataException failure = assertThrows(DataException.class, construction::run);
        assertThat(failure.getMessage(), is("A JDBC client requires exactly one connection source."));
    }
}
