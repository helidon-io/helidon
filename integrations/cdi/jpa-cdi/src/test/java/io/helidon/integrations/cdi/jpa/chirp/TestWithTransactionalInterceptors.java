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
package io.helidon.integrations.cdi.jpa.chirp;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.spi.Context;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceException;
import javax.persistence.SynchronizationType;
import javax.persistence.TransactionRequiredException;
import javax.sql.DataSource;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transactional;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestWithTransactionalInterceptors;"
        + "INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestWithTransactionalInterceptors {

    private SeContainer cdiContainer;

    private TestWithTransactionalInterceptors self;

    @Inject
    private TransactionManager tm;

    @Inject
    @Named("chirp")
    private DataSource dataSource;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp"
    )
    private EntityManager em;


    /*
     * Constructors.
     */


    TestWithTransactionalInterceptors() {
        super();
    }


    /*
     * Setup and teardown methods.
     */


    @BeforeEach
    void startCdiContainer() throws SQLException, SystemException {
        assertNull(this.dataSource);
        assertNull(this.em);
        assertNull(this.tm);
        assertNull(this.self);

        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
        assertNotNull(initializer);
        this.cdiContainer = initializer.initialize();
        assertNotNull(this.cdiContainer);

        this.self = this.cdiContainer.select(this.getClass()).get();
        assertNotNull(this.self);

        this.em = this.self.getEntityManager();
        assertNotNull(this.em);

        this.tm = this.self.getTransactionManager();
        assertNotNull(this.tm);

        this.dataSource = this.self.getDataSource();
        assertNotNull(this.dataSource);

        assertAuthorTableIsEmpty();
        assertNoTransaction();
    }

    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
    }


    /*
     * Support methods.
     */


    public EntityManager getEntityManager() {
        return this.em;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    public TransactionManager getTransactionManager() {
        return this.tm;
    }

    private void onShutdown(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event,
                            final TransactionManager tm) throws SystemException {
        // If an assertion fails, or some other error happens in the
        // CDI container, there may be a current transaction that has
        // neither been committed nor rolled back.  Because the
        // Narayana transaction engine is fundamentally static, this
        // means that a transaction affiliation with the main thread
        // may "leak" into another JUnit test (since JUnit, by
        // default, forks once, and then runs all tests in the same
        // JVM).  CDI, thankfully, will fire an event for the
        // application context shutting down, even in the case of
        // errors.
        if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
            tm.rollback();
        }
    }


    /*
     * Test methods.
     */


    @Test
    void runTestInsertAndVerifyResults() throws Exception {

        // Test a simple insert.  Note testInsert() is annotated
        // with @Transactional so we invoke it through our "self"
        // proxy.
        self.testInsert();

        // The transaction should have committed so is no longer
        // active.
        assertNoTransaction();

        // Make sure the operation worked.
        try (final Connection connection = this.dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID FROM AUTHOR");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertFalse(resultSet.next());
        }

    }

    @Test
    void runTestFindAndUpdateAndVerifyResults() throws Exception {
        // First (re-)run our runTestInsertAndVerifyResults() method,
        // which will put an author with ID 1 in the database and
        // verify that that worked.
        this.runTestInsertAndVerifyResults();

        // Find him and change his name.  Note testFindAndUpdate() is
        // annotated with @Transactional so we invoke it through our
        // "self" proxy.
        self.testFindAndUpdate();

        // The transaction should have committed so is no longer
        // active.
        assertNoTransaction();
        
        // Make sure the operation worked.
        try (final Connection connection = this.dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertEquals("Abe Lincoln", resultSet.getString(2));
            assertFalse(resultSet.next());
        }
    }


    /*
     * Transactional methods under test.
     */


    @Transactional
    public void testInsert() throws Exception {
        assertActiveTransaction();

        // Make sure there's nothing in there.
        assertAuthorTableIsEmpty();

        // Persist an Author.
        final Author author = new Author("Abraham Lincoln");
        em.persist(author);
        assertTrue(em.contains(author));
    }

    @Transactional
    public void testFindAndUpdate() throws Exception {
        assertActiveTransaction();
        final Author author = this.em.find(Author.class, Integer.valueOf(1));
        assertNotNull(author);
        assertEquals(Integer.valueOf(1), author.getId());
        assertEquals("Abraham Lincoln", author.getName());
        assertTrue(this.em.contains(author));
        author.setName("Abe Lincoln");
    }


    /*
     * Assertion-style methods.
     */


    private void assertAuthorTableIsEmpty() throws SQLException {
        assertNotNull(this.dataSource);
        try (final Connection connection = this.dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM AUTHOR");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
            assertFalse(resultSet.next());
        }
    }

    private void assertActiveTransaction() throws SystemException {
        assertNotNull(this.tm);
        assertEquals(Status.STATUS_ACTIVE, this.tm.getStatus());
        assertNotNull(this.em);
        assertTrue(this.em.isJoinedToTransaction());
    }

    private void assertNoTransaction() throws SystemException {
        assertNotNull(this.tm);
        assertNotNull(this.em);
        assertEquals(Status.STATUS_NO_TRANSACTION, this.tm.getStatus());
        assertFalse(this.em.isJoinedToTransaction());
    }

}
