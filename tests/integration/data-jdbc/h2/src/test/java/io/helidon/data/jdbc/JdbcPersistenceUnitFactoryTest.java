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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcPersistenceUnitFactoryTest {

    /**
     * Proves multiple resolved datasources bootstrap and publish distinct clients
     * carrying both name and JDBC-provider qualifiers.
     */
    @Test
    void createsDistinctNamedAndProviderQualifiedClients() throws Exception {
        JdbcDataSource contacts = dataSource("contacts", "CONTACTS");
        JdbcDataSource audit = dataSource("audit", "AUDIT");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "contacts",
                "data.persistence-units.jdbc.0.data-source", "contacts-source",
                "data.persistence-units.jdbc.0.init-script.content-plain",
                "UPDATE UNIT_NAME SET NAME = 'READY_CONTACTS';",
                "data.persistence-units.jdbc.1.name", "audit",
                "data.persistence-units.jdbc.1.data-source", "audit-source",
                "data.persistence-units.jdbc.1.init-script.content-plain",
                "UPDATE UNIT_NAME SET NAME = 'READY_AUDIT';")));
        List<ServiceInstance<DataSource>> sources = List.of(instance("contacts-source", contacts),
                                                            instance("audit-source", audit));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(() -> sources,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        List<Service.QualifiedInstance<JdbcClient>> clients = factory.services();

        assertThat(clients.size(), is(2));
        JdbcClient contactsClient = namedProviderClient(clients, "contacts");
        JdbcClient auditClient = namedProviderClient(clients, "audit");
        assertThat(contactsClient.create("SELECT NAME FROM UNIT_NAME").map(String.class).one(), is("READY_CONTACTS"));
        assertThat(auditClient.create("SELECT NAME FROM UNIT_NAME").map(String.class).one(), is("READY_AUDIT"));
    }

    /**
     * Proves a missing later named datasource prevents an earlier unit's
     * initialization script from changing durable database state.
     */
    @Test
    void resolvesEveryNamedDataSourceBeforeExecutingEarlierBootstrap() throws Exception {
        JdbcDataSource first = dataSource("missing_later_datasource", "UNCHANGED");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "first",
                "data.persistence-units.jdbc.0.data-source", "first-source",
                "data.persistence-units.jdbc.0.init-script.content-plain",
                "UPDATE UNIT_NAME SET NAME = 'CHANGED';",
                "data.persistence-units.jdbc.1.name", "missing",
                "data.persistence-units.jdbc.1.data-source", "missing-source")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("first-source", first)),
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("No SQL datasource service is named 'missing-source'."));
        assertThat(singleUnitName(first), is("UNCHANGED"));
    }

    /**
     * Proves a later direct-driver resolution failure prevents an earlier
     * unit's drop script from changing durable database state.
     */
    @Test
    void resolvesEveryDirectDriverBeforeExecutingEarlierBootstrap() throws Exception {
        JdbcDataSource first = dataSource("missing_later_driver", "UNCHANGED");
        String unavailableUrl = "jdbc:missing:bootstrap-resolution";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "first",
                "data.persistence-units.jdbc.0.data-source", "first-source",
                "data.persistence-units.jdbc.0.drop-script.content-plain", "DELETE FROM UNIT_NAME;",
                "data.persistence-units.jdbc.1.name", "missing-driver",
                "data.persistence-units.jdbc.1.connection.url", unavailableUrl)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("first-source", first)),
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit 'missing-driver' could not resolve a JDBC driver for its direct "
                              + "connection."));
        SensitiveFailureAssertions.assertNoSecrets(failure, unavailableUrl);
        assertThat(singleUnitName(first), is("UNCHANGED"));
    }

    /**
     * Proves direct driver configuration creates a usable client and executes its classpath initialization script.
     */
    @Test
    void createsAClientFromExistingDirectConnectionConfiguration() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url",
                "jdbc:h2:mem:direct_unit;DB_CLOSE_DELAY=-1",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name",
                "org.h2.Driver",
                "data.persistence-units.jdbc.0.init-script.resource-path",
                "db/h2/jdbc-bootstrap-init.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM SCRIPT_VALUE ORDER BY ID").map(String.class).list(),
                   is(List.of("first;value", "second 'quoted;value'")));
    }

    /**
     * Proves an H2 authentication failure discloses neither database URL components nor configured credentials.
     */
    @Test
    void sanitizesDirectConnectionAuthenticationFailure() throws Exception {
        String databaseCanary = "private-database-canary";
        String usernameCanary = "private-username-canary";
        String passwordCanary = "private-password-canary";
        String url = "jdbc:h2:mem:" + databaseCanary + ";DB_CLOSE_DELAY=-1";
        JdbcDataSource owner = new JdbcDataSource();
        owner.setURL(url);
        owner.setUser("owner");
        owner.setPassword("correct-password");
        try (var ignored = owner.getConnection()) {
            // Establish the authenticated in-memory database before attempting invalid credentials.
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", url,
                "data.persistence-units.jdbc.0.connection.username", usernameCanary,
                "data.persistence-units.jdbc.0.connection.password", passwordCanary,
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "org.h2.Driver")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());
        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("SELECT 1").map(Integer.class).one());

        SensitiveFailureAssertions.assertNoSecrets(failure, databaseCanary, usernameCanary, passwordCanary);
    }

    /**
     * Proves filesystem drop and init resources are loaded, closed, and executed in drop-before-init order.
     */
    @Test
    void executesFilesystemBootstrapResource(@TempDir Path directory) throws Exception {
        Path dropScript = directory.resolve("filesystem-bootstrap-drop.sql");
        Path initScript = directory.resolve("filesystem-bootstrap-init.sql");
        Files.writeString(dropScript, "DROP TABLE FILESYSTEM_RESOURCE;");
        Files.writeString(initScript, "CREATE TABLE FILESYSTEM_RESOURCE (DATA_VALUE VARCHAR(20));"
                + "INSERT INTO FILESYSTEM_RESOURCE VALUES ('filesystem');");
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:filesystem_resource;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FILESYSTEM_RESOURCE (DATA_VALUE VARCHAR(20))");
            statement.execute("INSERT INTO FILESYSTEM_RESOURCE VALUES ('obsolete')");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.drop-script.path", dropScript.toString(),
                "data.persistence-units.jdbc.0.init-script.path", initScript.toString())));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM FILESYSTEM_RESOURCE").map(String.class).one(),
                   is("filesystem"));
    }

    /**
     * Proves configured plain-text drop and init resources execute verbatim in drop-before-init order.
     */
    @Test
    void executesConfiguredTextBootstrapResource() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:configured_text_resource;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE TEXT_RESOURCE (DATA_VALUE VARCHAR(20))");
            statement.execute("INSERT INTO TEXT_RESOURCE VALUES ('obsolete')");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.drop-script.content-plain", "DROP TABLE TEXT_RESOURCE;",
                "data.persistence-units.jdbc.0.init-script.content-plain",
                "CREATE TABLE TEXT_RESOURCE (DATA_VALUE VARCHAR(20));"
                        + "INSERT INTO TEXT_RESOURCE VALUES ('configured');")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM TEXT_RESOURCE").map(String.class).one(), is("configured"));
    }

    /**
     * Proves Base64 resources decode as UTF-8 and execute both bootstrap roles in drop-before-init order.
     */
    @Test
    void executesConfiguredBinaryBootstrapResource() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:configured_binary_resource;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE BINARY_RESOURCE (DATA_VALUE VARCHAR(20))");
            statement.execute("INSERT INTO BINARY_RESOURCE VALUES ('obsolete')");
        }
        String dropScript = "DROP TABLE BINARY_RESOURCE;";
        String script = "CREATE TABLE BINARY_RESOURCE (DATA_VALUE VARCHAR(20));"
                + "INSERT INTO BINARY_RESOURCE VALUES ('configured');";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.drop-script.content",
                Base64.getEncoder().encodeToString(dropScript.getBytes(StandardCharsets.UTF_8)),
                "data.persistence-units.jdbc.0.init-script.content",
                Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8)))));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM BINARY_RESOURCE").map(String.class).one(), is("configured"));
    }

    /**
     * Proves filesystem, plain-text, and Base64 resources each execute when
     * configured as the sole initialization source.
     */
    @Test
    void executesInitOnlyForEveryConfigurationResourceForm(@TempDir Path directory) throws Exception {
        String script = "CREATE TABLE INIT_ONLY_RESOURCE (DATA_VALUE VARCHAR(20));"
                + "INSERT INTO INIT_ONLY_RESOURCE VALUES ('initialized');";
        Path file = directory.resolve("init-only.sql");
        Files.writeString(file, script);

        assertInitOnlyExecutes("init_only_file", "init-script.path", file.toString());
        assertInitOnlyExecutes("init_only_text", "init-script.content-plain", script);
        assertInitOnlyExecutes("init_only_binary",
                               "init-script.content",
                               Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Proves filesystem, plain-text, and Base64 resources each execute when
     * configured as the sole drop source.
     */
    @Test
    void executesDropOnlyForEveryConfigurationResourceForm(@TempDir Path directory) throws Exception {
        String script = "DROP TABLE DROP_ONLY_RESOURCE;";
        Path file = directory.resolve("drop-only.sql");
        Files.writeString(file, script);

        assertDropOnlyExecutes("drop_only_file", "drop-script.path", file.toString());
        assertDropOnlyExecutes("drop_only_text", "drop-script.content-plain", script);
        assertDropOnlyExecutes("drop_only_binary",
                               "drop-script.content",
                               Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Proves invalid Base64 fails during configuration construction without disclosing the configured content.
     */
    @Test
    void rejectsInvalidConfiguredBinaryWithoutDisclosingIt() {
        String secret = "private-invalid-base64-canary";
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:invalid_configured_binary_resource;DB_CLOSE_DELAY=-1");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.init-script.content", secret)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        SensitiveFailureAssertions.assertNoSecrets(failure, secret);
    }

    /**
     * Proves the script splitter handles a BOM, UTF-8 data, comments, protected semicolons, and encounter order.
     */
    @Test
    void executesUtf8BootstrapWithBomCommentsAndProtectedSemicolonsInOrder() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:configured_utf8_resource;DB_CLOSE_DELAY=-1");
        String script = "\uFEFF-- leading comment with ;\n"
                + "CREATE TABLE UTF8_RESOURCE (ID INTEGER PRIMARY KEY, DATA_VALUE VARCHAR(40));\n"
                + "/* protected comment ; */ INSERT INTO UTF8_RESOURCE VALUES (1, 'café;first');\n"
                + "INSERT INTO UTF8_RESOURCE VALUES (2, 'second ''quoted;value'''); -- trailing ;\n";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.init-script.content-plain", script)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM UTF8_RESOURCE ORDER BY ID").map(String.class).list(),
                   is(List.of("café;first", "second 'quoted;value'")));
    }

    /**
     * Proves an explicitly empty bootstrap resource is a valid no-op and still yields a usable client.
     */
    @Test
    void acceptsEmptyBootstrapResource() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:empty_resource");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.init-script.content-plain", "")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT 1").map(Integer.class).one(), is(1));
    }

    /**
     * Proves a script containing only ordinary comments is treated as a valid
     * no-op while the resulting client remains usable.
     */
    @Test
    void acceptsCommentOnlyBootstrapResource() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:comment_only_resource");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0.init-script.content-plain",
                "-- line comment containing ;\n/* block comment containing ; */")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT 1").map(Integer.class).one(), is(1));
    }

    /**
     * Proves a named datasource executes the drop resource before init by replacing deliberately incompatible state.
     */
    @Test
    void executesDropBeforeInitForNamedDataSource() throws Exception {
        JdbcDataSource source = dataSource("bootstrap_order", "obsolete");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SCRIPT_VALUE (ID INTEGER PRIMARY KEY, DATA_VALUE VARCHAR(80))");
            statement.execute("INSERT INTO SCRIPT_VALUE VALUES (99, 'obsolete')");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "bootstrap",
                "data.persistence-units.jdbc.0.data-source", "bootstrap-source",
                "data.persistence-units.jdbc.0.drop-script.resource-path", "db/h2/jdbc-bootstrap-drop.sql",
                "data.persistence-units.jdbc.0.init-script.resource-path", "db/h2/jdbc-bootstrap-init.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("bootstrap-source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), "bootstrap");

        assertThat(client.create("SELECT DATA_VALUE FROM SCRIPT_VALUE ORDER BY ID").map(String.class).list(),
                   is(List.of("first;value", "second 'quoted;value'")));
    }

    /**
     * Proves a configured drop-only resource executes even when no initialization resource is present.
     */
    @Test
    void executesDropScriptWithoutAnInitScript() throws Exception {
        JdbcDataSource source = dataSource("bootstrap_drop_only", "obsolete");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SCRIPT_VALUE (ID INTEGER PRIMARY KEY)");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "drop-only",
                "data.persistence-units.jdbc.0.data-source", "drop-only-source",
                "data.persistence-units.jdbc.0.drop-script.resource-path", "db/h2/jdbc-bootstrap-drop.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("drop-only-source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), "drop-only");

        assertThrows(DataException.class,
                     () -> client.create("SELECT COUNT(*) FROM SCRIPT_VALUE").map(Long.class).one());
    }

    /**
     * Locates the client carrying both the persistence-unit name and the JDBC
     * provider qualifier.
     *
     * @param clients qualified clients
     * @param name persistence-unit name
     * @return matching client
     */
    private static JdbcClient namedProviderClient(List<Service.QualifiedInstance<JdbcClient>> clients, String name) {
        Qualifier named = Qualifier.createNamed(name);
        return clients.stream()
                .filter(client -> client.qualifiers().contains(named))
                .filter(client -> client.qualifiers().size() == 2)
                .findFirst()
                .orElseThrow()
                .get();
    }

    /**
     * Executes one init-only resource and verifies its durable database state.
     *
     * @param database isolated database name
     * @param resourceKey resource configuration suffix
     * @param resourceValue encoded resource value
     */
    private static void assertInitOnlyExecutes(String database, String resourceKey, String resourceValue) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0." + resourceKey, resourceValue)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM INIT_ONLY_RESOURCE").map(String.class).one(),
                   is("initialized"));
    }

    /**
     * Executes one drop-only resource and verifies the former table no longer
     * exists through the provider's public failure boundary.
     *
     * @param database isolated database name
     * @param resourceKey resource configuration suffix
     * @param resourceValue encoded resource value
     * @throws Exception when database setup fails
     */
    private static void assertDropOnlyExecutes(String database,
                                               String resourceKey,
                                               String resourceValue) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE DROP_ONLY_RESOURCE (DATA_VALUE VARCHAR(20))");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.0." + resourceKey, resourceValue)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThrows(DataException.class,
                     () -> client.create("SELECT COUNT(*) FROM DROP_ONLY_RESOURCE").map(Long.class).one());
    }

    /**
     * Reads the sole identifying value from a factory-test datasource without
     * relying on a client which failed factory publication.
     *
     * @param dataSource initialized datasource
     * @return sole identifying value
     * @throws Exception when the database cannot be inspected
     */
    private static String singleUnitName(JdbcDataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT NAME FROM UNIT_NAME")) {
            assertThat(resultSet.next(), is(true));
            String name = resultSet.getString(1);
            assertThat(resultSet.next(), is(false));
            return name;
        }
    }

    /**
     * Creates a datasource with one identifying row.
     *
     * @param database in-memory database name
     * @param value identifying value
     * @return initialized datasource
     * @throws Exception when setup fails
     */
    private static JdbcDataSource dataSource(String database, String value) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE UNIT_NAME (NAME VARCHAR(20))");
            statement.execute("INSERT INTO UNIT_NAME VALUES ('" + value + "')");
        }
        return dataSource;
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
