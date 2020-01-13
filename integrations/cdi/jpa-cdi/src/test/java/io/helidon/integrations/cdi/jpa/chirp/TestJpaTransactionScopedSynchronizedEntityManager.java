/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
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
    url = "jdbc:h2:mem:chirp;INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp.ddl'",
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
        Author author = new Author("Abraham Lincoln");
        try {
            em.persist(author);
            fail("A TransactionRequiredException should have been thrown");
        } catch (final TransactionRequiredException expected) {

        }

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        final TransactionManager tm = self.getTransactionManager();
        assertNotNull(tm);
        tm.setTransactionTimeout(60 * 20); // TODO: set to 20 minutes for debugging purposes only
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

        // Start another transaction.
        tm.begin();
        assertTrue(transactionScopedContext.isActive());

        // Persist our Author.
        assertNull(author.getId());
        em.persist(author);
        assertTrue(em.contains(author));
        tm.commit();

        // After the transaction commits, a flush should happen, and
        // the author is managed, so we should see his ID.
        assertEquals(Integer.valueOf(1), author.getId());

        // The transaction is over, so our EntityManager is not joined
        // to one anymore.
        assertFalse(em.isJoinedToTransaction());
        assertFalse(transactionScopedContext.isActive());

        // Our PersistenceContextType is TRANSACTION, not EXTENDED, so
        // the underlying persistence context dies with the
        // transaction so the EntityManager's persistence context
        // should be empty, so the contains() operation should return
        // false.
        assertFalse(em.contains(author));

        // Start a transaction.
        tm.begin();
        assertTrue(transactionScopedContext.isActive());

        // Remove the Author we successfully committed before.  We
        // have to merge because the author became detached a few
        // lines above.
        author = em.merge(author);
        em.remove(author);
        assertFalse(em.contains(author));
        tm.commit();

        assertFalse(em.isJoinedToTransaction());
        assertFalse(transactionScopedContext.isActive());

        // Note that its ID is still 1.
        assertEquals(Integer.valueOf(1), author.getId());

        assertDatabaseIsEmpty(dataSource);

        tm.begin();

        try {
            assertTrue(em.isJoinedToTransaction());
            assertTrue(transactionScopedContext.isActive());

            // This is interesting. author is now detached, but it
            // still has a persistent identifier set to 1.
            em.persist(author);

            // The act of persisting doesn't flush anything, so our id
            // is still 1.
            assertEquals(Integer.valueOf(1), author.getId());

            assertTrue(transactionScopedContext.isActive());
            assertTrue(em.contains(author));
            assertTrue(em.isJoinedToTransaction());

            // Persisting the same thing again is a no-op.
            em.persist(author);

            // The act of persisting doesn't flush anything, so our id
            // is still 1.
            assertEquals(Integer.valueOf(1), author.getId());

            // Make sure the TransactionContext is active.
            assertTrue(transactionScopedContext.isActive());
            assertTrue(em.contains(author));
            assertTrue(em.isJoinedToTransaction());

            tm.commit();

            // Make sure the TransactionContext is NOT active.
            assertFalse(em.isJoinedToTransaction());
            assertFalse(em.contains(author));
            assertFalse(transactionScopedContext.isActive());

            // Now that the commit and accompanying flush have
            // happened, our author's ID has changed.
            assertEquals(Integer.valueOf(2), author.getId());
        } catch (final EntityExistsException expected) {

        } catch (final Exception somethingUnexpected) {
            fail(somethingUnexpected);
        }
    }

    private static final void assertDatabaseIsEmpty(final DataSource dataSource) throws SQLException {
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT COUNT(a.id) FROM AUTHOR a");) {
            assertNotNull(resultSet);
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
        }
    }

}
