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
 * Request to configure MySQL database.
 */
public class MySqlConfigureRequest extends DbConfigure.Request<MySqlConfigureRequest> {
    protected MySqlConfigureRequest(String connectionUrl) {
        super("mysql-database-plugin");
        add("connection_url", connectionUrl);
    }

    /**
     * A new request builder with connection URL.
     * <p>
     * URL specifies the MySQL DSN. This field can be templated and supports passing the username and password parameters in the
     * following format {@code {{field_name}}}. A templated connection URL is required when using root credential rotation.
     * <p>
     * Example: {@code {{username}}:{{password}}@tcp(127.0.0.1:3306)}
     *
     * @param connectionUrl URL to connect to the database
     * @return a new request builder
     */
    public static MySqlConfigureRequest builder(String connectionUrl) {
        return new MySqlConfigureRequest(connectionUrl);
    }

    /**
     * Specifies the maximum number of open connections to the database.
     * Default value is {@code 4}.
     *
     * @param connections number of connections
     * @return updated request
     */
    public MySqlConfigureRequest maxOpenConnections(int connections) {
        return add("max_open_connections", connections);
    }

    /**
     * Specifies the maximum number of idle connections to the database.
     * A zero uses the value of {@link #maxOpenConnections(int)} and a
     * negative value disables idle connections. If larger than {@link #maxOpenConnections(int)} it will be reduced to be equal.
     *
     * @param connections number of connections
     * @return updated request
     */
    public MySqlConfigureRequest maxIdleConnections(int connections) {
        return add("max_idle_connections", connections);
    }

    /**
     * Specifies the maximum amount of time a connection may be reused. If &lt;= 0s connections are reused forever.
     *
     * @param duration maximal lifetime of a connection
     * @return updated request
     */
    public MySqlConfigureRequest maxConnectionLifetime(Duration duration) {
        return add("max_connection_lifetime", duration);
    }

    /**
     * x509 certificate for connecting to the database. This must be a PEM encoded version of the private key and the
     * certificate combined.
     *
     * @param certificateWithKey certificate and key in PEM format
     * @return updated request
     */
    public MySqlConfigureRequest tlsCertificateKey(String certificateWithKey) {
        return add("tls_certificate_key", certificateWithKey);
    }

    /**
     * x509 CA file for validating the certificate presented by the MySQL server. Must be PEM encoded.
     *
     * @param tlsCa Certification authority certificate in PEM format
     * @return updated request
     */
    public MySqlConfigureRequest tlsCa(String tlsCa) {
        return add("tls_ca", tlsCa);
    }
}
