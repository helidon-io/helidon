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
import java.sql.SQLException;
import java.util.concurrent.Executor;

/**
 * Invalidates a JDBC connection that must not return to ordinary pool reuse.
 * <p>
 * Invalidation first requests {@link Connection#abort(Executor)} and then
 * invokes {@link Connection#close()} as a fallback cleanup signal. Failures
 * from either driver call are sanitized before suppression so invalidation
 * cannot introduce raw driver diagnostics into the primary failure tree.
 */
final class JdbcConnectionInvalidator {

    // Connection.abort requires an executor even though provider cleanup is synchronous.
    private static final Executor ABORT_EXECUTOR = Runnable::run;

    private JdbcConnectionInvalidator() {
    }

    /**
     * Aborts an unsafe connection and then closes it as a fallback. Both calls
     * are attempted because a failed abort must not skip the close fallback.
     * This method never replaces the failure which caused invalidation.
     *
     * @param connection unsafe connection
     * @param primaryFailure failure which required invalidation
     */
    static void invalidate(Connection connection, Throwable primaryFailure) {
        try {
            connection.abort(ABORT_EXECUTOR);
        } catch (SQLException | RuntimeException | Error abortFailure) {
            JdbcExceptionTranslator.suppress(primaryFailure, "connection abort", abortFailure);
        }
        try {
            connection.close();
        } catch (SQLException | RuntimeException | Error closeFailure) {
            JdbcExceptionTranslator.suppress(primaryFailure, "invalidated connection close", closeFailure);
        }
    }
}
