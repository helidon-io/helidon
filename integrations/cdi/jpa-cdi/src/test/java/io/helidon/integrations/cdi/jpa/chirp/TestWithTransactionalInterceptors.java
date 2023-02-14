/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
    name = "chirp",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestWithTransactionalInterceptors;"
        + "MODE=LEGACY;"
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
        assertThat(this.dataSource, nullValue());
        assertThat(this.em, nullValue());
        assertThat(this.tm, nullValue());
        assertThat(this.self, nullValue());

        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
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
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(1));
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
        try (final Connection connection = this.dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(1));
            assertThat(resultSet.getString(2), is("Abe Lincoln"));
            assertThat(resultSet.next(), is(false));
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
        assertThat(em.contains(author), is(true));
    }

    @Transactional
    public void testFindAndUpdate() throws Exception {
        assertActiveTransaction();
        final Author author = this.em.find(Author.class, Integer.valueOf(1));
        assertThat(author, notNullValue());
        assertThat(author.getId(), is(1));
        assertThat(author.getName(), is("Abraham Lincoln"));
        assertThat(this.em.contains(author), is(true));
        author.setName("Abe Lincoln");
    }


    /*
     * Assertion-style methods.
     */


    private void assertAuthorTableIsEmpty() throws SQLException {
        assertThat(this.dataSource, notNullValue());
        try (final Connection connection = this.dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(0));
            assertThat(resultSet.next(), is(false));
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
        assertThat(this.em, notNullValue());
        assertThat(this.tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(this.em.isJoinedToTransaction(), is(false));
    }

}
