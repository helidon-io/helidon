/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.dbclient.jdbc.spi.HikariCpExtensionProvider;

/**
 * Helidon JDBC DB Client Metrics.
 */
module io.helidon.dbclient.metrics.jdbc {
    requires java.logging;
    requires io.helidon.dbclient;
    requires io.helidon.dbclient.jdbc;
    requires io.helidon.metrics;
    requires io.helidon.dbclient.metrics;
    requires com.zaxxer.hikari;
    requires com.codahale.metrics;

    exports io.helidon.dbclient.metrics.jdbc;

    provides HikariCpExtensionProvider with io.helidon.dbclient.metrics.jdbc.JdbcMetricsExtensionProvider;

}
