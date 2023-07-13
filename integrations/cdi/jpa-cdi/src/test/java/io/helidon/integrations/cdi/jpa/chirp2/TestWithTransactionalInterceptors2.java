/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa.chirp2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import jakarta.persistence.SynchronizationType;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp2",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestWithTransactionalInterceptors2;"
        + "MODE=LEGACY;"
        + "INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp2.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestWithTransactionalInterceptors2 {

    private SeContainer cdiContainer;

    private TestWithTransactionalInterceptors2 self;

    @Inject
    private TransactionManager tm;

    @Inject
    @Named("chirp2")
    private DataSource dataSource;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp2"
    )
    private EntityManager em;


    /*
     * Constructors.
     */


    TestWithTransactionalInterceptors2() {
        super();
    }


    /*
     * Setup and teardown methods.
     */


    @BeforeEach
    void startCdiContainer() throws SQLException, SystemException {
        System.setProperty(io.helidon.integrations.cdi.jpa.JpaExtension.class.getName() + ".enabled", "false");
        System.setProperty(io.helidon.integrations.cdi.jpa.PersistenceExtension.class.getName() + ".enabled", "true");
        assertThat(this.dataSource, nullValue());
        assertThat(this.em, nullValue());
        assertThat(this.tm, nullValue());
        assertThat(this.self, nullValue());

        SeContainerInitializer initializer = SeContainerInitializer.newInstance().addBeanClasses(this.getClass());
        assertThat(initializer, notNullValue());
        this.cdiContainer = initializer.initialize();
        assertThat(this.cdiContainer, notNullValue());

        this.self = this.cdiContainer.select(this.getClass()).get();
        assertThat(this.self, notNullValue());

        this.em = this.self.getEntityManager();
        assertThat(this.em, notNullValue());

        this.tm = this.self.getTransactionManager();
        assertThat(this.tm, notNullValue());

        this.dataSource = this.self.getDataSource();
        assertThat(this.dataSource, notNullValue());

        assertAuthorTableIsEmpty();
        assertNoTransaction();
    }

    @AfterEach
    void shutDownCdiContainer() throws Exception {
        try {
            this.em.clear();
            this.em.getEntityManagerFactory().getCache().evictAll();
        } finally {
            if (this.cdiContainer != null) {
                this.cdiContainer.close();
            }
        }
        System.setProperty(io.helidon.integrations.cdi.jpa.PersistenceExtension.class.getName() + ".enabled", "false");
        System.setProperty(io.helidon.integrations.cdi.jpa.JpaExtension.class.getName() + ".enabled", "true");
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

    private void onShutdown(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event,
                            TransactionManager tm) throws SystemException {
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
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT ID FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            // assertThat(resultSet.getInt(1), is(1));
            assertThat(resultSet.next(), is(false));
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
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            // assertThat(resultSet.getInt(1), is(1));
            assertThat(resultSet.getString(2), is("Abe Lincoln"));
            assertThat(resultSet.next(), is(false));
        } finally {
            self.removeAbe();
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
        Author author = new Author(1, "Abraham Lincoln");
        em.persist(author);
        assertThat(em.contains(author), is(true));
    }

    @Transactional
    public void testFindAndUpdate() throws Exception {
        assertActiveTransaction();
        // Author author = this.em.find(Author.class, Integer.valueOf(1));
        Author author = (Author) this.em.createQuery("SELECT a FROM Author a WHERE a.name = 'Abraham Lincoln'").getResultList().get(0);
        assertThat(author, notNullValue());
        // assertThat(author.getId(), is(1));
        assertThat(author.getName(), is("Abraham Lincoln"));
        assertThat(this.em.contains(author), is(true));
        author.setName("Abe Lincoln");
    }

    @Transactional
    public void removeAbe() throws Exception {
        assertActiveTransaction();
        this.em.remove(this.em.getReference(Author.class, 1));
    }


    /*
     * Assertion-style methods.
     */


    private void assertAuthorTableIsEmpty() throws SQLException {
        assertThat(this.dataSource, notNullValue());
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(0));
            assertThat(resultSet.next(), is(false));
        }
    }

    private void deleteAllFromAuthorTableAfterTest() throws SQLException, SystemException {
        assertThat(this.dataSource, notNullValue());
        // assertNoTransaction();
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM AUTHOR");
        }
    }

    private void resetAuthorIdentityColumn() throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE AUTHOR ALTER COLUMN ID RESTART WITH 1");
        }
    }

    private void assertActiveTransaction() throws SystemException {
        assertThat(this.tm, notNullValue());
        assertThat(this.tm.getStatus(), is(Status.STATUS_ACTIVE));
        assertThat(this.em, notNullValue());
        assertThat(this.em.isJoinedToTransaction(), is(true));
    }

    private void assertNoTransaction() throws SystemException {
        assertThat(this.tm, notNullValue());
        assertThat(this.tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(this.em, notNullValue());
        assertThat(this.em.isJoinedToTransaction(), is(false));
    }

}
