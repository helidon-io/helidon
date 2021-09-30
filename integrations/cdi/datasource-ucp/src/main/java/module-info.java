/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 * Provides classes and interfaces that integrate the <a
 * href="https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/index.html"
 * target="_parent">Oracle Universal Connection Pool</a> into CDI as a
 * provider of {@link javax.sql.DataSource} beans.
 *
 * @see
 * io.helidon.integrations.datasource.ucp.cdi.UCPBackedDataSourceExtension
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.datasource.ucp.cdi {
    requires transitive java.sql;
    requires java.desktop; // For java.beans
    requires java.naming; // PoolDataSourceImpl implements javax.naming.Referenceable
    requires transitive jakarta.inject.api;
    requires transitive jakarta.enterprise.cdi.api;
    requires microprofile.config.api;
    requires com.oracle.database.ucp;
    requires transitive io.helidon.integrations.datasource.cdi;

    exports io.helidon.integrations.datasource.ucp.cdi;

    provides javax.enterprise.inject.spi.Extension
            with io.helidon.integrations.datasource.ucp.cdi.UCPBackedDataSourceExtension;
}
