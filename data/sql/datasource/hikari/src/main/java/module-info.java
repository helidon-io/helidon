/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

/**
 * Hikari {@link javax.sql.DataSource} support.
 */
module io.helidon.data.sql.datasource.hikari {

    requires io.helidon.common.config;
    requires io.helidon.builder.api;
    requires io.helidon.service.registry;
    requires transitive io.helidon.data.sql.common;
    requires io.helidon.data.sql.datasource;

    requires java.sql;
    requires com.zaxxer.hikari;

    exports io.helidon.data.sql.datasource.hikari;

    provides io.helidon.data.sql.datasource.spi.DataSourceConfigProvider
            with io.helidon.data.sql.datasource.hikari.HikariDataSourceConfigProvider;

}
