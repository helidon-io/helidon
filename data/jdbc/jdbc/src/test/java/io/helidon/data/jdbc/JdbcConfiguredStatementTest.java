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

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcConfiguredStatementTest {

    @Test
    void copiesConfigurationAndProvidesExactClientLocalLookup() {
        DataSource source = mock(DataSource.class);
        Map<String, String> definitions = new HashMap<>(Map.of("find", "select :secret"));
        JdbcClientConfig config = JdbcClientConfig.builder().dataSource(source).statements(definitions).buildPrototype();
        definitions.put("find", "select :replacement");
        JdbcClient client = config.build();
        assertThat(config.statements(), is(Map.of("find", "select :secret")));
        assertThrows(UnsupportedOperationException.class, () -> config.statements().put("x", "select 1"));
        assertThat(config.toString(), not(containsString("secret")));
        assertThat(JdbcClientConfig.builder().from(config).toString(), not(containsString("secret")));
        assertThat(client.createNamed("find"), not(sameInstance(client.createNamed("find"))));
        client.createNamed("find").bind("secret", "VALUE_CANARY");
        assertThrows(DataException.class, () -> client.createNamed("FIND"));
        assertThrows(DataException.class, () -> client.createNamed(" find"));
        assertThrows(IllegalArgumentException.class, () -> client.createNamed(" "));
        assertThrows(NullPointerException.class, () -> client.createNamed(null));
        JdbcClient other = JdbcClient.builder().dataSource(source).statements(definitions).build();
        assertThrows(IllegalArgumentException.class, () -> other.createNamed("find").bind("secret", "x"));
        assertThrows(DataException.class, () -> JdbcClient.builder().dataSource(source).build().createNamed("find"));
        verifyZeroInteractions(source);
    }

    @Test
    void configurationAndBuilderHaveEquivalentCatalogs() {
        Config node = Config.builder().sources(ConfigSources.create(Map.of(
                "data-source", "test", "statements.find", "select :value"))).build();
        JdbcClientConfig configured = JdbcClientConfig.builder().config(node).buildPrototype();
        JdbcClientConfig built = JdbcClientConfig.builder().dataSourceName("test")
                .statements(Map.of("find", "select :value")).buildPrototype();
        assertThat(configured.statements(), is(built.statements()));
        assertThat(configured.toString(), not(containsString("select :value")));
    }

    @Test
    void validatesCatalogsAndProtectedMarkersWithoutAcquisition() {
        DataSource source = mock(DataSource.class);
        for (String sql : new String[] {" ", "select ?", "select :value, ?", "select 'unterminated",
                "select /* nested /* comment */ */ 1", "select :value.field", "select 1--ambiguous"}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> JdbcClient.builder().dataSource(source).statements(Map.of("name", sql)).build());
            if (!sql.isBlank()) {
                assertThat(failure.toString(), not(containsString(sql)));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> JdbcClient.builder().dataSource(source)
                .statements(Map.of(" ", "select 1")).build());
        JdbcClient client = JdbcClient.builder().dataSource(source).statements(Map.of("protected",
                "select ':fake ?', nq'[ :fake ? ]', $$:fake ?$$, `:fake ?`, \":fake ?\", :𐐀value "
                        + "/* :fake ? */ -- :fake ?\n, :𐐀value")).build();
        client.createNamed("protected").bind("𐐀value", 1);
        assertThrows(IllegalArgumentException.class, () -> client.createNamed("protected").bind("fake", 1));
        verifyZeroInteractions(source);
    }

    @Test
    void validatesEverySlotBeforeMutationAndPreservesPositionalNullBridge() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("update T set A = ?, B = ?, C = ?")).thenReturn(prepared);
        when(prepared.getLargeUpdateCount()).thenReturn(1L, -1L);
        JdbcClient client = JdbcClient.builder().dataSource(source).statements(Map.of("update",
                "update T set A = :value, B = :other, C = :value")).build();
        JdbcClient.Statement stage = client.createNamed("update");
        GeneratedJdbcData.bindNull(stage, 3, JDBCType.VARCHAR);
        assertThrows(IllegalArgumentException.class, () -> GeneratedJdbcData.bindNull(stage, "value", JDBCType.VARCHAR));
        // Slot one must remain unassigned after the later duplicate slot rejects the named assignment.
        stage.bind(1, "first");
        assertThrows(IllegalArgumentException.class, () -> GeneratedJdbcData.bindNull(stage, "other", JDBCType.STRUCT));
        assertThrows(IllegalArgumentException.class, () -> GeneratedJdbcData.bindNull(stage, "absent", JDBCType.VARCHAR));
        assertThrows(IllegalStateException.class, stage::execute);
        assertThrows(NullPointerException.class, () -> stage.bind("other", null));
        assertThat(GeneratedJdbcData.bindNull(stage, "other", JDBCType.INTEGER), sameInstance(stage));
        verifyZeroInteractions(source);
        assertThat(stage.execute(), is(1L));
        verify(prepared).setString(1, "first");
        verify(prepared).setNull(2, JDBCType.INTEGER.getVendorTypeNumber());
        verify(prepared).setNull(3, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(prepared).close();
        verify(connection).close();
        assertThrows(IllegalStateException.class, () -> stage.bind("other", 1));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.bindNull(stage, (String) null, JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.bindNull(stage, "value", null));
        assertThrows(NullPointerException.class, () -> stage.bind(null, 1));
    }

    @Test
    void sanitizesConfiguredInfrastructureFailuresAndReleasesResources() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("update T set A = ? /* SQL_CANARY */")).thenReturn(prepared);
        when(prepared.execute()).thenThrow(new SQLException("DRIVER_CANARY SQL_CANARY VALUE_CANARY", "42000", 17));
        JdbcClient client = JdbcClient.builder().dataSource(source).statements(Map.of("name",
                "update T set A = :value /* SQL_CANARY */")).build();
        DataException failure = assertThrows(DataException.class,
                () -> client.createNamed("name").bind("value", "VALUE_CANARY").execute());
        for (Throwable current = failure; current != null; current = current.getCause()) {
            assertThat(current.toString(), not(containsString("CANARY")));
            for (Throwable suppressed : current.getSuppressed()) {
                assertThat(suppressed.toString(), not(containsString("CANARY")));
            }
        }
        verify(prepared).close();
        verify(connection).close();
    }

    @Test
    void repeatsValuesAndTypedNullsWithoutPrematureAcquisition() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("update T set A = ?, B = ?")).thenReturn(prepared);
        when(prepared.getLargeUpdateCount()).thenReturn(1L, -1L);
        JdbcClient client = JdbcClient.builder().dataSource(source).statements(Map.of("repeat",
                "update T set A = :value, B = :value")).build();
        JdbcClient.Statement stage = client.createNamed("repeat");
        GeneratedJdbcData.bindNull(stage, "value", JDBCType.VARCHAR);
        assertThrows(IllegalArgumentException.class, () -> stage.bind("value", "VALUE_CANARY"));
        verifyZeroInteractions(source);
        stage.execute();
        verify(prepared).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(prepared).setNull(2, JDBCType.VARCHAR.getVendorTypeNumber());
        assertThrows(IllegalStateException.class, () -> client.create("select ?").bind("value", 1));
    }
}
