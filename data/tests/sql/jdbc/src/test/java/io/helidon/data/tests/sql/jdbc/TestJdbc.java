/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.tests.sql.jdbc;

import java.util.List;

import javax.sql.DataSource;

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.data.sql.datasource.jdbc.JdbcDataSourceConfig;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.testing.junit5.suite.Suite;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Suite(MySqlSuite.class)
@Testcontainers(disabledWithoutDocker = true)
@Testing.Test
class TestJdbc {
    @Test
    void testDataSourceConfig(Config config) {
        assertThat(config.exists(), is(true));
        DataSourceConfig dataSourceConfig = DataSourceConfig.create(config.get("data.sources.sql.0"));
        JdbcDataSourceConfig hikariDataSourceConfig = (JdbcDataSourceConfig) dataSourceConfig.provider();
        assertThat(hikariDataSourceConfig.username().isPresent(), is(true));
        assertThat(hikariDataSourceConfig.username().get(),
                   is(config.get("data.sources.sql.0.provider.jdbc.username").as(String.class).get()));
        assertThat(hikariDataSourceConfig.password().isPresent(), is(true));
        assertThat(new String(hikariDataSourceConfig.password().get()),
                   is(config.get("data.sources.sql.0.provider.jdbc.password").as(String.class).get()));
        assertThat(hikariDataSourceConfig.url(),
                   is(config.get("data.sources.sql.0.provider.jdbc.url").as(String.class).get()));
    }

    @Test
    void testDataSourceRegistry() {
        TypeName jdbcName = TypeName.create("io.helidon.data.sql.datasource.jdbc.JdbcDataSourceService");
        Service.ServicesFactory<DataSource> factory = Services.get(jdbcName);
        List<Service.QualifiedInstance<DataSource>> services = factory.services();
        assertThat(services.size(), is(1));
    }

}
