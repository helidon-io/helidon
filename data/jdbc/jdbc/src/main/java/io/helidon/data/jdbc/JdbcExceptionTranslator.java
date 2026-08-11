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
 * chains are copied with explicit cycle and size limits. Provider-owned safe
 * diagnostic types make repeated sanitization idempotent.
 */
final class JdbcExceptionTranslator {

    // A short digest correlates repeated SQL failures without revealing the statement.
    private static final int FINGERPRINT_BYTES = 12;

    // Limits both work and the number of suppressed diagnostics accepted from one JDBC owner.
    private static final int MAX_WARNINGS_PER_OWNER = 64;
    private static final String DRIVER_FAILURE = "JDBC driver failure";
    private static final String DRIVER_WARNING = "JDBC driver warning";

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
        return translate(operation.preparationPlan().resultKind().name(), operation.sql(), cause);
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
        String state = cause.getSQLState() == null ? "unknown" : cause.getSQLState();
        String message = "JDBC " + operation + " failed [SQLState=" + state
                + ", vendorCode=" + cause.getErrorCode() + ", sqlFingerprint=" + fingerprint(sql) + "]";
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
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Copies safe JDBC metadata without retaining driver-provided messages,
     * SQL text, URLs, arbitrary cause text, or driver-owned throwable
     * references. Identity tracking also makes recursive exception graphs safe
     * to copy.
     *
     * @param cause driver failure
     * @return sanitized failure tree
     */
    private static SQLException safeCause(SQLException cause) {
        return safeCause(cause, new IdentityHashMap<>());
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
        if (failure instanceof SafeSQLException
                || failure instanceof SafeSQLWarning
                || failure instanceof SafeDiagnosticException) {
            return failure;
        }
        if (failure instanceof SQLException sqlException) {
            return safeCause(sqlException);
        }
        return diagnostic(operation, failure);
    }

    /**
     * Attaches a sanitized JDBC-owned failure without risking self-suppression.
     * The primary failure is intentionally left unchanged because it may be an
     * application or mapper failure whose identity and type are contractual.
     *
     * @param primary receiving failure
     * @param operation stable provider operation label
     * @param failure JDBC-owned failure to attach
     */
    static void suppress(Throwable primary, String operation, Throwable failure) {
        Throwable sanitized = sanitize(operation, failure);
        if (primary != sanitized) {
            primary.addSuppressed(sanitized);
        }
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
                sanitized.add(new SafeDiagnosticException("JDBC " + operation + " chain truncated"));
                break;
            }
            try {
                // The returned list preserves order without retaining a driver-owned next link.
                sanitized.add(new SafeSQLWarning(DRIVER_WARNING,
                                                 current.getSQLState(),
                                                 current.getErrorCode()));
                current = current.getNextWarning();
            } catch (Throwable traversalFailure) {
                sanitized.add(diagnostic(operation + " traversal", traversalFailure));
                break;
            }
        }
        return List.copyOf(sanitized);
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
     * Recursively copies the safe portions of a JDBC exception graph.
     *
     * @param cause current driver or provider-owned SQL exception
     * @param copies identity map from source exceptions to sanitized copies
     * @return sanitized copy of {@code cause}
     */
    private static SQLException safeCause(SQLException cause, Map<SQLException, SQLException> copies) {
        SQLException existing = copies.get(cause);
        if (existing != null) {
            return existing;
        }
        boolean safeWarning = cause instanceof SafeSQLWarning;
        String message = cause instanceof SafeSQLException || safeWarning ? cause.getMessage() : DRIVER_FAILURE;
        SQLException copy = safeWarning
                ? new SafeSQLWarning(message, cause.getSQLState(), cause.getErrorCode())
                : new SafeSQLException(message, cause.getSQLState(), cause.getErrorCode());
        copies.put(cause, copy);
        for (Throwable suppressed : cause.getSuppressed()) {
            if (suppressed instanceof SQLException sqlException) {
                SQLException safe = safeCause(sqlException, copies);
                if (safe != copy) {
                    copy.addSuppressed(safe);
                }
            } else {
                copy.addSuppressed(sanitize("related", suppressed));
            }
        }
        Throwable nested = cause.getCause();
        if (nested instanceof SQLException sqlException) {
            SQLException safe = safeCause(sqlException, copies);
            if (safe != copy) {
                copy.initCause(safe);
            }
        } else if (nested != null) {
            copy.initCause(sanitize("related", nested));
        }
        SQLException next = cause.getNextException();
        if (next != null) {
            SQLException safe = safeCause(next, copies);
            if (safe != copy) {
                copy.setNextException(safe);
            }
        }
        return copy;
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
        return new SafeDiagnosticException("JDBC " + operation + " failure ["
                                                   + failure.getClass().getName() + "]");
    }

    /**
     * Marks an SQL exception whose message was created by this provider and is
     * therefore safe to preserve during repeated sanitization.
     */
    private static final class SafeSQLException extends SQLException {
        private static final long serialVersionUID = 1L;

        private SafeSQLException(String message) {
            super(message);
        }

        private SafeSQLException(String message, String sqlState, int vendorCode) {
            super(message, sqlState, vendorCode);
        }
    }

    /**
     * Sanitized warning with no driver-owned cause, suppressed tree, or next
     * warning link.
     */
    private static final class SafeSQLWarning extends SQLWarning {
        private static final long serialVersionUID = 1L;

        private SafeSQLWarning(String message, String sqlState, int vendorCode) {
            super(message, sqlState, vendorCode);
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
