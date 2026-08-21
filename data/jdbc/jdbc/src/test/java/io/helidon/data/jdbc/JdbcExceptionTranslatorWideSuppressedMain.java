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
import java.util.List;

import io.helidon.data.DataException;

/**
 * Isolated small-heap entry point used by
 * {@link JdbcExceptionTranslatorGraphTest}.
 */
public final class JdbcExceptionTranslatorWideSuppressedMain {
    private static final int SUPPRESSED_COUNT = 200_000;

    private JdbcExceptionTranslatorWideSuppressedMain() {
    }

    /**
     * Builds a wide driver-owned suppressed list and sanitizes it in the
     * current process.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        try {
            SQLException root = new ThinSQLException("private root", "42000", 1);
            SQLException shared = new ThinSQLException("private suppressed", "42001", 2);
            populateSuppressed(root, shared);

            DataException failure = JdbcExceptionTranslator.translate("query", root);
            Throwable sanitized = failure.getCause();
            List<Throwable> graph = JdbcExceptionTranslatorGraphTest.graph(sanitized);
            long truncationMarkers = JdbcExceptionTranslatorGraphTest.truncationMarkers(graph);

            System.out.println("sourceSuppressed=" + SUPPRESSED_COUNT
                                       + " nodes=" + graph.size()
                                       + " truncationMarkers=" + truncationMarkers
                                       + " suppressed=" + sanitized.getSuppressed().length);
            if (graph.size() != 1 || truncationMarkers != 0 || sanitized.getSuppressed().length != 0) {
                System.exit(2);
            }
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void populateSuppressed(SQLException root, SQLException shared) {
        for (int count = 0; count < SUPPRESSED_COUNT; count++) {
            root.addSuppressed(shared);
        }
    }

    private static final class ThinSQLException extends SQLException {
        private static final long serialVersionUID = 1L;

        private ThinSQLException(String reason, String sqlState, int vendorCode) {
            super(reason, sqlState, vendorCode);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
