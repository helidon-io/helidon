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

            assertThat(failure.getMessage(), containsString("a syntax error or access-rule violation"));
            assertThat(failure.getMessage(), containsString("SQLSTATE '42000'"));
            assertThat(failure.getMessage(), not(containsString("SQLSTATE class")));
            assertThat(failure.getMessage(), containsString("vendor code 91"));
            assertThat(failure.getMessage(),
                       containsString("SQL fingerprint is '" + JdbcExceptionTranslator.fingerprint(sql) + "'"));
            assertThat(failure.getMessage(),
                       containsString("Consult the JDBC driver documentation for details about this SQLSTATE "
                                              + "and vendor code."));
            assertThat(messages, not(containsString(sql)));
            assertThat(messages, not(containsString("private-token")));
            assertThat(messages, not(containsString("user:password")));
            assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
            SQLException safeCause = (SQLException) failure.getCause();
            assertThat(safeCause.getSQLState(), is("42000"));
            assertThat(safeCause.getErrorCode(), is(91));
            assertThat(safeCause.getSuppressed().length, is(0));
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
    void describesEveryNonRdaSqlStateFromThePortableCatalog() {
        Map<String, List<String>> statesByDescription = Map.ofEntries(
                Map.entry("successful completion", List.of("00000")),
                Map.entry("a warning", List.of("01000", "01002", "01003", "01004", "01005", "01006", "01007",
                                                    "0100B")),
                Map.entry("no data", List.of("02000")),
                Map.entry("a dynamic SQL error", List.of("07000", "07001", "07002", "07003", "07004", "07005",
                                                              "07006", "07008", "07009")),
                Map.entry("a connection exception", List.of("08000", "08001", "08002", "08003", "08004", "08006",
                                                                  "08007")),
                Map.entry("a feature-not-supported condition", List.of("0A000", "0A001")),
                Map.entry("a cardinality violation", List.of("21000", "21S01", "21S02")),
                Map.entry("a data exception", List.of("22000", "22001", "22002", "22003", "22005", "22006", "22007",
                                                          "22008", "22011", "22012", "22015", "22018", "22019", "22021",
                                                          "22024", "22025", "22027")),
                Map.entry("an integrity-constraint violation", List.of("23000")),
                Map.entry("an invalid cursor state", List.of("24000")),
                Map.entry("an invalid transaction state", List.of("25000")),
                Map.entry("an invalid SQL statement identifier", List.of("26000")),
                Map.entry("an invalid authorization specification", List.of("28000")),
                Map.entry("an invalid character-set name", List.of("2C000")),
                Map.entry("an invalid transaction termination", List.of("2D000")),
                Map.entry("an invalid connection name", List.of("2E000")),
                Map.entry("an invalid SQL descriptor name", List.of("33000")),
                Map.entry("an invalid exception number", List.of("35000")),
                Map.entry("an invalid catalog name", List.of("3D000")),
                Map.entry("an invalid schema name", List.of("3F000")),
                Map.entry("a transaction rollback", List.of("40000", "40003")),
                Map.entry("a syntax error or access-rule violation",
                          List.of("42000", "42S01", "42S02", "42S11", "42S12", "42S21", "42S22", "42S31", "42S32",
                                  "42S42", "42S51", "42S52", "42S61", "42S62", "42S72", "42S81", "42S82")),
                Map.entry("a WITH CHECK OPTION violation", List.of("44000")));

        for (Map.Entry<String, List<String>> entry : statesByDescription.entrySet()) {
            for (String sqlState : entry.getValue()) {
                String diagnostic = JdbcExceptionTranslator.sqlDiagnostic(new SQLException("private", sqlState, 91));

                assertThat(sqlState,
                           diagnostic,
                           containsString("The database reported " + entry.getKey()));
                assertThat(sqlState, diagnostic, containsString("SQLSTATE '" + sqlState + "'"));
                assertThat(sqlState, diagnostic, containsString("vendor code 91"));
                assertThat(sqlState, diagnostic, not(containsString("SQLSTATE class")));
            }
        }
    }

    @Test
    void describesEverySpecificNonRdaConditionFromThePortableCatalog() {
        Map<String, String> descriptionsByState = Map.ofEntries(
                Map.entry("01002", "Disconnect error"),
                Map.entry("01003", "Null value eliminated in set function"),
                Map.entry("01004", "String data, right truncation"),
                Map.entry("01005", "Insufficient item descriptor areas"),
                Map.entry("01006", "Privilege not revoked"),
                Map.entry("01007", "Privilege not granted"),
                Map.entry("0100B", "Default value too long for system view"),
                Map.entry("07001", "Using clause does not match dynamic parameters"),
                Map.entry("07002", "Using clause does not match target specifications"),
                Map.entry("07003", "Cursor specification cannot be executed"),
                Map.entry("07004", "Using clause is required for dynamic parameters"),
                Map.entry("07005", "Prepared statement is not a cursor specification"),
                Map.entry("07006", "Restricted data type attribute violation"),
                Map.entry("07008", "Invalid descriptor count"),
                Map.entry("07009", "Invalid descriptor index"),
                Map.entry("08001", "Client unable to establish connection"),
                Map.entry("08002", "Connection name in use"),
                Map.entry("08003", "Connection does not exist"),
                Map.entry("08004", "Server rejected the connection"),
                Map.entry("08006", "Connection failure"),
                Map.entry("08007", "Transaction resolution unknown"),
                Map.entry("0A001", "Multiple-server transaction"),
                Map.entry("21S01", "Insert value list does not match column list"),
                Map.entry("21S02", "Degree of derived table does not match column list"),
                Map.entry("22001", "String data, right truncation"),
                Map.entry("22002", "Null value, no indicator parameter"),
                Map.entry("22003", "Numeric value out of range"),
                Map.entry("22005", "Error in assignment"),
                Map.entry("22006", "Invalid interval format"),
                Map.entry("22007", "Invalid date/time format"),
                Map.entry("22008", "Date/time field overflow"),
                Map.entry("22011", "Substring error"),
                Map.entry("22012", "Division by zero"),
                Map.entry("22015", "Interval field overflow"),
                Map.entry("22018", "Invalid character value for CAST"),
                Map.entry("22019", "Invalid escape character"),
                Map.entry("22021", "Translation result not in target repertoire"),
                Map.entry("22024", "Unterminated string"),
                Map.entry("22025", "Invalid escape sequence"),
                Map.entry("22027", "Trim error"),
                Map.entry("40003", "Statement completion unknown"),
                Map.entry("42S01", "Base table or viewed table already exists"),
                Map.entry("42S02", "Base table not found"),
                Map.entry("42S11", "Index already exists"),
                Map.entry("42S12", "Index not found"),
                Map.entry("42S21", "Column already exists"),
                Map.entry("42S22", "Column not found"),
                Map.entry("42S31", "Schema already exists"),
                Map.entry("42S32", "Schema not found"),
                Map.entry("42S42", "Catalog not found"),
                Map.entry("42S51", "Character set already exists"),
                Map.entry("42S52", "Character set not found"),
                Map.entry("42S61", "Collation already exists"),
                Map.entry("42S62", "Collation not found"),
                Map.entry("42S72", "Conversion not found"),
                Map.entry("42S81", "Translation already exists"),
                Map.entry("42S82", "Translation not found"));

        for (Map.Entry<String, String> entry : descriptionsByState.entrySet()) {
            String diagnostic = JdbcExceptionTranslator.sqlDiagnostic(new SQLException("private", entry.getKey(), 91));

            assertThat(entry.getKey(),
                       diagnostic,
                       containsString("The SQLSTATE condition is '" + entry.getValue() + "'"));
        }
    }

    @Test
    void combinesThePortableClassAndAsteriskedConditionFor42s01() {
        String diagnostic = JdbcExceptionTranslator.sqlDiagnostic(new SQLException("private", "42S01", 42101));

        assertThat(diagnostic,
                   is(" The database reported a syntax error or access-rule violation. "
                              + "The SQLSTATE condition is 'Base table or viewed table already exists' "
                              + "(SQLSTATE '42S01' and vendor code 42101). "
                              + "Consult the JDBC driver documentation for details about this SQLSTATE and vendor code."));
    }

    @Test
    void leavesRdaAndOtherUnknownSqlStateClassesUnclassified() {
        for (String sqlState : List.of("HZ000", "HZ4A0", "HY000")) {
            String diagnostic = JdbcExceptionTranslator.sqlDiagnostic(new SQLException("private", sqlState, 91));

            assertThat(diagnostic, containsString("an error outside the recognized portable SQLSTATE catalog"));
            assertThat(diagnostic, containsString("SQLSTATE '" + sqlState + "'"));
            assertThat(diagnostic, containsString("vendor code 91"));
            assertThat(diagnostic, not(containsString("SQLSTATE class")));
        }
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
        assertThat(truncationMarkers(graph), is(0L));
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
        assertThat(truncationMarkers(graph), is(0L));
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
        assertThat(graph.size(), is(2));
        assertThat(truncationMarkers(graph), is(0L));
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

        assertThat(graph.size(), is(1));
        assertThat(truncationMarkers(graph), is(0L));
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
        assertThat(graph.size(), is(2));
        assertThat(truncationMarkers(graph), is(0L));
        assertThat(graphMessages(graph), not(containsString("secret")));
    }

    @Test
    void rejectsMalformedSqlStateContent() {
        String secretState = "jdbc:test://user:password@host/db?token=private-token";
        SQLException cause = new SQLException("private driver message", secretState, 91);

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", cause);
        SQLException sanitized = (SQLException) failure.getCause();

        assertThat(failure.getMessage(), containsString("an error without a valid SQLSTATE"));
        assertThat(failure.getMessage(), not(containsString(secretState)));
        assertThat(sanitized.getSQLState(), nullValue());
        assertThat(sanitized.getErrorCode(), is(91));
        assertThat(graphMessages(exceptionGraph(sanitized)), not(containsString("private")));
    }

    @Test
    void containsBrokenSqlExceptionAccessorsInsideTheSanitizationBoundary() {
        SQLException hostile = new SQLException("private root message") {
            @Override
            public String getSQLState() {
                throw new IllegalStateException("private SQL state accessor");
            }

            @Override
            public int getErrorCode() {
                throw new IllegalStateException("private vendor code accessor");
            }

            @Override
            public synchronized Throwable getCause() {
                throw new IllegalStateException("private cause accessor");
            }

            @Override
            public SQLException getNextException() {
                throw new IllegalStateException("private next-exception accessor");
            }
        };

        DataException failure = JdbcExceptionTranslator.translate("query", "SELECT 1", hostile);
        SQLException sanitized = (SQLException) failure.getCause();
        List<Throwable> graph = exceptionGraph(sanitized);

        assertThat(failure.getMessage(), containsString("an error without a valid SQLSTATE"));
        assertThat(failure.getMessage(), containsString("vendor code not provided"));
        assertThat(sanitized.getSQLState(), nullValue());
        assertThat(sanitized.getErrorCode(), is(0));
        assertThat(truncationMarkers(graph), is(0L));
        assertThat(graphMessages(graph), not(containsString("private")));
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
