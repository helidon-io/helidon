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

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.common.Weighted;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Portable bootstrap configuration diagnostic contract shared by all supported
 * JDBC database leaves.
 */
public abstract class AbstractJdbcBootstrapDiagnosticTest {
    private static final String SOURCE_NAME = "source";

    /**
     * Creates a simple datasource backed by {@link DriverManager}.
     *
     * @param url JDBC URL
     * @param username username, or {@code null}
     * @param password password, or {@code null}
     * @return datasource
     */
    protected static DataSource driverManagerDataSource(String url, String username, String password) {
        return new DriverManagerBackedDataSource(url, username, password);
    }

    /**
     * Provides the database-specific datasource used by this leaf module.
     *
     * @return datasource
     */
    protected abstract DataSource dataSource();

    /**
     * Proves bootstrap script diagnostics identify the script role, configured
     * source keys, and supported resource keys when more than one source is
     * configured.
     */
    @Test
    void rejectsBootstrapScriptWithMultipleResourceSourceKeys() {
        DataException dropFailure = assertThrows(DataException.class,
                                                 () -> factoryFromConfig(Map.of(
                                                         "data.persistence-units.jdbc.0.drop-script.path",
                                                         "drop.sql",
                                                         "data.persistence-units.jdbc.0.drop-script.resource-path",
                                                         "db/drop.sql"))
                                                         .services());
        DataException initFailure = assertThrows(DataException.class,
                                                 () -> factoryFromConfig(Map.of(
                                                         "data.persistence-units.jdbc.0.init-script.path",
                                                         "init.sql",
                                                         "data.persistence-units.jdbc.0.init-script.content-plain",
                                                         "SELECT 1"))
                                                         .services());

        assertFailureContains(dropFailure,
                              "JDBC persistence unit configuration has invalid value for 'drop-script'.",
                              "Configure exactly one resource source key for 'drop-script'.",
                              "Configured keys are 'path' and 'resource-path'.",
                              "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.");
        assertFailureContains(initFailure,
                              "JDBC persistence unit configuration has invalid value for 'init-script'.",
                              "Configure exactly one resource source key for 'init-script'.",
                              "Configured keys are 'path' and 'content-plain'.",
                              "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.");
    }

    /**
     * Proves bootstrap script diagnostics list supported resource keys when a
     * script configuration does not contain any supported source key.
     */
    @Test
    void rejectsBootstrapScriptWithoutSupportedResourceSourceKey() {
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.init-script.unsupported-key",
                                                     "init.sql"))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration has invalid value for 'init-script'.",
                              "Configure 'init-script' as a resource object.",
                              "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.");
    }

    /**
     * Proves missing filesystem bootstrap resources are reported as existing
     * file requirements for the exact script resource key.
     *
     * @param directory temporary directory
     */
    @Test
    void reportsMissingFilesystemBootstrapScriptPath(@TempDir Path directory) {
        Path missing = directory.resolve("missing-drop.sql");
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.drop-script.path",
                                                     missing.toString()))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration could not load the file drop script",
                              "configured by 'drop-script.path'.",
                              "Ensure the filesystem path points to an existing file readable by "
                                      + "the application process.");
        SensitiveFailureAssertions.assertNoSecrets(failure, missing.toString());
    }

    /**
     * Proves missing classpath bootstrap resources are reported as classpath
     * requirements for the exact script resource key.
     */
    @Test
    void reportsMissingClasspathBootstrapScriptResource() {
        String missing = "db/missing-init-script.sql";
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.init-script.resource-path",
                                                     missing))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration could not load the classpath init script",
                              "configured by 'init-script.resource-path'.",
                              "Ensure the classpath resource exists in the application runtime classpath.");
        SensitiveFailureAssertions.assertNoSecrets(failure, missing);
    }

    /**
     * Proves URI bootstrap resources are rejected with the script role instead
     * of flowing into the common resource factory.
     */
    @Test
    void rejectsUriBootstrapScriptResource() {
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.drop-script.uri",
                                                     "file:///private/drop.sql"))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration does not support a URI value for 'drop-script'.");
    }

    private JdbcPersistenceUnitFactory factoryFromConfig(Map<String, String> configValues) {
        Map<String, String> values = new HashMap<>(configValues);
        values.put("data.persistence-units.jdbc.0.data-source", SOURCE_NAME);
        Config config = Config.just(ConfigSources.create(values));
        return new JdbcPersistenceUnitFactory(
                () -> List.of(instance(SOURCE_NAME, dataSource())),
                () -> config,
                new JdbcTransactionConnectionManager());
    }

    private static ServiceInstance<DataSource> instance(String name, DataSource dataSource) {
        return new TestServiceInstance(dataSource, java.util.Set.of(Qualifier.createNamed(name)));
    }

    private static void assertFailureContains(Throwable failure, String... expectedMessages) {
        for (String expectedMessage : expectedMessages) {
            assertThat(failure.getMessage(), containsString(expectedMessage));
        }
    }

    private record TestServiceInstance(DataSource value,
                                       java.util.Set<Qualifier> qualifiers) implements ServiceInstance<DataSource> {
        @Override
        public DataSource get() {
            return value;
        }

        @Override
        public java.util.Set<ResolvedType> contracts() {
            return java.util.Set.of(ResolvedType.create(TypeName.create(DataSource.class)));
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
            return TypeName.create(AbstractJdbcBootstrapDiagnosticTest.class);
        }
    }

    private record DriverManagerBackedDataSource(String url,
                                                 String username,
                                                 String password) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            if (username == null) {
                return DriverManager.getConnection(url);
            }
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Datasource is not a wrapper for " + iface.getName() + ".");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
