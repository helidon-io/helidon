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
package io.helidon.data.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import io.helidon.common.Weighted;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcPersistenceUnitFactoryHikariTest {

    @Test
    void returnsScriptConnectionAfterSqlFailure() {
        try (HikariDataSource source = dataSource("invalid_script", true)) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "invalid-script",
                    "data.persistence-units.jdbc.0.data-source", "invalid-script-source",
                    "data.persistence-units.jdbc.0.init-script.resource-path", "jdbc-bootstrap-invalid.sql")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("invalid-script-source", source)),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            DataException failure = assertThrows(DataException.class, factory::services);

            assertThat(failure.getMessage(), containsString("init bootstrap resource #1 (classpath)"));
            assertThat(failure.getMessage(), not(containsString("jdbc-bootstrap-invalid.sql")));
            assertThat(failure.getMessage(), containsString("statement 2"));
            assertThat(failure.getCause(), instanceOf(java.sql.SQLException.class));
            assertThat(source.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    @Test
    void commitsBootstrapWorkWhenDatasourceDisablesAutoCommit() throws Exception {
        try (HikariDataSource source = dataSource("manual_bootstrap", false)) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "manual-bootstrap",
                    "data.persistence-units.jdbc.0.data-source", "manual-bootstrap-source",
                    "data.persistence-units.jdbc.0.init-script.resource-path", "jdbc-bootstrap-init.sql")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("manual-bootstrap-source", source)),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            factory.services();

            try (var connection = source.getConnection();
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM SCRIPT_VALUE")) {
                resultSet.next();
                assertThat(resultSet.getLong(1), is(2L));
                connection.rollback();
            }
        }
    }

    @Test
    void rollsBackBootstrapWorkWhenDatasourceDisablesAutoCommit() throws Exception {
        try (HikariDataSource source = dataSource("manual_bootstrap_failure", false)) {
            try (var connection = source.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE BOOTSTRAP_TX (ID INTEGER PRIMARY KEY)");
                connection.commit();
            }
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "manual-bootstrap-failure",
                    "data.persistence-units.jdbc.0.data-source", "manual-bootstrap-failure-source",
                    "data.persistence-units.jdbc.0.init-script.resource-path",
                    "jdbc-bootstrap-transaction-invalid.sql")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("manual-bootstrap-failure-source", source)),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            assertThrows(DataException.class, factory::services);

            try (var connection = source.getConnection();
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM BOOTSTRAP_TX")) {
                resultSet.next();
                assertThat(resultSet.getLong(1), is(0L));
                connection.rollback();
            }
        }
    }

    private static HikariDataSource dataSource(String databaseName, boolean autoCommit) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        config.setAutoCommit(autoCommit);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static ServiceInstance<DataSource> instance(String name, DataSource dataSource) {
        return new TestServiceInstance(dataSource, Set.of(Qualifier.createNamed(name)));
    }

    private record TestServiceInstance(DataSource value,
                                       Set<Qualifier> qualifiers) implements ServiceInstance<DataSource> {
        @Override
        public DataSource get() {
            return value;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(TypeName.create(DataSource.class)));
        }

        @Override
        public TypeName scope() {
            return TypeName.create(Service.Singleton.class);
        }

        @Override
        public double weight() {
            return Weighted.DEFAULT_WEIGHT;
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create(JdbcPersistenceUnitFactoryHikariTest.class);
        }
    }
}
