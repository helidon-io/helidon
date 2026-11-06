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
package io.helidon.data.sql.datasource;

import java.sql.SQLException;

/**
 * Retains bounded probe diagnostics without driver messages or connection information.
 */
final class DataSourceProbeFailures {
    private static final int MAX_SUPPRESSED = 16;

    private DataSourceProbeFailures() {
    }

    static RuntimeException translateFailure(String operation, Throwable failure) {
        Throwable safe = mergeInfrastructure(null, operation, failure);
        if (safe instanceof Error error) {
            throw error;
        }
        return (RuntimeException) safe;
    }

    static Throwable mergeInfrastructure(Throwable primary, String operation, Throwable failure) {
        if (primary == failure) {
            return primary;
        }
        Throwable safe = failure instanceof Error || failure instanceof ProbeFailure ? failure
                : new ProbeFailure("The database health probe failed while " + operation + ".");
        if (primary == null) {
            return safe;
        }
        if (safe instanceof Error && !(primary instanceof Error)) {
            safe.addSuppressed(primary);
            return safe;
        }
        if (primary.getSuppressed().length < MAX_SUPPRESSED) {
            primary.addSuppressed(safe);
        }
        return primary;
    }

    static SQLException safeException(String message) {
        return new SQLException(message);
    }

    private static final class ProbeFailure extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private ProbeFailure(String message) {
            super(message);
        }
    }
}
