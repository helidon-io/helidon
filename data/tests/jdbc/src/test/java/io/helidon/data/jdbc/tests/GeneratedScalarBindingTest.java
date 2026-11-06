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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigException;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientConfig;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class GeneratedScalarBindingTest {

    /**
     * Proves codegen shares the nullable decision across every supported
     * reference scalar while retaining one invocation per physical marker.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatedRepositorySharesNullableBindHelperAcrossPhysicalMarkers() throws Exception {
        String source = generatedSource(ScalarBindingRepository.class);

        assertThat(source.split("bindParameter\\(jdbcStatement,", -1).length - 1, is(16));
        assertThat(source, containsString("void bindParameter("));
        assertThat(source, containsString("if (value == null) {"));
        assertThat(source, containsString("GeneratedJdbcData.bindNull(statement, index, nullType);"));
        assertThat(source, containsString("statement.bind(index, value);"));
        assertThat(source, containsString("GeneratedJdbcData.createGenerated(jdbcClient, SQL_BIND_ALL, 16);"));
        assertThat(source, not(containsString("if (booleanValue == null) {")));

        String[] parameterNames = {
                "booleanValue",
                "byteValue",
                "shortValue",
                "integerValue",
                "longValue",
                "floatValue",
                "doubleValue",
                "decimalValue",
                "stringValue",
                "bytesValue",
                "localDateValue",
                "localTimeValue",
                "localDateTimeValue",
                "dateValue",
                "timeValue",
                "timestampValue"
        };
        JDBCType[] nullTypes = {
                JDBCType.BOOLEAN,
                JDBCType.TINYINT,
                JDBCType.SMALLINT,
                JDBCType.INTEGER,
                JDBCType.BIGINT,
                JDBCType.REAL,
                JDBCType.DOUBLE,
                JDBCType.DECIMAL,
                JDBCType.VARCHAR,
                JDBCType.VARBINARY,
                JDBCType.DATE,
                JDBCType.TIME,
                JDBCType.TIMESTAMP,
                JDBCType.DATE,
                JDBCType.TIME,
                JDBCType.TIMESTAMP
        };
        for (int index = 0; index < parameterNames.length; index++) {
            assertThat(source,
                       containsString("bindParameter(jdbcStatement, " + (index + 1) + ", "
                                              + parameterNames[index] + ", JDBCType."
                                              + nullTypes[index] + ");"));
        }
    }

    /**
     * Proves a repository with one nullable physical bind keeps its branch
     * inline instead of adding a one-use private helper.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatedRepositoryKeepsOneNullableBindInline() throws Exception {
        String source = generatedSource(OverflowRepository.class);

        assertThat(source, containsString("if (name == null) {"));
        assertThat(source, containsString("GeneratedJdbcData.bindNull(jdbcStatement, 1, JDBCType.VARCHAR);"));
        assertThat(source, not(containsString("void bindParameter(")));
    }

    /**
     * Proves a repository contract using the preferred helper name receives a
     * compilable generated implementation with a collision-free helper name,
     * and that a repeated parameter is bound at every physical marker.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatedRepositoryAvoidsNullableBindHelperNameCollision() throws Exception {
        String source = generatedSource(NullableBindingCollisionRepository.class);

        assertThat(source.split("bindParameter2\\(jdbcStatement,", -1).length - 1, is(3));
        assertThat(source, containsString("void bindParameter2("));
        assertThat(source, containsString("bindParameter2(jdbcStatement, 1, name, JDBCType.VARCHAR);"));
        assertThat(source, containsString("bindParameter2(jdbcStatement, 2, label, JDBCType.VARCHAR);"));
        assertThat(source, containsString("bindParameter2(jdbcStatement, 3, name, JDBCType.VARCHAR);"));
    }

    /**
     * Proves generated non-null calls use the supported statement contract
     * when an alternate client receives the generated repository.
     */
    @Test
    void generatedRepositoryBindsEveryReferenceScalarThroughTheSupportedContract() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.bind(anyInt(), any())).thenReturn(statement);
        ScalarBindingRepository repository = new ScalarBindingRepository__Jdbc(client);

        Object[] values = {
                Boolean.TRUE,
                (byte) 2,
                (short) 3,
                4,
                5L,
                6.5F,
                7.5D,
                new BigDecimal("8.50"),
                "value",
                new byte[] {9, 10},
                LocalDate.of(2026, 7, 27),
                LocalTime.of(10, 11, 12),
                LocalDateTime.of(2026, 7, 27, 10, 11, 12),
                Date.valueOf("2026-07-27"),
                Time.valueOf("10:11:12"),
                Timestamp.valueOf("2026-07-27 10:11:12")
        };
        invoke(repository, values);

        verify(client).create(anyString());
        for (int index = 0; index < values.length; index++) {
            verify(statement).bind(index + 1, values[index]);
        }
        verify(statement).execute();
    }

    @Test
    void missingAndAdditionalInputsFailBeforeAcquisitionIncludingNullableInputs() {
        DataSource source = mock(DataSource.class);
        for (String sql : List.of("select :name", "select :name, :email, :additional", "select ?", "select ?, ?, ?")) {
            assertThrows(IllegalArgumentException.class, () -> new ConfiguredBindingRepository__Jdbc(
                    JdbcClient.builder().dataSource(source).build(), configuredSql(sql)));
        }
        verifyZeroInteractions(source);
    }

    @Test
    void renamedGeneratedParametersRemainBoundValues() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        String sql = "select ?, ?, ?, ?, ?, ?, ?";
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("mapped");
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        ConfiguredBindingRepository repository = new ConfiguredBindingRepository__Jdbc(client,
                configuredSql("select :name, :email"));
        assertThat(repository.collision("SQL_CANARY", "CLIENT_CANARY", "STATEMENT_CANARY", "RESULT", "ROWS",
                "CONFIG", "INPUTS"),
                is("mapped"));
        verify(statement).setString(1, "ROWS");
        verify(statement).setString(2, "CLIENT_CANARY");
        verify(statement).setString(3, "SQL_CANARY");
        verify(statement).setString(4, "RESULT");
        verify(statement).setString(5, "STATEMENT_CANARY");
        verify(statement).setString(6, "CONFIG");
        verify(statement).setString(7, "INPUTS");
        // Advancing the JDBC result channel closes the current result set under the JDBC contract.
        verify(statement).getMoreResults();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * The same compiled bindings use deployment positions and declared SQL null types.
     */
    @Test
    void bindsNullableInputsWithEitherDeploymentMarkerStyle() throws Exception {
        for (String sql : List.of("select :email, :name, :email", "select ?, ?")) {
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet rows = mock(ResultSet.class);
            ResultSetMetaData metadata = mock(ResultSetMetaData.class);
            String rewritten = sql.startsWith("select :") ? "select ?, ?, ?" : sql;
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(rewritten)).thenReturn(statement);
            when(statement.execute()).thenReturn(true);
            when(statement.getResultSet()).thenReturn(rows);
            when(statement.getLargeUpdateCount()).thenReturn(-1L);
            when(rows.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(1);
            when(metadata.getColumnLabel(1)).thenReturn("VALUE");
            ConfiguredBindingRepository repository = new ConfiguredBindingRepository__Jdbc(
                    JdbcClient.builder().dataSource(source).build(), configuredSql(sql));
            verifyZeroInteractions(source);
            assertThat(repository.find("name", null), is(List.of()));
            if (sql.startsWith("select :")) {
                verify(statement).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
                verify(statement).setString(2, "name");
                verify(statement).setNull(3, JDBCType.VARCHAR.getVendorTypeNumber());
            } else {
                verify(statement).setString(1, "name");
                verify(statement).setNull(2, JDBCType.VARCHAR.getVendorTypeNumber());
            }
            verify(statement).close();
            verify(connection).close();
        }
    }

    @Test
    void configurationFailuresDoNotExposeSqlOrRawExceptionGraphs() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        for (Config config : List.of(Config.empty(),
                Config.just(ConfigSources.create(Map.of("application.sql.find.child", "SQL_CANARY"))),
                Config.just(ConfigSources.create(Map.of("application.sql.find.0", "SQL_CANARY"))))) {
            DataException failure = assertThrows(DataException.class,
                    () -> new ConfiguredBindingRepository__Jdbc(client, config));
            assertThat(failure.toString(), not(containsString("SQL_CANARY")));
            assertThat(failure.getCause(), nullValue());
            assertThat(failure.getSuppressed().length, is(0));
        }
        Config failedLookup = Config.builder().disableValueResolving()
                .sources(ConfigSources.create(Map.of("application.sql.find", "SQL_CANARY")))
                .addFilter((_, _) -> {
                    ConfigException failure = new ConfigException("SQL_CANARY", new IllegalStateException("CAUSE_CANARY"));
                    failure.addSuppressed(new IllegalArgumentException("SUPPRESSED_CANARY"));
                    throw failure;
                }).build();
        DataException failure = assertThrows(DataException.class,
                () -> new ConfiguredBindingRepository__Jdbc(client, failedLookup));
        assertThat(failure.toString(), not(containsString("CANARY")));
        assertThat(failure.getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(0));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfiguredBindingRepository__Jdbc(client, configuredSql(" ")));
        Map<String, String> blankDefault = new HashMap<>(configuredSql("select :name, :email").asMap().get());
        blankDefault.put("application.sql.defaulted", " ");
        assertThrows(IllegalArgumentException.class, () -> new ConfiguredBindingRepository__Jdbc(client,
                Config.just(ConfigSources.create(blankDefault))));
        verifyZeroInteractions(source);
    }

    /**
     * Known multiline defaults are preserved while a configured override can change marker style.
     */
    @Test
    void resolvesMultilineDefaultsAndOverridesWithoutAnotherExpressionPass() {
        JdbcClient client = mock(JdbcClient.class);
        new ConfiguredBindingRepository__Jdbc(client, configuredSql("select :name, :email"));
        verify(client).prepare("select :value /* } 'quote'\nretained lines */", List.of("value"));
        Config config = Config.just(ConfigSources.create(Map.of(
                "application.sql.find", "select :name, :email",
                "application.sql.keys", "insert into T values (:value, :data__value)",
                "application.sql.inherited", "select :value",
                "application.sql.collision", "select :rows, :jdbcClient, :SQL_COLLISION, :result, :statement, :config, :inputNames",
                "application.sql.defaulted", "select ? /* SQL_CANARY */")));
        JdbcClient override = mock(JdbcClient.class);
        new ConfiguredBindingRepository__Jdbc(override, config);
        verify(override).prepare("select ? /* SQL_CANARY */", List.of("value"));
        Map<String, String> values = new HashMap<>(config.asMap().get());
        String opaqueSql = "select :value, '\\${literal}'";
        values.put("application.sql.defaulted", opaqueSql);
        Config filtered = Config.builder().disableValueResolving().sources(ConfigSources.create(values))
                .addFilter((key, value) -> key.toString().endsWith("defaulted") ? value + " /* filtered */" : value)
                .build();
        JdbcClient opaque = mock(JdbcClient.class);
        new ConfiguredBindingRepository__Jdbc(opaque, filtered);
        verify(opaque).prepare(opaqueSql + " /* filtered */", List.of("value"));

    }

    /**
     * Each registry resolves its own root configuration even when another registry is global.
     */
    @Test
    void isolatesConfigurationAndSourcesAcrossLiveRegistries() throws Exception {
        DataSource firstSource = mock(DataSource.class);
        DataSource secondSource = mock(DataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        when(firstSource.getConnection()).thenReturn(firstConnection);
        when(secondSource.getConnection()).thenReturn(secondConnection);
        when(firstConnection.getAutoCommit()).thenReturn(true);
        when(secondConnection.getAutoCommit()).thenReturn(true);
        when(firstConnection.prepareStatement("select ?, ?, ?")).thenThrow(new SQLException("The fixture rejected preparation."));
        when(secondConnection.prepareStatement("select ?, ?")).thenThrow(new SQLException("The fixture rejected preparation."));
        ServiceRegistryManager first = ServiceRegistryManager.create();
        try {
            GlobalServiceRegistry.registry(first.registry());
            Services.set(Config.class, configuredSql("select :email, :name, :email"));
            Services.setNamed(DataSource.class, firstSource, "shared-source");
            Services.set(JdbcClientConfig.class, JdbcClient.builder().dataSourceName("shared-source").buildPrototype());
            ServiceRegistryManager second = ServiceRegistryManager.create();
            try {
                GlobalServiceRegistry.registry(second.registry());
                Services.set(Config.class, configuredSql("select ?, ?"));
                Services.setNamed(DataSource.class, secondSource, "shared-source");
                Services.set(JdbcClientConfig.class, JdbcClient.builder().dataSourceName("shared-source").buildPrototype());
                ConfiguredBindingRepository firstRepository = first.registry().get(ConfiguredBindingRepository.class);
                ConfiguredBindingRepository secondRepository = second.registry().get(ConfiguredBindingRepository.class);
                verifyZeroInteractions(firstSource, secondSource);
                assertThrows(DataException.class, () -> firstRepository.find("name", "email"));
                assertThrows(DataException.class, () -> secondRepository.find("name", "email"));
                verify(firstConnection).prepareStatement("select ?, ?, ?");
                verify(secondConnection).prepareStatement("select ?, ?");
                verify(firstConnection).close();
                verify(secondConnection).close();
            } finally {
                second.shutdown();
            }
        } finally {
            first.shutdown();
        }
    }

    /**
     * The same compiled alias contract uses deployment marker order and declared null types.
     */
    @Test
    void bindsConfiguredAliasesAndNullableInputsWithoutUsingJavaNames() throws Exception {
        for (String sql : List.of("select :email, :lookup, :email", "select :lookup, :email")) {
            for (boolean nullLookup : List.of(false, true)) {
                DataSource source = mock(DataSource.class);
                Connection connection = mock(Connection.class);
                PreparedStatement statement = mock(PreparedStatement.class);
                ResultSet rows = mock(ResultSet.class);
                ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                boolean repeated = sql.startsWith("select :email");
                when(source.getConnection()).thenReturn(connection);
                when(connection.getAutoCommit()).thenReturn(true);
                when(connection.prepareStatement(repeated ? "select ?, ?, ?" : "select ?, ?")).thenReturn(statement);
                when(statement.execute()).thenReturn(true);
                when(statement.getResultSet()).thenReturn(rows);
                when(statement.getLargeUpdateCount()).thenReturn(-1L);
                when(rows.getMetaData()).thenReturn(metadata);
                when(metadata.getColumnCount()).thenReturn(1);
                when(metadata.getColumnLabel(1)).thenReturn("VALUE");
                ConfiguredAliasBindingRepository repository = new ConfiguredAliasBindingRepository__Jdbc(
                        JdbcClient.builder().dataSource(source).build(), configuredAliasSql(sql));
                verifyZeroInteractions(source);
                assertThat(repository.find(nullLookup ? null : "VALUE_CANARY", nullLookup ? "email" : null), is(List.of()));
                int lookupPosition = repeated ? 2 : 1;
                int emailPosition = repeated ? 1 : 2;
                if (nullLookup) {
                    verify(statement).setNull(lookupPosition, JDBCType.VARCHAR.getVendorTypeNumber());
                    verify(statement).setString(emailPosition, "email");
                    if (repeated) {
                        verify(statement).setString(3, "email");
                    }
                } else {
                    verify(statement).setString(lookupPosition, "VALUE_CANARY");
                    verify(statement).setNull(emailPosition, JDBCType.VARCHAR.getVendorTypeNumber());
                    if (repeated) {
                        verify(statement).setNull(3, JDBCType.VARCHAR.getVendorTypeNumber());
                    }
                }
                verify(statement).close();
                verify(connection).close();
            }
        }
    }

    /**
     * Alias style and input failures occur at activation without client delegation or acquisition.
     */
    @Test
    void rejectsConfiguredAliasMismatchesAndAlternateClientsAtActivation() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        for (String sql : List.of("select ?, ?", "select :lookup", "select :lookup, :email, :extra",
                "select :lookup, ?", "select 1")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> new ConfiguredAliasBindingRepository__Jdbc(client, configuredAliasSql(sql)));
            assertThat(failure.toString(), not(containsString(sql)));
        }
        JdbcClient alternate = mock(JdbcClient.class);
        assertThrows(UnsupportedOperationException.class, () -> new ConfiguredAliasBindingRepository__Jdbc(
                alternate, configuredAliasSql("select :lookup, :email")));
        verifyZeroInteractions(source, alternate);
    }

    /**
     * Partial aliases preserve unaliased values and generated key null mapping.
     */
    @Test
    void executesConfiguredAliasUpdatesAndGeneratedKeys() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("update T set A = ?, B = ?, C = ?")).thenReturn(update);
        when(connection.prepareStatement(eq("insert into T values (?)"), aryEq(new String[] {"ID"}))).thenReturn(insert);
        when(update.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(insert.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("ID");
        when(keys.next()).thenReturn(true, false);
        when(keys.getLong(1)).thenReturn(42L);
        ConfiguredAliasBindingRepository repository = new ConfiguredAliasBindingRepository__Jdbc(
                JdbcClient.builder().dataSource(source).build(), configuredAliasSql("select :lookup, :email"));
        verifyZeroInteractions(source);
        repository.update(null, "tag");
        verify(update).setString(1, "tag");
        verify(update).setNull(2, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(update).setNull(3, JDBCType.VARCHAR.getVendorTypeNumber());
        assertThat(repository.insert(null), is(Optional.of(42L)));
        verify(insert).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(update).close();
        verify(insert).close();
        verify(keys).close();
    }

    /**
     * Replacement configuration creates new definitions without changing an existing repository or its client.
     */
    @Test
    void retainsConfiguredAliasDefinitionsWhenConfigurationIsReplaced() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("select ?, ?, ?")).thenThrow(new SQLException("SQL_CANARY"));
        when(connection.prepareStatement("select ?, ?")).thenThrow(new SQLException("SQL_CANARY"));
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        ConfiguredAliasBindingRepository first = new ConfiguredAliasBindingRepository__Jdbc(client,
                configuredAliasSql("select :email, :lookup, :email"));
        ConfiguredAliasBindingRepository replacement = new ConfiguredAliasBindingRepository__Jdbc(client,
                configuredAliasSql("select :lookup, :email"));
        verifyZeroInteractions(source);
        DataException oldFailure = assertThrows(DataException.class, () -> first.find("name", null));
        DataException newFailure = assertThrows(DataException.class, () -> replacement.find("name", null));
        assertThat(oldFailure.toString(), not(containsString("CANARY")));
        assertThat(newFailure.toString(), not(containsString("CANARY")));
        verify(connection).prepareStatement("select ?, ?, ?");
        verify(connection).prepareStatement("select ?, ?");
        verify(connection, times(2)).close();
    }

    private static Config configuredAliasSql(String find) {
        return Config.just(ConfigSources.create(Map.of(
                "application.aliases.find", find,
                "application.aliases.update", "update T set A = :tag, B = :SQL_UPDATE, C = :SQL_UPDATE",
                "application.aliases.keys", "insert into T values (:name)")));
    }

    private static Config configuredSql(String find) {
        return Config.just(ConfigSources.create(Map.of(
                "application.sql.find", find,
                "application.sql.keys", "insert into T values (:value, :data__value)",
                "application.sql.inherited", "select :value",
                "application.sql.collision", "select :rows, :jdbcClient, :SQL_COLLISION, :result, :statement, :config, :inputNames")));
    }

    private static void invoke(ScalarBindingRepository repository, Object[] values) {
        repository.bindAll((Boolean) values[0],
                           (Byte) values[1],
                           (Short) values[2],
                           (Integer) values[3],
                           (Long) values[4],
                           (Float) values[5],
                           (Double) values[6],
                           (BigDecimal) values[7],
                           (String) values[8],
                           (byte[]) values[9],
                           (LocalDate) values[10],
                           (LocalTime) values[11],
                           (LocalDateTime) values[12],
                           (Date) values[13],
                           (Time) values[14],
                           (Timestamp) values[15]);
    }

    private static String generatedSource(Class<?> repositoryType) throws Exception {
        Path testClasses = Path.of(GeneratedScalarBindingTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        Path generatedSource = testClasses.getParent()
                .resolve("generated-sources/annotations/")
                .resolve(repositoryType.getName().replace('.', '/') + "__Jdbc.java");
        return Files.readString(generatedSource);
    }
}
