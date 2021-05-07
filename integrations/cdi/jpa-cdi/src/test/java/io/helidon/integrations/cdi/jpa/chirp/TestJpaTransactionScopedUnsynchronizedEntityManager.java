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

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.SynchronizationType;
import javax.persistence.TransactionRequiredException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestJpaTransactionScopedUnsynchronizedEntityManager;"
        + "INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestJpaTransactionScopedUnsynchronizedEntityManager {

    static {
        System.setProperty("jpaAnnotationRewritingEnabled", "true");
    }

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;
    
    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.UNSYNCHRONIZED,
        unitName = "chirp"
    )
    private EntityManager jpaTransactionScopedUnsynchronizedEntityManager;


    /*
     * Constructors.
     */


    TestJpaTransactionScopedUnsynchronizedEntityManager() {
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
    EntityManager getJpaTransactionScopedUnsynchronizedEntityManager() {
        return this.jpaTransactionScopedUnsynchronizedEntityManager;
    }

    TransactionManager getTransactionManager() {
        return this.transactionManager;
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
    void testJpaTransactionScopedUnsynchronizedEntityManager()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException
    {

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        final TestJpaTransactionScopedUnsynchronizedEntityManager self =
            this.cdiContainer.select(TestJpaTransactionScopedUnsynchronizedEntityManager.class).get();
        assertNotNull(self);

        // Get the EntityManager that is not synchronized with but is
        // scoped to a JTA transaction.
        final EntityManager em = self.getJpaTransactionScopedUnsynchronizedEntityManager();
        assertNotNull(em);
        assertTrue(em.isOpen());

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @javax.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertFalse(em.isJoinedToTransaction());

        // Create a JPA entity and try to insert it.  This should fail
        // because according to JPA a TransactionRequiredException
        // will be thrown.
        final Author author = new Author("Abraham Lincoln");
        try {
            em.persist(author);
            fail("A TransactionRequiredException should have been thrown");
        } catch (final TransactionRequiredException expected) {
            
        }

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        final TransactionManager tm = self.getTransactionManager();
        assertNotNull(tm);
        tm.begin();

        // Because we're UNSYNCHRONIZED, no automatic joining takes place.
        assertFalse(em.isJoinedToTransaction());

        // We can join manually.
        em.joinTransaction();

        // Now we should be joined to it.
        assertTrue(em.isJoinedToTransaction());

        // Roll the transaction back and note that our EntityManager
        // is no longer joined to it.
        tm.rollback();
        assertFalse(em.isJoinedToTransaction());

        // Start another transaction and persist our Author.  But note
        // that joining the transaction must be manual.
        tm.begin();
        assertFalse(em.isJoinedToTransaction());
        em.persist(author);
        assertFalse(em.isJoinedToTransaction());
        assertTrue(em.contains(author));

        // (Remember, we weren't ever joined to this transaction.)
        tm.commit();

        // The transaction is over, and our EntityManager is STILL not joined
        // to one.
        assertFalse(em.isJoinedToTransaction());

        // Now the weird part.  Our EntityManager was of type
        // PersistenceContextType.TRANSACTION, but
        // SynchronizationType.UNSYNCHRONIZED.  So it never joins
        // transactions automatically, but its backing persistence
        // context does NOT span transactions.
        assertFalse(em.contains(author));
    }

}
