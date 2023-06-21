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
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.TransactionRequiredException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionScoped;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp2",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestJpaTransactionScopedSynchronizedEntityManager2;"
        + "MODE=LEGACY;"
        + "INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp2.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestJpaTransactionScopedSynchronizedEntityManager2 {

    /*
    static {
        System.setProperty("jpaAnnotationRewritingEnabled", "true");
    }
    */

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;

    @Inject
    @Named("chirp2")
    private DataSource dataSource;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp2"
    )
    private EntityManager jpaTransactionScopedSynchronizedEntityManager;


    /*
     * Constructors.
     */


    TestJpaTransactionScopedSynchronizedEntityManager2() {
        super();
    }


    /*
     * Setup and teardown methods.
     */


    @BeforeEach
    void startCdiContainer() {
        System.setProperty(io.helidon.integrations.cdi.jpa.JpaExtension.class.getName() + ".enabled", "false");
        System.setProperty(io.helidon.integrations.cdi.jpa.PersistenceExtension.class.getName() + ".enabled", "true");
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
        assertThat(initializer, notNullValue());
        this.cdiContainer = initializer.initialize();
    }

    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
        System.setProperty(io.helidon.integrations.cdi.jpa.PersistenceExtension.class.getName() + ".enabled", "false");
        System.setProperty(io.helidon.integrations.cdi.jpa.JpaExtension.class.getName() + ".enabled", "true");
    }


    /*
     * Support methods.
     */


    /**
     * A "business method" providing access to one of this {@link
     * TestJpaTransactionScopedEntityManager}'s {@link EntityManager}
     * instances for use by {@link Test}-annotated methods.
     *
     * @return a non-{@code null} {@link EntityManager}
     */
    EntityManager getJpaTransactionScopedSynchronizedEntityManager() {
        return this.jpaTransactionScopedSynchronizedEntityManager;
    }

    TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    DataSource getDataSource() {
        return this.dataSource;
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
    void testJpaTransactionScopedSynchronizedEntityManager()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException
    {

        // Get a BeanManager for later use.
        final BeanManager beanManager = this.cdiContainer.getBeanManager();
        assertThat(beanManager, notNullValue());

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        final TestJpaTransactionScopedSynchronizedEntityManager2 self =
            this.cdiContainer.select(TestJpaTransactionScopedSynchronizedEntityManager2.class).get();
        assertThat(self, notNullValue());

        // Get the EntityManager that is synchronized with and scoped
        // to a JTA transaction.
        final EntityManager em = self.getJpaTransactionScopedSynchronizedEntityManager();
        assertThat(em, notNullValue());
        assertThat(em.isOpen(), is(true));

        // Get a DataSource for JPA-independent testing and assertions.
        final DataSource dataSource = self.getDataSource();
        assertThat(dataSource, notNullValue());

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @jakarta.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertThat(em.isJoinedToTransaction(), is(false));

        // Create a JPA entity and try to insert it.  This should fail
        // because according to JPA a TransactionRequiredException
        // will be thrown.
        Author author1 = new Author(1, "Abraham Lincoln");
        try {
            em.persist(author1);
            fail("A TransactionRequiredException should have been thrown");
        } catch (final TransactionRequiredException expected) {

        }
        assertThat(em.contains(author1), is(false));
        assertThat(author1.getId(), is(1));

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        final TransactionManager tm = self.getTransactionManager();
        tm.setTransactionTimeout(60 * 20); // Set to 20 minutes for debugging purposes only

        // Create a new transaction.
        tm.begin();

        // Grab the TransactionScoped context while the transaction is
        // active.  We want to make sure it's active at various
        // points.
        final Context transactionScopedContext = beanManager.getContext(TransactionScoped.class);
        assertThat(transactionScopedContext, notNullValue());
        assertThat(transactionScopedContext.isActive(), is(true));

        // Now magically our EntityManager should be joined to it.
        assertThat(em.isJoinedToTransaction(), is(true));

        // Roll the transaction back and note that our EntityManager
        // is no longer joined to it.
        tm.rollback();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));
        assertThat(em.contains(author1), is(false));
        assertThat(author1.getId(), is(1));

        // Start another transaction.
        tm.begin();
        assertThat(transactionScopedContext.isActive(), is(true));
        assertThat(em.isJoinedToTransaction(), is(true));

        // Persist our Author.
        assertThat(author1.getId(), is(1));
        em.persist(author1);
        assertThat(em.contains(author1), is(true));

        // Commit the transaction and flush changes to the database.
        tm.commit();

        // After the transaction commits, a flush should happen, and
        // the Author is managed, so we should see his ID.
        assertThat(author1.getId(), is(1));

        // Make sure the database contains the changes.
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR WHERE ID = 1");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(1));
            assertThat(resultSet.getString(2), is("Abraham Lincoln"));
            assertThat(resultSet.next(), is(false));
        }

        // The Author, however, is detached, because the transaction
        // is over, and because our PersistenceContextType is
        // TRANSACTION, not EXTENDED, the underlying persistence
        // context dies with the transaction.
        assertThat(em.contains(author1), is(false));

        // The transaction is over, so our EntityManager is not joined
        // to one anymore.
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));

        // Start a new transaction.
        tm.begin();
        assertThat(transactionScopedContext.isActive(), is(true));
        assertThat(em.isJoinedToTransaction(), is(true));

        // Remove the Author we successfully committed before.  We
        // have to merge because author1 became detached a few lines
        // above.
        author1 = em.merge(author1);
        assertThat(author1.getId(), is(1));
        assertThat(em.contains(author1), is(true));
        em.remove(author1);
        assertThat(em.contains(author1), is(false));
        assertThat(author1.getId(), is(1));

        // Commit and flush the removal.
        tm.commit();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));

        // Note that its ID is still 1.
        assertThat(author1.getId(), is(1));

        // After all this activity we should have no rows in any
        // tables.
        assertTableRowCount(dataSource, "AUTHOR", 0);

        // Start a new transaction, merge our detached author1, and
        // commit.
        tm.begin();
        assertThat(em.isJoinedToTransaction(), is(true));
        assertThat(transactionScopedContext.isActive(), is(true));

        // Actually, this really should throw an
        // IllegalArgumentException, since author1 was
        // removed. Neither Eclipselink nor Hibernate throws an
        // exception here.
        author1 = em.merge(author1);
        
        assertThat(em.contains(author1), is(true));
        assertThat(author1.getId(), is(1));

        tm.commit();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(em.contains(author1), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));
        assertThat(author1.getId(), is(1));

        // Make sure the database contains the changes.
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(1));
            assertThat(resultSet.getString(2), is("Abraham Lincoln"));
            assertThat(resultSet.next(), is(false));
        }

        // Discard author1 in this unit test so we'll get a
        // NullPointerException if we try to use him again.
        author1 = null;

        // Let's find the new author that got merged in.  We'll use a
        // transaction just for kicks.
        tm.begin();
        assertThat(em.isJoinedToTransaction(), is(true));
        assertThat(transactionScopedContext.isActive(), is(true));

        Author author2 = em.find(Author.class, Integer.valueOf(1));
        assertThat(author2, notNullValue());
        assertThat(em.contains(author2), is(true));
        assertThat(author2.getId(), is(1));
        assertThat(author2.getName(), is("Abraham Lincoln"));

        // No need, really, but it's what a @Transactional method
        // would do.
        tm.commit();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(em.contains(author2), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));

        // New transaction.  Let's change the name.
        tm.begin();
        assertThat(em.isJoinedToTransaction(), is(true));
        assertThat(transactionScopedContext.isActive(), is(true));

        author2 = em.find(Author.class, Integer.valueOf(1));
        assertThat(author2, notNullValue());

        // Remember that finding an entity causes it to become
        // managed.
        assertThat(em.contains(author2), is(true));

        assertThat(author2.getId(), is(1));
        assertThat(author2.getName(), is("Abraham Lincoln"));

        author2.setName("Abe Lincoln");
        assertThat(author2.getId(), is(1));
        assertThat(author2.getName(), is("Abe Lincoln"));

        tm.commit();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(em.contains(author2), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));

        // Make sure the database contains the changes.
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(1));
            assertThat(resultSet.getString(2), is("Abe Lincoln"));
            assertThat(resultSet.next(), is(false));
        }

        // Let's go find him again.
        tm.begin();
        assertThat(em.isJoinedToTransaction(), is(true));
        assertThat(transactionScopedContext.isActive(), is(true));

        author2 = em.find(Author.class, Integer.valueOf(1));
        assertThat(author2, notNullValue());
        assertThat(em.contains(author2), is(true));
        assertThat(author2.getId(), is(1));
        assertThat(author2.getName(), is("Abe Lincoln"));

        // No need, really, but it's what a @Transactional method
        // would do.
        tm.commit();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(em.contains(author2), is(false));
        assertThat(transactionScopedContext.isActive(), is(false));

    }

    private static final void assertTableRowCount(final DataSource dataSource,
                                                  final String upperCaseTableName,
                                                  final int expectedCount)
        throws SQLException {
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + upperCaseTableName);) {
            assertThat(resultSet, notNullValue());
            assertThat(resultSet.next(), is(true));
            assertThat(resultSet.getInt(1), is(expectedCount));
        }
    }

}
