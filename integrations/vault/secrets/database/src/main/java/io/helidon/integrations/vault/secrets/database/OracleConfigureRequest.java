/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.secrets.database;

import java.time.Duration;

/**
 * Request to configure Oracle database.
 */
public class OracleConfigureRequest extends DbConfigure.Request<OracleConfigureRequest> {
    protected OracleConfigureRequest(String url) {
        super("oracle-database-plugin");

        add("connection_url", url);
    }

    /**
     * A new request builder for Oracle DB.
     *
     * @param url the Oracle DSN
     * @return a new request
     */
    public static OracleConfigureRequest builder(String url) {
        return new OracleConfigureRequest(url);
    }

    /**
     * Specifies the maximum number of open connections to the database.
     *
     * @param count number of open connections allowed
     * @return updated request
     */
    public OracleConfigureRequest maxOpenConnections(int count) {
        return add("max_open_connections", count);
    }

    /**
     * Specifies the maximum number of idle connections to the database.
     * A zero uses the value of max_open_connections and a negative value disables idle connections.
     * If larger than max_open_connections it will be reduced to be equal.
     *
     * @param count number of allowed idle connections
     * @return updated request
     */
    public OracleConfigureRequest maxIdleConnections(int count) {
        return add("max_idle_connections", count);
    }

    /**
     * Specifies the maximum amount of time a connection may be reused. If <= 0s connections are reused forever.
     *
     * @param duration maximal lifetime of a connection
     * @return updated request
     */
    public OracleConfigureRequest maxConnectionLifetimeSeconds(Duration duration) {
        return add("max_connection_lifetime", duration);
    }
}
