/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.dbclient.hikari.spi.HikariMetricsProvider;

/**
 * Helidon Database Client JDBC.
 */
@Feature(value = "Hikari Connection Pool support for Helidon JDBC Database Client",
         description = "Hikari connection pool for JDBC DB client",
         in = HelidonFlavor.SE,
         path = {"DbClient", "Hikari"}
)
module io.helidon.dbclient.hikari {

    requires static io.helidon.common.features.api;

    requires java.sql;
    requires com.zaxxer.hikari;

    requires transitive io.helidon.common;
    requires transitive io.helidon.dbclient;
    requires transitive io.helidon.dbclient.jdbc;
    requires transitive io.helidon.builder.api;

    requires static io.helidon.config.metadata;

    exports io.helidon.dbclient.hikari;
    exports io.helidon.dbclient.hikari.spi;

    uses HikariMetricsProvider;

    provides io.helidon.dbclient.jdbc.spi.JdbcConnectionPoolProvider
            with io.helidon.dbclient.hikari.HikariConnectionPoolProvider;

}
