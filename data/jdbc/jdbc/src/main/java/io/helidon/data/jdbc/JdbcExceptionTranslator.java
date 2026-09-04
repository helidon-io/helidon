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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.data.DataException;

/**
 * Translates failures raised by JDBC operations into safe application diagnostics.
 * <p>
 * JDBC driver exceptions can include SQL text, bind values, connection URLs,
 * credentials, and server details in their messages or related exceptions.
 * Retaining an original driver exception would allow those details to bypass
 * message sanitization. This translator therefore creates provider exceptions
 * from approved SQL state and vendor code metadata without retaining the
 * original driver exception. Copied exception relationships have cycle and
 * size limits so malformed driver exception graphs cannot create unbounded
 * provider state.
 * <p>
 * Runtime exceptions are sanitized only when the caller has established that
 * they came directly from a JDBC invocation. Application and row mapper
 * failures retain their original type and identity. Fatal {@link Error}
 * instances also propagate unchanged because translating them could conceal a
 * JVM failure or require allocation while the JVM is reporting resource
 * exhaustion.
 */
final class JdbcExceptionTranslator {

    // Retain at most 16 nodes in a sanitized graph, including its root.
    private static final int MAX_EXCEPTION_GRAPH_NODES = 16;

    private static final String DRIVER_FAILURE = "The JDBC driver reported a failure.";
    private static final String RESULT_VALUE_FAILURE = "The JDBC provider could not read a result value.";

    private JdbcExceptionTranslator() {
    }

    /**
     * Translates a driver failure using the operation's safe metadata.
     *
     * @param operation failed operation
     * @param cause driver failure
     * @return data-layer failure
     */
    static DataException translate(JdbcOperation operation, SQLException cause) {
        return translate(operationName(operation), cause);
    }

    /**
     * Describes an operation for use in application-visible diagnostics.
     *
     * @param operation JDBC operation
     * @return safe operation description
     */
    static String operationDescription(JdbcOperation operation) {
        return "JDBC " + operationName(operation);
    }

    /**
     * Converts a JDBC boundary failure to an unchecked failure. SQL exceptions
     * become sanitized data exceptions, existing runtime exceptions retain
     * their type, and fatal errors propagate unchanged.
     *
     * @param operation stable JDBC operation label
     * @param failure JDBC boundary failure
     * @return sanitized data exception to throw
     */
    static DataException translateFailure(String operation, Throwable failure) {
        if (failure instanceof SQLException sqlException) {
            return translate(operation, sqlException);
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }

    /**
     * Invokes one JDBC-owned value operation while preserving checked SQL
     * failures for normal translation and sanitizing unexpected runtime
     * failures at the point where their JDBC origin is known.
     *
     * @param operation stable JDBC operation label
     * @param invocation JDBC invocation
     * @param <T> returned value type
     * @return invocation result
     * @throws SQLException when JDBC reports a checked failure
     */
    static <T> T invoke(String operation, JdbcSupplier<T> invocation) throws SQLException {
        try {
            return invocation.get();
        } catch (SQLException sqlException) {
            throw sqlException;
        } catch (RuntimeException runtimeException) {
            throw (RuntimeException) sanitize(operation, runtimeException);
        }
    }

    /**
     * Invokes one JDBC-owned void operation with the same failure policy as
     * {@link #invoke(String, JdbcSupplier)}.
     *
     * @param operation stable JDBC operation label
     * @param invocation JDBC invocation
     * @throws SQLException when JDBC reports a checked failure
     */
    static void invokeVoid(String operation, JdbcRunnable invocation) throws SQLException {
        try {
            invocation.run();
        } catch (SQLException sqlException) {
            throw sqlException;
        } catch (RuntimeException runtimeException) {
            throw (RuntimeException) sanitize(operation, runtimeException);
        }
    }

    /**
     * Sanitizes a first provider-owned failure while leaving a SQL diagnostic
     * open for later bounded cleanup attachments.
     *
     * @param operation stable provider operation label
     * @param failure JDBC-owned failure
     * @return provider-owned primary failure
     */
    static Throwable prepare(String operation, Throwable failure) {
        if (safeDataException(failure)) {
            // Restore the safe SQL cause so the runner can apply the terminal operation context.
            return failure.getCause();
        }
        if (failure instanceof SafeSQLException
                || failure instanceof SafeDiagnosticException) {
            return failure;
        }
        if (failure instanceof SQLException sqlException) {
            return new ExceptionGraphSanitizer(sqlException).sanitize();
        }
        return diagnostic(operation, failure);
    }

    /**
     * Rebuilds a JDBC-owned failure without retaining driver-provided text or
     * throwable references. Callers should use this method before a failure is
     * stored or attached to an application-visible failure tree.
     * Provider-owned sanitized failures are returned unchanged.
     *
     * @param operation stable provider operation label
     * @param failure JDBC-owned failure
     * @return sanitized provider-owned failure
     */
    static Throwable sanitize(String operation, Throwable failure) {
        if (failure instanceof SafeSqlDiagnostic || safeDataException(failure)) {
            return failure;
        }
        if (failure instanceof SafeDiagnosticException) {
            return failure;
        }
        if (failure instanceof SQLException sqlException) {
            return safeCause(sqlException);
        }
        return diagnostic(operation, failure);
    }

    /**
     * Creates a stable diagnostic for a runtime failure from a result value accessor.
     * The driver-owned failure is deliberately not retained.
     *
     * @return sanitized provider-owned failure
     */
    static DataException resultValueFailure() {
        return new DataException(RESULT_VALUE_FAILURE);
    }

    /**
     * Attaches a sanitized JDBC-owned failure without risking self-suppression.
     * An SQL primary is rebuilt before attachment so provider-observed cleanup
     * never has to be recovered from a driver-owned suppressed list. A non-SQL
     * primary is left unchanged because it may be an application or mapper
     * failure whose identity and type are contractual.
     *
     * @param primary receiving failure
     * @param operation stable provider operation label
     * @param failure JDBC-owned failure to attach
     * @return receiving failure, which may be a sanitized replacement for an SQL primary
     */
    static Throwable suppress(Throwable primary, String operation, Throwable failure) {
        if (primary == failure) {
            return primary;
        }
        if (primary instanceof SQLException sqlPrimary) {
            SQLException sanitizedPrimary = sqlPrimary instanceof SafeSqlDiagnostic
                    ? sqlPrimary
                    : new ExceptionGraphSanitizer(sqlPrimary).sanitize();
            SafeSqlDiagnostic safePrimary = (SafeSqlDiagnostic) sanitizedPrimary;
            Throwable sanitized = relatedDiagnostic(operation, failure);
            DiagnosticBudget diagnosticBudget = safePrimary.diagnosticBudget();
            diagnosticBudget.attach(sanitizedPrimary, sanitized);
            return sanitizedPrimary;
        }
        Throwable sanitized = sanitize(operation, failure);
        if (primary != sanitized) {
            primary.addSuppressed(sanitized);
        }
        return primary;
    }

    /**
     * Creates a provider-owned SQLException whose message contains no SQL or
     * connection configuration and may therefore remain visible.
     *
     * @param message safe provider diagnostic
     * @return JDBC failure carrying trusted text
     */
    static SQLException safeException(String message) {
        return new SafeSQLException(message);
    }

    /**
     * Translates a driver failure without deriving an identity from its SQL.
     *
     * @param operation operation name
     * @param cause driver failure
     * @return data-layer failure
     */
    private static DataException translate(String operation, SQLException cause) {
        SqlMetadata metadata = sqlMetadata(cause);
        // A stable unkeyed SQL digest would let diagnostic readers identify predictable statements by comparison.
        String message = "The JDBC " + operation + " failed." + databaseDiagnostic(metadata)
                + driverDocumentationGuidance(metadata);
        return new DataException(message, safeCause(cause, metadata));
    }

    /**
     * Copies safe JDBC metadata without retaining driver-provided messages,
     * SQL text, URLs, arbitrary cause text, or driver-owned throwable
     * references. Identity tracking preserves shared relationships while
     * rejecting only edges that would create cycles. An iterative traversal
     * and a total node limit bound stack use and provider-created diagnostic
     * state.
     *
     * @param cause driver failure
     * @return sanitized failure tree
     */
    private static SQLException safeCause(SQLException cause) {
        return safeCause(cause, sqlMetadata(cause));
    }

    private static SQLException safeCause(SQLException cause, SqlMetadata metadata) {
        if (cause instanceof SafeSqlDiagnostic) {
            return cause;
        }
        return new ExceptionGraphSanitizer(cause, metadata).sanitize();
    }

    /**
     * Creates one safe leaf for a provider-observed related failure. Its source
     * relationships are deliberately not traversed and are omitted silently.
     *
     * @param operation stable provider operation label
     * @param failure related JDBC-owned failure
     * @return provider-owned leaf diagnostic
     */
    private static Throwable relatedDiagnostic(String operation, Throwable failure) {
        if (safeDataException(failure)) {
            return relatedDiagnostic(operation, failure.getCause());
        }
        if (failure instanceof SQLException sqlException) {
            String message = failure instanceof SafeSQLException ? failure.getMessage() : DRIVER_FAILURE;
            SqlMetadata metadata = sqlMetadata(sqlException);
            return new SafeSQLException(message, metadata.sqlState(), metadata.vendorCode());
        }
        if (failure instanceof SafeDiagnosticException safe) {
            return new SafeDiagnosticException(safe.getMessage());
        }
        return diagnostic(operation, failure);
    }

    /**
     * Returns the operation name without exposing SQL or bound values.
     *
     * @param operation JDBC operation
     * @return safe operation name
     */
    private static String operationName(JdbcOperation operation) {
        return switch (operation.preparationPlan().resultKind()) {
        case QUERY -> "query";
        case UPDATE -> "update";
        case GENERATED_KEYS -> "generated keys operation";
        };
    }

    /**
     * Determines whether a data exception contains only provider-owned SQL
     * diagnostics and can safely cross another sanitization boundary.
     *
     * @param failure candidate failure
     * @return whether the failure is already sanitized
     */
    private static boolean safeDataException(Throwable failure) {
        return failure instanceof DataException dataException
                && dataException.getCause() instanceof SafeSqlDiagnostic;
    }

    /**
     * Creates a provider-owned representation of a non-SQL JDBC failure.
     * Only the stable provider label and throwable class name are retained.
     *
     * @param operation stable provider operation label
     * @param failure JDBC-owned failure
     * @return safe diagnostic without a cause
     */
    private static SafeDiagnosticException diagnostic(String operation, Throwable failure) {
        return new SafeDiagnosticException("The JDBC provider encountered an exception of type '"
                                                   + failure.getClass().getName() + "' while " + operation + ".");
    }

    /**
     * Reads the two JDBC diagnostic values independently so a broken accessor
     * cannot prevent the other value from being retained or escape the
     * sanitization boundary. SQLState is retained only in its standard
     * five-character alphanumeric form.
     *
     * @param source driver-owned SQL failure
     * @return bounded safe metadata
     */
    private static SqlMetadata sqlMetadata(SQLException source) {
        String sqlState = null;
        try {
            String candidate = source.getSQLState();
            if (validSqlState(candidate)) {
                sqlState = candidate;
            }
        } catch (RuntimeException _) {
            // Broken driver accessors are treated as unavailable metadata.
        }

        int vendorCode = 0;
        boolean vendorCodeAvailable = false;
        try {
            vendorCode = source.getErrorCode();
            vendorCodeAvailable = true;
        } catch (RuntimeException _) {
            // Broken driver accessors are treated as unavailable metadata.
        }
        return new SqlMetadata(sqlState, vendorCode, vendorCodeAvailable);
    }

    private static boolean validSqlState(String sqlState) {
        if (sqlState == null || sqlState.length() != 5) {
            return false;
        }
        for (int index = 0; index < sqlState.length(); index++) {
            char character = sqlState.charAt(index);
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'A' && character <= 'Z')) {
                return false;
            }
        }
        return true;
    }

    private static String databaseDiagnostic(SqlMetadata metadata) {
        if (metadata.sqlState() == null) {
            return " The database reported an error without a valid SQLSTATE (vendor code "
                    + metadata.vendorCodeDescription() + ").";
        }

        String sqlState = metadata.sqlState();
        String sqlStateClass = sqlState.substring(0, 2);
        String classDescription = sqlStateClassDescription(sqlStateClass);
        String reportedCondition = classDescription.isEmpty()
                ? "an error outside the recognized portable SQLSTATE catalog"
                : classDescription;
        String conditionDescription = sqlStateConditionDescription(sqlState);
        String retainedMetadata = "(SQLSTATE '" + sqlState + "' and vendor code "
                + metadata.vendorCodeDescription() + ").";
        if (conditionDescription.isEmpty()) {
            return " The database reported " + reportedCondition + " " + retainedMetadata;
        }
        return " The database reported " + reportedCondition + ". The SQLSTATE condition is '"
                + conditionDescription + "' " + retainedMetadata;
    }

    private static String driverDocumentationGuidance(SqlMetadata metadata) {
        if (metadata.sqlState() != null && metadata.vendorCodeAvailable()) {
            return " Consult the JDBC driver documentation for details about this SQLSTATE and vendor code.";
        }
        if (metadata.sqlState() != null) {
            return " Consult the JDBC driver documentation for details about this SQLSTATE.";
        }
        if (metadata.vendorCodeAvailable()) {
            return " Consult the JDBC driver documentation for details about this vendor code.";
        }
        return " Consult the JDBC driver documentation for details about this database error.";
    }

    /**
     * Returns the portable description of an SQLSTATE class. SQLSTATE defines
     * the first two characters as the condition class, so this class-level
     * description provides a portable fallback when the final three characters
     * identify an unknown or vendor-specific subclass. Recognized
     * five-character conditions receive additional portable detail from
     * {@link #sqlStateConditionDescription(String)}.
     * <p>
     * The mappings cover all non-RDA classes in Appendix B, SQLSTATE Values, of
     * the X/Open
     * <a href="https://www.opengroup.org/onlinepubs/9695959099/toc.pdf">Data
     * Management: Structured Query Language (SQL), Version 2</a> specification.
     * The {@code 21} and {@code 42} mappings also provide the class-level
     * fallback for their asterisked diagnostic-area conditions. RDA
     * {@code HZ} conditions are excluded deliberately and, like other unknown
     * classes, return an empty string.
     *
     * @param sqlStateClass two-character SQLSTATE class
     * @return portable class description, or an empty string when the class is
     *         not recognized
     */
    private static String sqlStateClassDescription(String sqlStateClass) {
        return switch (sqlStateClass) {
        case "00" -> "successful completion";
        case "01" -> "a warning";
        case "02" -> "no data";
        case "07" -> "a dynamic SQL error";
        case "08" -> "a connection exception";
        case "0A" -> "a feature-not-supported condition";
        case "21" -> "a cardinality violation";
        case "22" -> "a data exception";
        case "23" -> "an integrity-constraint violation";
        case "24" -> "an invalid cursor state";
        case "25" -> "an invalid transaction state";
        case "26" -> "an invalid SQL statement identifier";
        case "28" -> "an invalid authorization specification";
        case "2C" -> "an invalid character-set name";
        case "2D" -> "an invalid transaction termination";
        case "2E" -> "an invalid connection name";
        case "33" -> "an invalid SQL descriptor name";
        case "35" -> "an invalid exception number";
        case "3D" -> "an invalid catalog name";
        case "3F" -> "an invalid schema name";
        case "40" -> "a transaction rollback";
        case "42" -> "a syntax error or access-rule violation";
        case "44" -> "a WITH CHECK OPTION violation";
        default -> "";
        };
    }

    /**
     * Returns a safe portable description for a recognized five-character
     * SQLSTATE condition. These provider-owned descriptions add standardized
     * detail without retaining the JDBC driver's potentially sensitive
     * message. An empty result deliberately falls back to the portable class
     * description and the exact state supplied for driver-documentation lookup.
     * <p>
     * The catalog contains every non-RDA condition in the X/Open SQLSTATE
     * table which is more specific than its class state. It explicitly
     * includes the asterisked diagnostic-area conditions because JDBC drivers
     * may return those values even though an implementation may return
     * {@code 42000} instead. RDA {@code HZ} conditions are excluded.
     *
     * @param sqlState valid five-character SQLSTATE
     * @return portable condition description, or an empty string when the
     *         state has no more-specific recognized description
     */
    private static String sqlStateConditionDescription(String sqlState) {
        return switch (sqlState) {
        case "01002" -> "Disconnect error";
        case "01003" -> "Null value eliminated in set function";
        case "01004" -> "String data, right truncation";
        case "01005" -> "Insufficient item descriptor areas";
        case "01006" -> "Privilege not revoked";
        case "01007" -> "Privilege not granted";
        case "0100B" -> "Default value too long for system view";
        case "07001" -> "Using clause does not match dynamic parameters";
        case "07002" -> "Using clause does not match target specifications";
        case "07003" -> "Cursor specification cannot be executed";
        case "07004" -> "Using clause is required for dynamic parameters";
        case "07005" -> "Prepared statement is not a cursor specification";
        case "07006" -> "Restricted data type attribute violation";
        case "07008" -> "Invalid descriptor count";
        case "07009" -> "Invalid descriptor index";
        case "08001" -> "Client unable to establish connection";
        case "08002" -> "Connection name in use";
        case "08003" -> "Connection does not exist";
        case "08004" -> "Server rejected the connection";
        case "08006" -> "Connection failure";
        case "08007" -> "Transaction resolution unknown";
        case "0A001" -> "Multiple-server transaction";
        case "21S01" -> "Insert value list does not match column list";
        case "21S02" -> "Degree of derived table does not match column list";
        case "22001" -> "String data, right truncation";
        case "22002" -> "Null value, no indicator parameter";
        case "22003" -> "Numeric value out of range";
        case "22005" -> "Error in assignment";
        case "22006" -> "Invalid interval format";
        case "22007" -> "Invalid date/time format";
        case "22008" -> "Date/time field overflow";
        case "22011" -> "Substring error";
        case "22012" -> "Division by zero";
        case "22015" -> "Interval field overflow";
        case "22018" -> "Invalid character value for CAST";
        case "22019" -> "Invalid escape character";
        case "22021" -> "Translation result not in target repertoire";
        case "22024" -> "Unterminated string";
        case "22025" -> "Invalid escape sequence";
        case "22027" -> "Trim error";
        case "40003" -> "Statement completion unknown";
        case "42S01" -> "Base table or viewed table already exists";
        case "42S02" -> "Base table not found";
        case "42S11" -> "Index already exists";
        case "42S12" -> "Index not found";
        case "42S21" -> "Column already exists";
        case "42S22" -> "Column not found";
        case "42S31" -> "Schema already exists";
        case "42S32" -> "Schema not found";
        case "42S42" -> "Catalog not found";
        case "42S51" -> "Character set already exists";
        case "42S52" -> "Character set not found";
        case "42S61" -> "Collation already exists";
        case "42S62" -> "Collation not found";
        case "42S72" -> "Conversion not found";
        case "42S81" -> "Translation already exists";
        case "42S82" -> "Translation not found";
        default -> "";
        };
    }

    @FunctionalInterface
    interface JdbcSupplier<T> {
        T get() throws SQLException;
    }

    @FunctionalInterface
    interface JdbcRunnable {
        void run() throws SQLException;
    }

    /**
     * Iteratively rebuilds a bounded, acyclic exception graph. Repeated source
     * nodes reuse one provider-owned copy so benign shared relationships are
     * retained. Only relationships that would create a cycle are omitted.
     */
    private static final class ExceptionGraphSanitizer {

        private final SQLException sanitizedRoot;
        private final DiagnosticBudget diagnosticBudget;
        private final Map<Throwable, Throwable> sanitizedCopies = new IdentityHashMap<>();
        private final Map<Throwable, List<Throwable>> acceptedEdges = new IdentityHashMap<>();
        private final ArrayDeque<SqlExceptionNode> pending = new ArrayDeque<>();

        private ExceptionGraphSanitizer(SQLException root) {
            this(root, sqlMetadata(root));
        }

        private ExceptionGraphSanitizer(SQLException root, SqlMetadata rootMetadata) {
            this.diagnosticBudget = new DiagnosticBudget();
            this.sanitizedRoot = copySqlException(root, diagnosticBudget, rootMetadata);
            diagnosticBudget.initialize();
            // Java has no bounded suppressed-exception accessor, so driver-owned suppressed state remains opaque.
            sanitizedCopies.put(root, sanitizedRoot);
            pending.addLast(new SqlExceptionNode(root, sanitizedRoot));
        }

        /**
         * Copies safe SQL metadata without retaining a driver message or
         * throwable reference.
         *
         * @param source driver-owned SQL exception
         * @param diagnosticBudget shared diagnostic node budget
         * @param metadata safe SQL metadata
         * @return provider-owned SQL exception
         */
        private static SQLException copySqlException(SQLException source,
                                                     DiagnosticBudget diagnosticBudget,
                                                     SqlMetadata metadata) {
            String message = source instanceof SafeSQLException ? source.getMessage() : DRIVER_FAILURE;
            return new SafeSQLException(message, metadata.sqlState(), metadata.vendorCode(), diagnosticBudget);
        }

        /**
         * Copies the reachable graph within the diagnostic budget.
         *
         * @return bounded provider-owned root exception
         */
        private SQLException sanitize() {
            while (!pending.isEmpty()) {
                SqlExceptionNode node = pending.removeFirst();
                copyCause(node);
                copyNextException(node);
            }
            return sanitizedRoot;
        }

        /**
         * Copies one cause relationship when it does not create a cycle.
         *
         * @param node current SQL exception pair
         */
        private void copyCause(SqlExceptionNode node) {
            Throwable cause;
            try {
                cause = node.source().getCause();
            } catch (RuntimeException _) {
                return;
            }
            if (cause == null) {
                return;
            }
            Throwable copy = copyRelated(cause);
            if (copy != null && acceptsEdge(node.target(), copy)) {
                node.target().initCause(copy);
                recordEdge(node.target(), copy);
            }
        }

        /**
         * Copies one JDBC next-exception relationship when it does not create
         * a cycle.
         *
         * @param node current SQL exception pair
         */
        private void copyNextException(SqlExceptionNode node) {
            SQLException next;
            try {
                next = node.source().getNextException();
            } catch (RuntimeException _) {
                return;
            }
            if (next == null) {
                return;
            }
            Throwable copy = copyRelated(next);
            if (copy != null && acceptsEdge(node.target(), copy)) {
                node.target().setNextException((SQLException) copy);
                recordEdge(node.target(), copy);
            }
        }

        /**
         * Creates or reuses one bounded provider-owned copy. Reuse preserves
         * shared source topology without consuming another node-budget slot.
         *
         * @param source driver-owned related failure
         * @return provider-owned copy, or {@code null} when omitted
         */
        private Throwable copyRelated(Throwable source) {
            Throwable existing = sanitizedCopies.get(source);
            if (existing != null) {
                return existing;
            }
            if (!diagnosticBudget.reserve()) {
                return null;
            }
            Throwable copy;
            if (source instanceof SQLException sqlException) {
                SQLException sqlCopy = copySqlException(sqlException,
                                                        diagnosticBudget,
                                                        sqlMetadata(sqlException));
                copy = sqlCopy;
                pending.addLast(new SqlExceptionNode(sqlException, sqlCopy));
            } else {
                copy = relatedDiagnostic("processing a related JDBC failure", source);
            }
            sanitizedCopies.put(source, copy);
            return copy;
        }

        /**
         * Determines whether a candidate relationship can be retained without
         * introducing a cycle into the sanitized graph.
         *
         * @param parent receiving diagnostic
         * @param child related diagnostic
         * @return {@code true} when the relationship is safe to attach
         */
        private boolean acceptsEdge(Throwable parent, Throwable child) {
            if (parent == child || reaches(child, parent)) {
                return false;
            }
            return true;
        }

        /**
         * Tests reachability using only relationships already accepted into
         * the bounded provider-owned graph. The graph contains at most
         * {@value #MAX_EXCEPTION_GRAPH_NODES} nodes.
         *
         * @param start traversal start
         * @param expected node being sought
         * @return {@code true} when {@code expected} is reachable
         */
        private boolean reaches(Throwable start, Throwable expected) {
            Map<Throwable, Boolean> visited = new IdentityHashMap<>();
            ArrayDeque<Throwable> reachable = new ArrayDeque<>();
            reachable.addLast(start);
            while (!reachable.isEmpty()) {
                Throwable current = reachable.removeFirst();
                if (current == expected) {
                    return true;
                }
                if (visited.put(current, Boolean.TRUE) != null) {
                    continue;
                }
                List<Throwable> children = acceptedEdges.get(current);
                if (children != null) {
                    reachable.addAll(children);
                }
            }
            return false;
        }

        /**
         * Records one accepted relationship for subsequent bounded cycle
         * checks without reading throwable-owned suppressed arrays.
         *
         * @param parent receiving diagnostic
         * @param child related diagnostic
         */
        private void recordEdge(Throwable parent, Throwable child) {
            acceptedEdges.computeIfAbsent(parent, _ -> new ArrayList<>(2)).add(child);
        }

        /**
         * Driver-owned and provider-owned forms of one retained SQL exception.
         *
         * @param source driver-owned exception
         * @param target provider-owned exception
         */
        private record SqlExceptionNode(SQLException source, SQLException target) {
        }
    }

    /**
     * Shared node budget for one provider-owned SQL diagnostic graph.
     */
    private static final class DiagnosticBudget {
        private int nodes;

        private void initialize() {
            nodes = 1;
        }

        private boolean reserve() {
            if (nodes == MAX_EXCEPTION_GRAPH_NODES) {
                return false;
            }
            nodes++;
            return true;
        }

        private void attach(SQLException target, Throwable related) {
            // Omit excess relationships because a limit marker would consume space without useful JDBC detail.
            if (reserve()) {
                target.addSuppressed(related);
            }
        }
    }

    private record SqlMetadata(String sqlState, int vendorCode, boolean vendorCodeAvailable) {

        private String vendorCodeDescription() {
            return vendorCodeAvailable ? Integer.toString(vendorCode) : "not provided";
        }
    }

    /**
     * Marks an SQL exception whose message was created by this provider and is
     * therefore safe to preserve during repeated sanitization.
     */
    private interface SafeSqlDiagnostic {
        DiagnosticBudget diagnosticBudget();
    }

    private static final class SafeSQLException extends SQLException implements SafeSqlDiagnostic {
        private static final long serialVersionUID = 1L;
        private final DiagnosticBudget diagnosticBudget;

        private SafeSQLException(String message) {
            super(message);
            diagnosticBudget = new DiagnosticBudget();
            diagnosticBudget.initialize();
        }

        private SafeSQLException(String message, String sqlState, int vendorCode) {
            super(message, sqlState, vendorCode);
            diagnosticBudget = new DiagnosticBudget();
            diagnosticBudget.initialize();
        }

        private SafeSQLException(String message,
                                 String sqlState,
                                 int vendorCode,
                                 DiagnosticBudget diagnosticBudget) {
            super(message, sqlState, vendorCode);
            this.diagnosticBudget = diagnosticBudget;
        }

        @Override
        public DiagnosticBudget diagnosticBudget() {
            return diagnosticBudget;
        }
    }

    /**
     * Provider-owned non-SQL diagnostic which deliberately has no original
     * cause.
     */
    private static final class SafeDiagnosticException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private SafeDiagnosticException(String message) {
            super(message);
        }
    }
}
