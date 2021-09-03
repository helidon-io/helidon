/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
    url = "jdbc:h2:mem:TestJpaTransactionScopedSynchronizedEntityManager;"
        + "INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestJpaTransactionScopedSynchronizedEntityManager {

    static {
        System.setProperty("jpaAnnotationRewritingEnabled", "true");
    }

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;

    @Inject
    @Named("chirp")
    private DataSource dataSource;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp"
    )
    private EntityManager jpaTransactionScopedSynchronizedEntityManager;


    /*
     * Constructors.
     */


    TestJpaTransactionScopedSynchronizedEntityManager() {
        super();
    }


    /*
     * Setup and teardown methods.
     */


    @BeforeEach
    void startCdiContainer() {
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
        assertNotNull(initializer);
        this.cdiContainer = initializer.initialize();
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
        assertNotNull(beanManager);

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        final TestJpaTransactionScopedSynchronizedEntityManager self =
            this.cdiContainer.select(TestJpaTransactionScopedSynchronizedEntityManager.class).get();
        assertNotNull(self);

        // Get the EntityManager that is synchronized with and scoped
        // to a JTA transaction.
        final EntityManager em = self.getJpaTransactionScopedSynchronizedEntityManager();
        assertNotNull(em);
        assertTrue(em.isOpen());

        // Get a DataSource for JPA-independent testing and assertions.
        final DataSource dataSource = self.getDataSource();
        assertNotNull(dataSource);

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @javax.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertFalse(em.isJoinedToTransaction());

        // Create a JPA entity and try to insert it.  This should fail
        // because according to JPA a TransactionRequiredException
        // will be thrown.
        Author author1 = new Author("Abraham Lincoln");
        try {
            em.persist(author1);
            fail("A TransactionRequiredException should have been thrown");
        } catch (final TransactionRequiredException expected) {

        }
        assertFalse(em.contains(author1));
        assertNull(author1.getId());

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        final TransactionManager tm = self.getTransactionManager();
        assertNotNull(tm);
        tm.setTransactionTimeout(60 * 20); // TODO: set to 20 minutes for debugging purposes only

        // New transaction.
        tm.begin();

        // Grab the TransactionScoped context while the transaction is
        // active.  We want to make sure it's active at various
        // points.
        final Context transactionScopedContext = beanManager.getContext(TransactionScoped.class);
        assertNotNull(transactionScopedContext);
        assertTrue(transactionScopedContext.isActive());

        // Now magically our EntityManager should be joined to it.
        assertTrue(em.isJoinedToTransaction());

        // Roll the transaction back and note that our EntityManager
        // is no longer joined to it.
        tm.rollback();
        assertFalse(em.isJoinedToTransaction());
        assertFalse(transactionScopedContext.isActive());
        assertFalse(em.contains(author1));
        assertNull(author1.getId());

        // Start another transaction.
        tm.begin();
        assertTrue(transactionScopedContext.isActive());

        // Persist our Author.
        assertNull(author1.getId());
        em.persist(author1);
        assertTrue(em.contains(author1));

        // A persist() doesn't flush(), so no ID should have been
        // generated yet.
        // assertNull(author1.getId());

        // Commit the transaction and flush changes to the database.
        tm.commit();

        // After the transaction commits, a flush should happen, and
        // the Author is managed, so we should see his ID.
        assertEquals(Integer.valueOf(1), author1.getId());

        // Make sure the database contains the changes.
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR WHERE ID = 1");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertEquals("Abraham Lincoln", resultSet.getString(2));
            assertFalse(resultSet.next());
        }
        
        // The Author, however, is detached, because the transaction
        // is over, and because our PersistenceContextType is
        // TRANSACTION, not EXTENDED, the underlying persistence
        // context dies with the transaction.
        assertFalse(em.contains(author1));

        // The transaction is over, so our EntityManager is not joined
        // to one anymore.
        assertFalse(em.isJoinedToTransaction());
        assertFalse(transactionScopedContext.isActive());

        // Start a new transaction.
        tm.begin();
        assertTrue(transactionScopedContext.isActive());

        // Remove the Author we successfully committed before.  We
        // have to merge because author1 became detached a few lines
        // above.
        author1 = em.merge(author1);
        assertNotNull(author1);
        assertTrue(em.contains(author1));
        em.remove(author1);
        assertFalse(em.contains(author1));

        // Commit and flush the removal.
        tm.commit();

        assertFalse(em.isJoinedToTransaction());
        assertFalse(transactionScopedContext.isActive());

        // Note that its ID is still 1.
        assertEquals(Integer.valueOf(1), author1.getId());

        // After all this activity we should have no rows in any
        // tables.
        assertTableRowCount(dataSource, "AUTHOR", 0);

        // Start a new transaction, merge our detached author1, and
        // commit.  This will bump the author's ID and put a row in
        // the database.
        tm.begin();
        assertTrue(em.isJoinedToTransaction());
        assertTrue(transactionScopedContext.isActive());
        author1 = em.merge(author1);
        tm.commit();
        assertFalse(em.isJoinedToTransaction());
        assertFalse(em.contains(author1));
        assertFalse(transactionScopedContext.isActive());

        // Now that the commit and accompanying flush have
        // happened, our author's ID has changed.
        assertEquals(Integer.valueOf(2), author1.getId());

        // Make sure the database contains the changes.
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(2, resultSet.getInt(1));
            assertEquals("Abraham Lincoln", resultSet.getString(2));
            assertFalse(resultSet.next());
        }

        // Discard author1 in this unit test so we'll get a
        // NullPointerException if we try to use him again.  (After
        // all it's confusing now that author1 has 2 for an ID!)
        author1 = null;

        // Let's find the new author that got merged in.  We'll use a
        // transaction just for kicks.
        tm.begin();
        assertTrue(em.isJoinedToTransaction());
        assertTrue(transactionScopedContext.isActive());

        Author author2 = em.find(Author.class, Integer.valueOf(2));
        assertNotNull(author2);
        assertTrue(em.contains(author2));
        assertEquals(Integer.valueOf(2), author2.getId());
        assertEquals("Abraham Lincoln", author2.getName());

        // No need, really, but it's what a @Transactional method
        // would do.
        tm.commit();
        assertFalse(em.isJoinedToTransaction());
        assertFalse(em.contains(author2));
        assertFalse(transactionScopedContext.isActive());

        // New transaction.  Let's change the name.
        tm.begin();
        assertTrue(em.isJoinedToTransaction());
        assertTrue(transactionScopedContext.isActive());

        author2 = em.find(Author.class, Integer.valueOf(2));
        assertNotNull(author2);

        // Remember that finding an entity causes it to become
        // managed.
        assertTrue(em.contains(author2));

        assertEquals(Integer.valueOf(2), author2.getId());
        assertEquals("Abraham Lincoln", author2.getName());

        author2.setName("Abe Lincoln");
        assertEquals(Integer.valueOf(2), author2.getId());
        assertEquals("Abe Lincoln", author2.getName());

        tm.commit();
        assertFalse(em.isJoinedToTransaction());
        assertFalse(em.contains(author2));
        assertFalse(transactionScopedContext.isActive());

        // Make sure the database contains the changes.
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM AUTHOR");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(2, resultSet.getInt(1));
            assertEquals("Abe Lincoln", resultSet.getString(2));
            assertFalse(resultSet.next());
        }

        // Let's go find him again.
        tm.begin();
        assertTrue(em.isJoinedToTransaction());
        assertTrue(transactionScopedContext.isActive());

        author2 = em.find(Author.class, Integer.valueOf(2));
        assertNotNull(author2);
        assertTrue(em.contains(author2));
        assertEquals(Integer.valueOf(2), author2.getId());
        assertEquals("Abe Lincoln", author2.getName());

        // No need, really, but it's what a @Transactional method
        // would do.
        tm.commit();
        assertFalse(em.isJoinedToTransaction());
        assertFalse(em.contains(author2));
        assertFalse(transactionScopedContext.isActive());

    }

    private static final void assertTableRowCount(final DataSource dataSource,
                                                  final String upperCaseTableName,
                                                  final int expectedCount)
        throws SQLException {
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + upperCaseTableName);) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(expectedCount, resultSet.getInt(1));
        }
    }

}
