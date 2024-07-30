/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
 * CDI integration for the <a
 * href="https://github.com/brettwooldridge/HikariCP/blob/HikariCP-5.1.0/README.md#-hikaricpits-fasterhikari-hikal%C4%93-origin-japanese-light-ray"
 * target="_parent">Hikari connection pool</a>.
 *
 * @see
 * io.helidon.integrations.datasource.hikaricp.cdi.HikariCPBackedDataSourceExtension
 */
@SuppressWarnings({ "requires-automatic"})
module io.helidon.integrations.datasource.hikaricp.cdi {

    requires com.zaxxer.hikari;
    requires jakarta.annotation;

    requires microprofile.metrics.api;

    requires transitive io.helidon.integrations.datasource.cdi;
    requires transitive jakarta.cdi;
    requires transitive jakarta.inject;
    requires transitive java.sql;

    exports io.helidon.integrations.datasource.hikaricp.cdi;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.datasource.hikaricp.cdi.HikariCPBackedDataSourceExtension;

}
