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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcPersistenceUnitFactoryTest {

    /**
     * Proves absent configuration and duplicate named units fail before any
     * provider service can be activated.
     */
    @Test
    void rejectsMissingAndDuplicatePersistenceUnitConfiguration() {
        Config missing = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "missing",
                "data.persistence-units.jdbc.0.data-source", "does-not-exist")));
        JdbcPersistenceUnitFactory missingFactory = new JdbcPersistenceUnitFactory(List::of,
                                                                                   () -> missing,
                                                                                   new JdbcTransactionConnectionManager());
        assertThrows(DataException.class, missingFactory::services);

        Config duplicate = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "same",
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.name", "same",
                "data.persistence-units.jdbc.1.data-source", "source")));
        JdbcPersistenceUnitFactory duplicateFactory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", new FailingDataSource())),
                () -> duplicate,
                new JdbcTransactionConnectionManager());
        assertThrows(DataException.class, duplicateFactory::services);
    }

    /**
     * Proves duplicate unnamed units fail without disclosing the internal
     * default qualifier.
     */
    @Test
    void rejectsDuplicateUnnamedPersistenceUnitsWithoutExposingTheDefaultQualifier() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.data-source", "source")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("Each JDBC persistence unit must have a unique name. "
                                                   + "More than one configured persistence unit is unnamed."));
    }

    /**
     * Proves the complete unit list is validated before the first datasource
     * is activated.
     */
    @Test
    void validatesEveryUnitBeforeActivatingTheFirstDatasource() {
        Config duplicate = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "same",
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.name", "same",
                "data.persistence-units.jdbc.1.data-source", "source")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("An earlier unit activated its datasource before validation completed");
                },
                () -> duplicate,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("More than one JDBC persistence unit is named 'same'."));
    }

    /**
     * Proves a later invalid connection-source definition fails before an
     * earlier datasource is activated.
     */
    @Test
    void validatesALaterConnectionSourceBeforeActivatingTheFirstDatasource() {
        Config invalidLaterUnit = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "first",
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.name", "invalid")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("An earlier unit activated its datasource before validation completed");
                },
                () -> invalidLaterUnit,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("invalid"));
        assertThat(failure.getMessage(), containsString("exactly one connection source"));
    }

    /**
     * Proves bootstrap input is fully detached and closed before datasource
     * resolution begins.
     */
    @Test
    void closesBootstrapResourceBeforeDatasourceResolution() {
        String resourceName = "close-before-datasource.sql";
        AtomicBoolean closed = new AtomicBoolean();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = new ClassLoader(previous) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (resourceName.equals(name)) {
                    return new ByteArrayInputStream("SELECT 1".getBytes(StandardCharsets.UTF_8)) {
                        @Override
                        public void close() {
                            closed.set(true);
                        }
                    };
                }
                return super.getResourceAsStream(name);
            }
        };
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "closed-resource",
                "data.persistence-units.jdbc.0.data-source", "missing-source",
                "data.persistence-units.jdbc.0.init-script.resource-path", resourceName)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        try {
            Thread.currentThread().setContextClassLoader(loader);

            assertThrows(DataException.class, factory::services);

            assertThat(closed.get(), is(true));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /**
     * Proves a unit with no connection source fails before dependency or
     * bootstrap-resource resolution.
     */
    @Test
    void rejectsMissingConnectionSourceBeforeResolvingDependenciesOrScripts() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "missing-source",
                "data.persistence-units.jdbc.0.init-script.resource-path", "does-not-exist.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Connection-source validation activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("missing-source"));
        assertThat(failure.getMessage(), is("JDBC persistence unit 'missing-source' must specify exactly one "
                                                   + "connection source. Configure either 'data-source' or "
                                                   + "'connection'."));
    }

    /**
     * Proves ambiguous direct and named connection sources fail before either
     * source is resolved.
     */
    @Test
    void rejectsAmbiguousConnectionSourceBeforeResolvingEitherSource() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "ambiguous-source",
                "data.persistence-units.jdbc.0.data-source", "must-not-resolve",
                "data.persistence-units.jdbc.0.connection.url", "jdbc:must-not-connect",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "missing.Driver",
                "data.persistence-units.jdbc.0.init-script.resource-path", "does-not-exist.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Connection-source validation activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("JDBC persistence unit 'ambiguous-source' must specify exactly one "
                                                   + "connection source. Configure either 'data-source' or "
                                                   + "'connection'."));
    }

    /**
     * Proves an ambiguous unnamed unit does not expose its internal default
     * qualifier in diagnostics.
     */
    @Test
    void rejectsAmbiguousUnnamedConnectionSourceWithoutExposingTheDefaultQualifier() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "must-not-resolve",
                "data.persistence-units.jdbc.0.connection.url", "jdbc:must-not-connect",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "missing.Driver")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Ambiguous configuration activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("JDBC persistence unit configuration must specify exactly one "
                                                   + "connection source. Configure either 'data-source' or "
                                                   + "'connection'."));
    }

    /**
     * Proves a blank datasource name fails before datasource services are
     * activated.
     */
    @Test
    void rejectsBlankDatasourceNameBeforeActivatingDatasourceServices() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "blank-source",
                "data.persistence-units.jdbc.0.data-source", "   ")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Blank datasource name activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("JDBC persistence unit 'blank-source' has a blank 'data-source' name."));
    }

    /**
     * Proves a missing classpath script fails and is sanitized before opening
     * a JDBC connection.
     */
    @Test
    void rejectsMissingScriptBeforeOpeningAConnection() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "missing-script",
                "data.persistence-units.jdbc.0.data-source", "missing-script-source",
                "data.persistence-units.jdbc.0.init-script.resource-path", "does-not-exist.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("missing-script-source", new FailingDataSource())),
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("The configuration for JDBC persistence unit 'missing-script' is invalid."));
        assertThat(failure.getMessage(), not(containsString("does-not-exist.sql")));
    }

    /**
     * Proves missing and directory filesystem resources fail before connection
     * acquisition without disclosing their configured paths.
     */
    @Test
    void rejectsInvalidFilesystemScriptsBeforeOpeningAConnection(@TempDir Path directory) {
        for (Path path : List.of(directory.resolve("private-missing-script.sql"), directory)) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "invalid-filesystem-script",
                    "data.persistence-units.jdbc.0.data-source", "source",
                    "data.persistence-units.jdbc.0.init-script.path", path.toString())));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("source", new FailingDataSource())),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            DataException failure = assertThrows(DataException.class, factory::services);

            assertThat(failure.getMessage(), containsString("invalid-filesystem-script"));
            for (Throwable current = failure; current != null; current = current.getCause()) {
                assertThat(current.toString(), not(containsString(path.toString())));
            }
        }
    }

    /**
     * Proves unsupported URI configuration fails without exposing URI
     * credentials or retaining its parsing failure.
     */
    @Test
    void failedUriResourceDoesNotExposeUriSecrets() {
        String secret = "private-uri-token";
        for (String role : List.of("init-script", "drop-script")) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "private-uri",
                    "data.persistence-units.jdbc.0.data-source", "source",
                    "data.persistence-units.jdbc.0." + role + ".uri", "not a URI " + secret)));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                                () -> config,
                                                                                new JdbcTransactionConnectionManager());

            DataException failure = assertThrows(DataException.class, factory::services);

            assertThat(failure.getMessage(),
                       is("JDBC persistence unit 'private-uri' does not support a URI value for '" + role + "'."));
            assertThat(failure.getMessage(), not(containsString(secret)));
            assertThat(failure.getCause(), nullValue());
        }
    }

    /**
     * Proves every unterminated protected SQL region fails before connection
     * acquisition and without exposing its resource name.
     */
    @Test
    void rejectsEveryUnterminatedBootstrapLexicalRegionBeforeConnecting() {
        for (String resource : List.of("jdbc-bootstrap-unterminated-quote.sql",
                                       "jdbc-bootstrap-unterminated-comment.sql",
                                       "jdbc-bootstrap-unterminated-dollar.sql")) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "malformed",
                    "data.persistence-units.jdbc.0.data-source", "malformed-source",
                    "data.persistence-units.jdbc.0.init-script.resource-path", resource)));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("malformed-source", new FailingDataSource())),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            DataException failure = assertThrows(DataException.class, factory::services);

            assertThat(failure.getMessage(), containsString("unterminated"));
            assertThat(failure.getMessage(), containsString("classpath init script"));
            assertThat(failure.getMessage(), not(containsString(resource)));
        }
    }

    /**
     * Wraps a datasource as a named registry service instance.
     *
     * @param name datasource registry name
     * @param dataSource datasource value
     * @return named service instance
     */
    private static ServiceInstance<DataSource> instance(String name, DataSource dataSource) {
        return new TestServiceInstance(dataSource, Set.of(Qualifier.createNamed(name)));
    }

    // Datasource that proves malformed scripts fail before connection acquisition.
    private static final class FailingDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new AssertionError("Malformed script acquired a connection");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new AssertionError("Malformed script acquired a connection");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
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
            return TypeName.create(JdbcPersistenceUnitFactoryTest.class);
        }
    }
}
