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
package io.helidon.data.jdbc.tests.chaos.support;

import io.helidon.data.jdbc.tests.chaos.application.ChaosSql;

/**
 * Centralizes secret canaries that chaos tests must not expose through public failures.
 */
public final class ChaosSecrets {
    /**
     * Database name canary embedded in a test JDBC URL.
     */
    public static final String DATABASE_NAME_CANARY = "private_chaos_database_url_canary";

    /**
     * URL canary embedded in a test JDBC URL.
     */
    public static final String URL_CANARY = "private-chaos-url-canary";

    private ChaosSecrets() {
    }

    /**
     * Returns the canaries expected to stay out of malformed SQL diagnostics.
     *
     * @return malformed SQL canaries
     */
    public static String[] malformedSqlCanaries() {
        return new String[] {
                ChaosSql.MALFORMED_SQL_CANARY,
                ChaosSql.MALFORMED_QUERY,
                "MISSING_CHAOS_COLUMN",
                DATABASE_NAME_CANARY,
                URL_CANARY
        };
    }

    /**
     * Returns the canaries expected to stay out of bind-value failure diagnostics.
     *
     * @return bind-value canaries
     */
    public static String[] bindValueCanaries() {
        return new String[] {
                ChaosSql.BIND_VALUE_CANARY,
                DATABASE_NAME_CANARY,
                URL_CANARY
        };
    }

    /**
     * Returns the canaries expected to stay out of conversion failure diagnostics.
     *
     * @return conversion canaries
     */
    public static String[] conversionCanaries() {
        return new String[] {
                ChaosSql.CONVERSION_VALUE_CANARY,
                ChaosSql.CONVERSION_FAILURE_QUERY,
                DATABASE_NAME_CANARY,
                URL_CANARY
        };
    }
}
