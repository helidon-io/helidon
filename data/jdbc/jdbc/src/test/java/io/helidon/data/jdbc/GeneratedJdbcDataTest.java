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

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

class GeneratedJdbcDataTest {

    /**
     * Verifies that the generated support boundary rejects missing inputs
     * before it delegates to a client or statement.
     */
    @Test
    void generatedSupportRejectsMissingInputs() {
        AlternateStatement statement = new AlternateStatement();

        assertThrows(NullPointerException.class,
                     () -> GeneratedJdbcData.createGenerated(null, "select 1", 0));
        assertThrows(NullPointerException.class,
                     () -> GeneratedJdbcData.bindNull(null, 1, JDBCType.VARCHAR));
        assertThrows(NullPointerException.class,
                     () -> GeneratedJdbcData.bindNull(statement, 1, null));
    }

    /**
     * Verifies that generated statement creation preserves compatibility with
     * an alternate client through its supported creation method.
     */
    @Test
    void generatedCreationFallsBackToTheSupportedAlternateProviderContract() {
        AlternateClient client = new AlternateClient();

        JdbcClient.Statement result = GeneratedJdbcData.createGenerated(client, "select ?", 1);

        assertThat(result, sameInstance(client.statement));
        assertThat(client.sql, is("select ?"));
    }

    /**
     * Verifies that typed null binding remains specific to Helidon's provider
     * and does not add a method to the statement contract.
     */
    @Test
    void typedNullDoesNotExpandTheAlternateStatementContract() {
        AlternateStatement statement = new AlternateStatement();

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                                                              () -> GeneratedJdbcData.bindNull(statement,
                                                                                        1,
                                                                                        JDBCType.VARCHAR));

        assertThat(failure.getMessage(),
                   is("Typed SQL null binding requires a statement created by Helidon's JDBC provider."));
    }

    @Test
    void namedDefaultsPreserveAlternateImplementationsAndNullPrecedence() {
        AlternateClient client = new AlternateClient();
        AlternateStatement statement = client.statement;
        assertThrows(NullPointerException.class, () -> client.prepare(" ", null));
        assertThrows(NullPointerException.class, () -> client.prepare(" ", Arrays.asList("", null)));
        assertThrows(UnsupportedOperationException.class, () -> client.prepare("select ?", List.of("name")));
        assertThrows(NullPointerException.class, () -> client.prepare(null));
        assertThrows(UnsupportedOperationException.class, () -> client.prepare("select :name"));
        assertThrows(NullPointerException.class, () -> statement.bind(null, "value"));
        assertThrows(NullPointerException.class, () -> statement.bind("name", null));
        assertThrows(UnsupportedOperationException.class, () -> statement.bind("name", "value"));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.bindNull(null, "name", JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.bindNull(statement, (String) null, JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.bindNull(statement, "name", null));
        assertThrows(UnsupportedOperationException.class, () -> GeneratedJdbcData.bindNull(statement, "name", JDBCType.INTEGER));
    }

    /**
     * Reference validation precedes unsupported providers and invalid content.
     */
    @Test
    void namedPreparationValidatesEveryReferenceBeforeProviderDispatch() {
        JdbcClient alternate = mock(JdbcClient.class);
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.prepareNamed(null, "select :name", List.of("name")));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.prepareNamed(alternate, null, List.of("name")));
        assertThrows(NullPointerException.class, () -> GeneratedJdbcData.prepareNamed(alternate, " ", null));
        assertThrows(NullPointerException.class,
                () -> GeneratedJdbcData.prepareNamed(alternate, " ", Arrays.asList("", null)));
        assertThrows(UnsupportedOperationException.class,
                () -> GeneratedJdbcData.prepareNamed(alternate, " ", List.of("")));
        assertThrows(UnsupportedOperationException.class,
                () -> GeneratedJdbcData.prepareNamed(alternate, "select :name", List.of("name")));
        verifyZeroInteractions(alternate);
    }

    /**
     * The alias bridge restricts only its own definitions and performs no JDBC work.
     */
    @Test
    void namedPreparationRejectsUnsupportedStylesAndInputContracts() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        assertThrows(NullPointerException.class,
                () -> GeneratedJdbcData.prepareNamed(client, " ", Arrays.asList("", null)));
        for (String sql : List.of(" ", "select ?", "select 1", "select ':name'", "select :name, ?",
                "select :absent", "select :name, :additional", "select :name /* SQL_CANARY")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> GeneratedJdbcData.prepareNamed(client, sql, List.of("name")));
            assertThat(failure.toString(), not(containsString("SQL_CANARY")));
            assertThat(failure.getSuppressed().length, is(0));
        }
        for (List<String> names : List.of(List.<String>of(), List.of("name", "name"), List.of(":name"), List.of("name "))) {
            assertThrows(IllegalArgumentException.class, () -> GeneratedJdbcData.prepareNamed(client, "select :name", names));
        }
        assertThrows(IllegalArgumentException.class, () -> GeneratedJdbcData.prepareNamed(client, "select 1", List.of()));
        client.prepare("select ?", List.of("name")).get().bind(1, "value");
        client.create("select ?").bind(1, "value");
        verifyZeroInteractions(source);
    }

    /**
     * Prepared alias stages retain defensive input copies and independent assignments.
     */
    @Test
    void namedPreparationRetainsPrivateDefinitionsAndFreshStages() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        List<String> names = new ArrayList<>(List.of("𐐀name", "email"));
        var factory = GeneratedJdbcData.prepareNamed(client,
                "select :𐐀name, :email, :𐐀name, nq'[? :ignored]' /* SQL_CANARY */", names);
        names.clear();
        names.add("other");
        JdbcClient.Statement first = factory.get();
        JdbcClient.Statement second = factory.get();
        assertThat(first, not(sameInstance(second)));
        first.bind("𐐀name", "VALUE_CANARY");
        GeneratedJdbcData.bindNull(first, "email", JDBCType.VARCHAR);
        GeneratedJdbcData.bindNull(second, "𐐀name", JDBCType.VARCHAR);
        second.bind("email", "email");
        assertThrows(IllegalArgumentException.class, () -> first.bind("𐐀name", "duplicate"));
        assertThrows(IllegalArgumentException.class, () -> second.bind("other", "value"));
        assertThat(factory.toString(), not(containsString("CANARY")));
        assertThat(first.toString(), not(containsString("CANARY")));
        verifyZeroInteractions(source);
    }

    @Test
    void alternateProviderRetainsDefaultBatchBoundaryWithoutTraversal() {
        AlternateStatement statement = new AlternateStatement();
        Iterable<Integer> source = () -> { throw new AssertionError("The source must not be traversed."); };
        assertThrows(NullPointerException.class, () -> statement.executeBatch(null, null));
        assertThrows(NullPointerException.class, () -> statement.executeBatch(source, null));
        assertThrows(UnsupportedOperationException.class, () -> statement.executeBatch(source, (_, _) -> { }));
    }

    private static final class AlternateClient implements JdbcClient {
        private static final JdbcClientConfig CONFIG = JdbcClientConfig.builder()
                .dataSourceName("test-data-source")
                .buildPrototype();

        private final AlternateStatement statement = new AlternateStatement();

        private String sql;

        @Override
        public JdbcClientConfig prototype() {
            return CONFIG;
        }

        @Override
        public Statement create(String sql) {
            this.sql = sql;
            return statement;
        }
    }

    private static final class AlternateStatement implements JdbcClient.Statement {

        @Override
        public JdbcClient.Statement bind(int index, Object value) {
            return this;
        }

        @Override
        public long execute() {
            return 0;
        }

        @Override
        public <T> JdbcClient.Rows<T> map(JdbcClient.RowMapper<T> mapper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> JdbcClient.Rows<T> map(Class<T> scalarType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JdbcClient.GeneratedKeys generatedKeys() {
            throw new UnsupportedOperationException();
        }
    }
}
