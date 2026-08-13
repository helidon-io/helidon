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

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcExceptionTranslatorTest {

    @Test
    void exposesOnlySafeSqlIdentityAndDriverMetadata() {
        List<String> statements = List.of("SELECT 'private-name'",
                                          "SELECT 987654321",
                                          "SELECT X'DEADBEEF'",
                                          "SELECT 'private''quote'",
                                          "SELECT 1 /* private-comment */",
                                          "SELECT '" + "s".repeat(1_024) + "'");

        for (String sql : statements) {
            SQLException cause = new SQLException("driver echoed " + sql
                                                           + " from jdbc:test://user:password@host/db?token=private-token",
                                                   "42000",
                                                   91);
            cause.addSuppressed(new SQLException("suppressed private-token", "08003", 92));
            cause.setNextException(new SQLException("next private-token", "08004", 93));

            DataException failure = JdbcExceptionTranslator.translate("QUERY", sql, cause);
            String messages = messages(failure);

            assertThat(failure.getMessage(), containsString("SQL state is '42000'"));
            assertThat(failure.getMessage(), containsString("vendor code is 91"));
            assertThat(failure.getMessage(),
                       containsString("SQL fingerprint is '" + JdbcExceptionTranslator.fingerprint(sql) + "'"));
            assertThat(messages, not(containsString(sql)));
            assertThat(messages, not(containsString("private-token")));
            assertThat(messages, not(containsString("user:password")));
            assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
            SQLException safeCause = (SQLException) failure.getCause();
            assertThat(safeCause.getSQLState(), is("42000"));
            assertThat(safeCause.getErrorCode(), is(91));
            assertThat(safeCause.getSuppressed().length, is(1));
            assertThat(safeCause.getNextException().getSQLState(), is("08004"));
        }
    }

    @Test
    void createsStableBoundedFingerprints() {
        String first = JdbcExceptionTranslator.fingerprint("SELECT 1");
        String repeated = JdbcExceptionTranslator.fingerprint("SELECT 1");
        String second = JdbcExceptionTranslator.fingerprint("SELECT 2");

        assertThat(first, is(repeated));
        assertThat(first.length(), is(24));
        assertThat(first, not(is(second)));
    }

    @Test
    void copiesWarningMetadataWithoutRetainingDriverOwnedContent() {
        SQLWarning first = new SQLWarning("secret server and SQL detail", "01001", 11);
        SQLWarning second = new SQLWarning("secret database name", "01002", 12);
        first.addSuppressed(new IllegalStateException("secret suppressed detail"));
        second.initCause(new IllegalArgumentException("secret nested detail"));
        first.setNextWarning(second);

        List<Throwable> warnings = JdbcExceptionTranslator.sanitizeWarnings("statement warning", first);

        assertThat(warnings.size(), is(2));
        assertSafeWarning(warnings.get(0), "01001", 11);
        assertSafeWarning(warnings.get(1), "01002", 12);
        assertThat(((SQLException) warnings.get(0)).getNextException(), nullValue());
        assertThat(messages(warnings.get(0)), not(containsString("secret")));
        assertThat(messages(warnings.get(1)), not(containsString("secret")));
    }

    @Test
    void boundsAndCycleProtectsWarningChains() {
        SQLWarning cyclic = new SQLWarning("secret cycle", "01000", 1) {
            @Override
            public SQLWarning getNextWarning() {
                return this;
            }
        };

        List<Throwable> cycle = JdbcExceptionTranslator.sanitizeWarnings("result set warning", cyclic);

        assertThat(cycle.size(), is(2));
        assertSafeWarning(cycle.get(0), "01000", 1);
        assertThat(cycle.get(1).getMessage(),
                   is("The JDBC provider could not process result set warnings."));

        SQLWarning first = new SQLWarning("secret warning 0", "01000", 0);
        SQLWarning current = first;
        for (int index = 1; index <= 64; index++) {
            SQLWarning next = new SQLWarning("secret warning " + index, "01000", index);
            current.setNextWarning(next);
            current = next;
        }

        List<Throwable> bounded = JdbcExceptionTranslator.sanitizeWarnings("connection warning", first);

        assertThat(bounded.size(), is(65));
        assertThat(bounded.get(64).getMessage(),
                   is("The JDBC provider could not process connection warnings."));
    }

    @Test
    void replacesHostileWarningTraversalAndNonSqlFailures() {
        SQLWarning broken = new SQLWarning("secret warning", "01003", 13) {
            @Override
            public SQLWarning getNextWarning() {
                throw new IllegalArgumentException("secret traversal detail");
            }
        };

        List<Throwable> warnings = JdbcExceptionTranslator.sanitizeWarnings("statement warning", broken);
        Throwable traversal = warnings.get(1);

        assertThat(warnings.size(), is(2));
        assertThat(traversal.getMessage(),
                   is("The JDBC provider could not process statement warnings."));
        assertThat(traversal.getCause(), nullValue());
        assertThat(traversal.getSuppressed().length, is(0));

        IllegalStateException driverFailure = new IllegalStateException("secret driver detail",
                                                                         new RuntimeException("secret cause"));
        driverFailure.addSuppressed(new RuntimeException("secret suppressed"));
        Throwable sanitized = JdbcExceptionTranslator.sanitize("closing a connection", driverFailure);

        assertThat(sanitized.getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' "
                              + "while closing a connection."));
        assertThat(sanitized.getCause(), nullValue());
        assertThat(sanitized.getSuppressed().length, is(0));
        assertThat(messages(sanitized), not(containsString("secret")));
    }

    @Test
    void boundsSqlExceptionCauseChains() {
        SQLException first = new SQLException("secret cause 0", "42000", 0);
        SQLException current = first;
        for (int index = 1; index < 17; index++) {
            SQLException next = new SQLException("secret cause " + index, "42000", index);
            current.initCause(next);
            current = next;
        }

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", first);
        List<Throwable> graph = exceptionGraph(failure.getCause());

        assertThat(graph.size(), is(16));
        assertThat(truncationMarkers(graph), is(1L));
        assertThat(graphMessages(graph), not(containsString("secret")));
    }

    @Test
    void boundsNextExceptionChains() {
        SQLException first = new SQLException("secret next 0", "42000", 0);
        SQLException current = first;
        for (int index = 1; index < 17; index++) {
            SQLException next = new SQLException("secret next " + index, "42000", index);
            current.setNextException(next);
            current = next;
        }

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", first);
        List<Throwable> graph = exceptionGraph(failure.getCause());

        assertThat(graph.size(), is(16));
        assertThat(truncationMarkers(graph), is(1L));
        assertThat(graphMessages(graph), not(containsString("secret")));
    }

    @Test
    void preservesSharedCauseAndNextExceptionRelationships() {
        SQLException first = new SQLException("secret root", "42000", 1);
        SQLException shared = new SQLException("secret shared", "42001", 2);
        first.initCause(shared);
        first.setNextException(shared);

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", first);
        SQLException sanitized = (SQLException) failure.getCause();
        SQLException sanitizedShared = (SQLException) sanitized.getCause();
        List<Throwable> graph = exceptionGraph(sanitized);

        assertThat(sanitized.getNextException(), sameInstance(sanitizedShared));
        assertThat(sanitizedShared.getSQLState(), is("42001"));
        assertThat(sanitizedShared.getErrorCode(), is(2));
        assertThat(graph.size(), is(3));
        assertThat(truncationMarkers(graph), is(1L));
        assertThat(graphMessages(graph), not(containsString("secret")));
    }

    @Test
    void boundsWideSuppressedExceptionGraphs() {
        SQLException first = new SQLException("secret root", "42000", 1);
        for (int index = 0; index < 16; index++) {
            first.addSuppressed(new SQLException("secret suppressed " + index, "42001", index));
        }

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", first);
        List<Throwable> graph = exceptionGraph(failure.getCause());

        assertThat(graph.size(), is(2));
        assertThat(truncationMarkers(graph), is(1L));
        assertThat(graphMessages(graph), not(containsString("secret")));
    }

    @Test
    void removesCyclesFromSanitizedExceptionGraphs() {
        SQLException first = new SQLException("secret first", "42000", 1);
        SQLException second = new SQLException("secret second", "42001", 2);
        first.initCause(second);
        second.setNextException(first);

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", first);
        SQLException sanitized = (SQLException) failure.getCause();
        SQLException sanitizedSecond = (SQLException) sanitized.getCause();
        List<Throwable> graph = exceptionGraph(sanitized);

        assertThat(sanitizedSecond.getNextException(), nullValue());
        assertThat(graph.size(), is(3));
        assertThat(truncationMarkers(graph), is(1L));
        assertThat(graphMessages(graph), not(containsString("secret")));
    }

    private static void assertSafeWarning(Throwable actual, String sqlState, int vendorCode) {
        assertThat(actual, instanceOf(SQLWarning.class));
        SQLWarning warning = (SQLWarning) actual;
        assertThat(warning.getMessage(), is("The JDBC driver reported a warning."));
        assertThat(warning.getSQLState(), is(sqlState));
        assertThat(warning.getErrorCode(), is(vendorCode));
        assertThat(warning.getCause(), nullValue());
        assertThat(warning.getSuppressed().length, is(0));
    }

    private static String messages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            result.append(current.getMessage()).append('\n');
            for (Throwable suppressed : current.getSuppressed()) {
                result.append(messages(suppressed));
            }
            if (current instanceof SQLException sqlException) {
                SQLException next = sqlException.getNextException();
                if (next != current.getCause()) {
                    result.append(messages(next));
                }
            }
            current = current.getCause();
        }
        return result.toString();
    }

    private static List<Throwable> exceptionGraph(Throwable root) {
        List<Throwable> result = new ArrayList<>();
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.addLast(root);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (visited.put(current, Boolean.TRUE) != null) {
                continue;
            }
            result.add(current);
            for (Throwable suppressed : current.getSuppressed()) {
                pending.addLast(suppressed);
            }
            Throwable cause = current.getCause();
            if (cause != null) {
                pending.addLast(cause);
            }
            if (current instanceof SQLException sqlException) {
                SQLException next = sqlException.getNextException();
                if (next != null) {
                    pending.addLast(next);
                }
            }
        }
        return List.copyOf(result);
    }

    private static long truncationMarkers(List<Throwable> graph) {
        return graph.stream()
                .filter(node -> ("Some JDBC failure relationships were not inspected or were omitted "
                        + "to keep diagnostics bounded.").equals(node.getMessage()))
                .count();
    }

    private static String graphMessages(List<Throwable> graph) {
        StringBuilder result = new StringBuilder();
        for (Throwable node : graph) {
            result.append(node.getMessage()).append('\n');
        }
        return result.toString();
    }
}
