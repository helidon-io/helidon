/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.data.tests.sql.ucp;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.data.sql.datasource.ucp.UcpDataSourceConfig;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.testing.junit5.suite.TestSuite;

import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@TestSuite.Suite(OraDbSuite.class)
@Testcontainers(disabledWithoutDocker = true)
@Testing.Test
class TestUcp {

    @Test
    void testDataSourceConfig(Config config) {
        assertThat(config.exists(), is(true));
        DataSourceConfig dataSourceConfig = DataSourceConfig.create(config.get("data.sources.sql.0"));
        UcpDataSourceConfig ucpDataSourceConfig = (UcpDataSourceConfig) dataSourceConfig.provider();
        assertThat(ucpDataSourceConfig.username().isPresent(), is(true));
        assertThat(ucpDataSourceConfig.username().get(),
                   is(config.get("data.sources.sql.0.provider.ucp.username").as(String.class).get()));
        assertThat(ucpDataSourceConfig.password().isPresent(), is(true));
        assertThat(new String(ucpDataSourceConfig.password().get()),
                   is(config.get("data.sources.sql.0.provider.ucp.password").as(String.class).get()));
        assertThat(ucpDataSourceConfig.url(),
                   is(config.get("data.sources.sql.0.provider.ucp.url").as(String.class).get()));
        assertThat(ucpDataSourceConfig.connectionFactoryProperties().orElseThrow(),
                   is(Map.of("description", "Helidon UCP test connection factory",
                             "implicitCachingEnabled", "true",
                             "maxStatements", "23")));
        assertThat(ucpDataSourceConfig.connectionProperties().orElseThrow(),
                   is(Map.of("defaultRowPrefetch", "42",
                             "includeSynonyms", "true",
                             "remarksReporting", "true")));
    }

    @Test
    void testDataSourceRegistry() throws SQLException {
        TypeName ucpName = TypeName.create("io.helidon.data.sql.datasource.ucp.UcpDataSourceProviderService");
        Service.ServicesFactory<DataSource> provider = Services.get(ucpName);
        assertThat(provider, notNullValue());
        List<Service.QualifiedInstance<DataSource>> services = provider.services();
        assertThat(services.size(), is(1));

        DataSource dataSource = services.getFirst().get();
        assertThat(dataSource, instanceOf(PoolDataSource.class));
        PoolDataSource poolDataSource = (PoolDataSource) dataSource;
        assertThat(poolDataSource.getConnectionFactoryProperties().size(), is(3));
        assertThat(poolDataSource.getConnectionFactoryProperty("description"),
                   is("Helidon UCP test connection factory"));
        assertThat(poolDataSource.getConnectionFactoryProperty("implicitCachingEnabled"), is("true"));
        assertThat(poolDataSource.getConnectionFactoryProperty("maxStatements"), is("23"));
        assertThat(poolDataSource.getConnectionProperties().size(), is(3));
        assertThat(poolDataSource.getConnectionProperty("defaultRowPrefetch"), is("42"));
        assertThat(poolDataSource.getConnectionProperty("includeSynonyms"), is("true"));
        assertThat(poolDataSource.getConnectionProperty("remarksReporting"), is("true"));

        try (Connection connection = poolDataSource.getConnection()) {
            assertThat(connection.isValid(0), is(true));
        }
    }

}
