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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.data.DataException;

/**
 * Creates application-visible JDBC diagnostics without retaining confidential
 * driver or environment text.
 * <p>
 * Driver-owned throwables never cross this boundary by reference. SQL
 * exceptions are rebuilt from SQL state and vendor code, non-SQL failures are
 * represented by a stable provider operation and throwable class, and warning
 * chains and exception graphs are copied with explicit cycle and size limits.
 * Provider-owned safe diagnostic types make repeated sanitization idempotent.
 */
final class JdbcExceptionTranslator {

    // A short digest correlates repeated SQL failures without revealing the statement.
    private static final int FINGERPRINT_BYTES = 12;

    // Limits both work and the number of suppressed diagnostics accepted from one JDBC owner.
    private static final int MAX_WARNINGS_PER_OWNER = 64;

    // Includes the root and the truncation marker. One slot is reserved for that marker.
    private static final int MAX_EXCEPTION_GRAPH_NODES = 16;
    private static final int MAX_COPIED_EXCEPTION_GRAPH_NODES = MAX_EXCEPTION_GRAPH_NODES - 1;
    private static final String DRIVER_FAILURE = "The JDBC driver reported a failure.";
    private static final String DRIVER_WARNING = "The JDBC driver reported a warning.";
    private static final String RESULT_VALUE_FAILURE = "The JDBC provider could not read a result value.";
    private static final String TRUNCATED_EXCEPTION_GRAPH =
            "Some JDBC failure relationships were not inspected or were omitted to keep diagnostics bounded.";

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
        return translate(operationName(operation), operation.sql(), cause);
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
     * Translates a driver failure without including any bound values.
     *
     * @param operation operation name
     * @param sql statement text
     * @param cause driver failure
     * @return data-layer failure
     */
    static DataException translate(String operation, String sql, SQLException cause) {
        String state = cause.getSQLState() == null ? "not provided" : "'" + cause.getSQLState() + "'";
        String message = "The JDBC " + operation + " failed. The SQL state is " + state
                + ", the vendor code is " + cause.getErrorCode() + ", and the SQL fingerprint is '"
                + fingerprint(sql) + "'.";
        return new DataException(message, safeCause(cause));
    }

    /**
     * Creates a bounded identity for SQL without retaining its text in a
     * diagnostic.
     *
     * @param sql SQL text
     * @return hexadecimal fingerprint
     */
    static String fingerprint(String sql) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, FINGERPRINT_BYTES);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by every Java implementation.
            throw new IllegalStateException("The SHA-256 message digest is unavailable.", e);
        }
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
        if (cause instanceof SafeSqlDiagnostic safe) {
            safe.diagnosticBudget().finish();
            return cause;
        }
        return new ExceptionGraphSanitizer(cause).sanitize();
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
        if (failure instanceof SafeSQLException
                || failure instanceof SafeSQLWarning
                || failure instanceof SafeDiagnosticException) {
            return failure;
        }
        if (failure instanceof SQLException sqlException) {
            return new ExceptionGraphSanitizer(sqlException).sanitize(false);
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
        if (failure instanceof SafeSqlDiagnostic safe) {
            safe.diagnosticBudget().finish();
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
                    : new ExceptionGraphSanitizer(sqlPrimary).sanitize(false);
            SafeSqlDiagnostic safePrimary = (SafeSqlDiagnostic) sanitizedPrimary;
            Throwable sanitized = relatedDiagnostic(operation, failure);
            DiagnosticBudget diagnosticBudget = safePrimary.diagnosticBudget();
            if (failure instanceof SQLException
                    && (!(failure instanceof SafeSqlDiagnostic safeRelated)
                            || safeRelated.diagnosticBudget().hasOmissions())) {
                diagnosticBudget.omitted();
            }
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
     * Creates one safe leaf for a provider-observed related failure. Its source
     * relationships are deliberately not traversed; the receiving SQL graph's
     * marker represents those opaque relationships.
     *
     * @param operation stable provider operation label
     * @param failure related JDBC-owned failure
     * @return provider-owned leaf diagnostic
     */
    private static Throwable relatedDiagnostic(String operation, Throwable failure) {
        if (failure instanceof SQLException sqlException) {
            boolean safeWarning = failure instanceof SafeSQLWarning;
            String message = failure instanceof SafeSQLException || safeWarning
                    ? failure.getMessage()
                    : DRIVER_FAILURE;
            return safeWarning
                    ? new SafeSQLWarning(message, sqlException.getSQLState(), sqlException.getErrorCode())
                    : new SafeSQLException(message, sqlException.getSQLState(), sqlException.getErrorCode());
        }
        if (failure instanceof SafeDiagnosticException safe) {
            return new SafeDiagnosticException(safe.getMessage());
        }
        return diagnostic(operation, failure);
    }

    /**
     * Rebuilds a warning-processing failure using stable provider text.
     * Driver messages, causes, and suppressed failures are not retained.
     *
     * @param owner stable warning owner
     * @param failure warning-processing failure
     * @return sanitized provider-owned diagnostic
     */
    static Throwable warningFailure(String owner, Throwable failure) {
        String message = warningFailureMessage(owner);
        if (failure instanceof SQLException sqlException) {
            return new SafeSQLException(message, sqlException.getSQLState(), sqlException.getErrorCode());
        }
        return new SafeDiagnosticException(message);
    }

    /**
     * Copies one JDBC warning chain in encounter order. The returned failures
     * have no driver-owned causes, suppressed failures, next-exception links,
     * or messages. Broken and cyclic chains terminate with one provider-owned
     * diagnostic rather than growing an application-visible failure tree. At
     * most {@value #MAX_WARNINGS_PER_OWNER} warnings are copied for one JDBC
     * owner.
     *
     * @param operation stable warning-owner label
     * @param first first warning, or {@code null}
     * @return sanitized warnings in encounter order
     */
    static List<Throwable> sanitizeWarnings(String operation, SQLWarning first) {
        if (first == null) {
            return List.of();
        }
        List<Throwable> sanitized = new ArrayList<>();
        Map<SQLWarning, Boolean> visited = new IdentityHashMap<>();
        SQLWarning current = first;
        while (current != null) {
            // A driver can return an overlong or cyclic warning chain.
            if (sanitized.size() == MAX_WARNINGS_PER_OWNER || visited.put(current, Boolean.TRUE) != null) {
                sanitized.add(new SafeDiagnosticException(warningFailureMessage(operation)));
                break;
            }
            try {
                // The returned list preserves order without retaining a driver-owned next link.
                sanitized.add(new SafeSQLWarning(DRIVER_WARNING,
                                                 current.getSQLState(),
                                                 current.getErrorCode()));
                current = current.getNextWarning();
            } catch (RuntimeException traversalFailure) {
                sanitized.add(warningFailure(operation, traversalFailure));
                break;
            }
        }
        return List.copyOf(sanitized);
    }

    /**
     * Returns the stable message for a warning owner.
     *
     * @param owner stable warning owner
     * @return safe warning-processing message
     */
    private static String warningFailureMessage(String owner) {
        return "The JDBC provider could not process " + owner + "s.";
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
            this.diagnosticBudget = new DiagnosticBudget();
            this.sanitizedRoot = copySqlException(root, diagnosticBudget);
            diagnosticBudget.initialize(sanitizedRoot);
            // Java has no bounded suppressed-exception accessor, so driver-owned suppressed state remains opaque.
            diagnosticBudget.omitted();
            sanitizedCopies.put(root, sanitizedRoot);
            pending.addLast(new SqlExceptionNode(root, sanitizedRoot));
        }

        /**
         * Copies the reachable graph within the diagnostic budget.
         *
         * @return bounded provider-owned root exception
         */
        private SQLException sanitize() {
            return sanitize(true);
        }

        /**
         * Copies the reachable graph and optionally seals its omission marker.
         * An unsealed graph may receive provider-observed cleanup diagnostics
         * before it crosses the application boundary.
         */
        private SQLException sanitize(boolean finish) {
            while (!pending.isEmpty()) {
                SqlExceptionNode node = pending.removeFirst();
                copyCause(node);
                copyNextException(node);
            }
            if (finish) {
                diagnosticBudget.finish();
            }
            return sanitizedRoot;
        }

        /**
         * Copies one cause relationship when it does not create a cycle.
         *
         * @param node current SQL exception pair
         */
        private void copyCause(SqlExceptionNode node) {
            Throwable cause = node.source().getCause();
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
            SQLException next = node.source().getNextException();
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
                SQLException sqlCopy = copySqlException(sqlException, diagnosticBudget);
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
                diagnosticBudget.omitted();
                return false;
            }
            return true;
        }

        /**
         * Tests reachability using only relationships already accepted into
         * the bounded provider-owned graph. The graph contains at most
         * {@value #MAX_COPIED_EXCEPTION_GRAPH_NODES} nodes.
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
            acceptedEdges.computeIfAbsent(parent, ignored -> new ArrayList<>(2)).add(child);
        }

        /**
         * Copies safe SQL metadata without retaining a driver message or
         * throwable reference.
         *
         * @param source driver-owned SQL exception
         * @return provider-owned SQL exception
         */
        private static SQLException copySqlException(SQLException source, DiagnosticBudget diagnosticBudget) {
            boolean safeWarning = source instanceof SafeSQLWarning;
            String message = source instanceof SafeSQLException || safeWarning ? source.getMessage() : DRIVER_FAILURE;
            return safeWarning
                    ? new SafeSQLWarning(message, source.getSQLState(), source.getErrorCode(), diagnosticBudget)
                    : new SafeSQLException(message, source.getSQLState(), source.getErrorCode(), diagnosticBudget);
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
        private SQLException root;
        private int nodes;
        private boolean markerRequired;
        private boolean markerAdded;

        private void initialize(SQLException root) {
            this.root = root;
            this.nodes = 1;
        }

        private boolean reserve() {
            int limit = markerAdded ? MAX_EXCEPTION_GRAPH_NODES : MAX_COPIED_EXCEPTION_GRAPH_NODES;
            if (nodes == limit) {
                omitted();
                return false;
            }
            nodes++;
            return true;
        }

        private void attach(SQLException target, Throwable related) {
            if (reserve()) {
                target.addSuppressed(related);
            }
        }

        private void omitted() {
            markerRequired = true;
        }

        private boolean hasOmissions() {
            return markerRequired;
        }

        private void finish() {
            if (markerRequired && !markerAdded) {
                root.addSuppressed(new SafeDiagnosticException(TRUNCATED_EXCEPTION_GRAPH));
                nodes++;
                markerAdded = true;
            }
        }
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
            diagnosticBudget.initialize(this);
        }

        private SafeSQLException(String message, String sqlState, int vendorCode) {
            super(message, sqlState, vendorCode);
            diagnosticBudget = new DiagnosticBudget();
            diagnosticBudget.initialize(this);
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
     * Sanitized warning with no driver-owned cause, suppressed tree, or next
     * warning link.
     */
    private static final class SafeSQLWarning extends SQLWarning implements SafeSqlDiagnostic {
        private static final long serialVersionUID = 1L;
        private final DiagnosticBudget diagnosticBudget;

        private SafeSQLWarning(String message, String sqlState, int vendorCode) {
            super(message, sqlState, vendorCode);
            diagnosticBudget = new DiagnosticBudget();
            diagnosticBudget.initialize(this);
        }

        private SafeSQLWarning(String message,
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
