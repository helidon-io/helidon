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

package io.helidon.data.jakarta.persistence;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.transaction.spi.TxSupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;

import static io.helidon.data.jakarta.persistence.PersistenceUnitFactory.TRANSACTION_TYPE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistenceLoggingTest {
    private static final String PASSWORD = "connection-secret";
    private static final String OTHER_SECRET = "provider-secret";
    private static final String JDBC_URL = "jdbc:logging-test:database";
    private static final String CUSTOM_VALUE = "diagnostic-value";

    @Test
    void configurationRedactsSensitiveKeys() throws SQLException {
        Driver driver = new TestDriver();
        TxSupport txSupport = mock(TxSupport.class);
        when(txSupport.type()).thenReturn("resource-local");
        Logger logger = Logger.getLogger(PersistenceConfiguration.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();

        try (InMemoryLoggingHandler logs = InMemoryLoggingHandler.create(logger)) {
            DriverManager.registerDriver(driver);
            logger.setLevel(Level.FINE);
            logger.setUseParentHandlers(false);
            ConnectionConfig connection = ConnectionConfig.builder()
                    .url(JDBC_URL)
                    .username("test-user")
                    .password(PASSWORD.toCharArray())
                    .jdbcDriverClassName(driver.getClass().getName())
                    .build();
            JpaPersistenceUnitConfig config = JpaPersistenceUnitConfig.builder()
                    .connection(connection)
                    .putProperty("custom.Secret", OTHER_SECRET)
                    .putProperty("custom.option", CUSTOM_VALUE)
                    .build();

            PersistenceConfiguration configuration = PersistenceConfiguration.create("logging-test",
                                                                                     txSupport,
                                                                                     Set.of(),
                                                                                     List::of,
                                                                                     config);

            assertThat(configuration.getProperties().getProperty("jakarta.persistence.jdbc.password"), is(PASSWORD));
            assertThat(configuration.getProperties().getProperty("jakarta.persistence.jdbc.url"), is(JDBC_URL));
            assertThat(configuration.getProperties().getProperty("custom.Secret"), is(OTHER_SECRET));
            assertThat(configuration.getProperties().getProperty("custom.option"), is(CUSTOM_VALUE));
            assertSensitiveKeysRedacted(logs, " = ");
        } finally {
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    void repositoryCallRedactsSensitiveKeys() {
        Map<String, Object> properties = Map.of(TRANSACTION_TYPE, PersistenceUnitTransactionType.RESOURCE_LOCAL,
                                                "jakarta.persistence.jdbc.password", PASSWORD,
                                                "jakarta.persistence.jdbc.url", JDBC_URL,
                                                "custom.Secret", OTHER_SECRET,
                                                "custom.option", CUSTOM_VALUE);
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        EntityManager entityManager = mock(EntityManager.class);
        EntityTransaction transaction = mock(EntityTransaction.class);
        when(factory.getProperties()).thenReturn(properties);
        when(factory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.getProperties()).thenReturn(properties);
        when(entityManager.getTransaction()).thenReturn(transaction);
        TxSupport txSupport = new LocalTxSupport(
                new LocalTransactionStorage.LocalTransactionProvider(new LocalTransactionStorage.LocalTransactionManager()),
                List.of(new TransactionContext.JtaLifeCycle()));
        JpaRepositoryExecutorImpl executor = new JpaRepositoryExecutorImpl(factory, Optional.of(txSupport));
        Logger logger = Logger.getLogger(JpaRepositoryExecutorImpl.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();

        try (InMemoryLoggingHandler logs = InMemoryLoggingHandler.create(logger)) {
            logger.setLevel(Level.FINE);
            logger.setUseParentHandlers(false);

            assertThat(executor.call(EntityManager::getProperties), is(properties));

            verify(transaction).begin();
            verify(transaction).commit();
            verify(entityManager).close();
            assertSensitiveKeysRedacted(logs, " :: ");
        } finally {
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }

    private static void assertSensitiveKeysRedacted(InMemoryLoggingHandler logs, String separator) {
        SimpleFormatter formatter = new SimpleFormatter();
        List<String> messages = logs.logRecords().stream().map(formatter::formatMessage).toList();
        assertThat(messages, hasItem(containsString("jakarta.persistence.jdbc.password" + separator + "<redacted>")));
        assertThat(messages, hasItem(containsString("custom.Secret" + separator + "<redacted>")));
        assertThat(messages, hasItem(containsString("jakarta.persistence.jdbc.url" + separator + JDBC_URL)));
        assertThat(messages, hasItem(containsString("custom.option" + separator + CUSTOM_VALUE)));
        assertThat(messages, not(hasItem(containsString(PASSWORD))));
        assertThat(messages, not(hasItem(containsString(OTHER_SECRET))));
    }

    private static final class TestDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            throw new SQLException("No database connection expected");
        }

        @Override
        public boolean acceptsURL(String url) {
            return JDBC_URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(TestDriver.class.getName());
        }
    }
}
