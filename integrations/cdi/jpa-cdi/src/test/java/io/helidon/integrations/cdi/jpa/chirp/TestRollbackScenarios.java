/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import javax.annotation.sql.DataSourceDefinition;
import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
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
class TestRollbackScenarios {

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp"
    )
    private EntityManager jpaTransactionScopedSynchronizedEntityManager;


    /*
     * Constructors.
     */


    TestRollbackScenarios() {
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


    TestRollbackScenarios self() {
        return this.cdiContainer.select(TestRollbackScenarios.class).get();
    }

    /**
     * A "business method" providing access to one of this {@link
     * TestRollbackScenarios}' {@link EntityManager}
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
    void testRollbackScenarios()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException
    {

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        final TestRollbackScenarios self = self();
        assertNotNull(self);

        // Get the EntityManager that is synchronized with and scoped
        // to a JTA transaction.
        final EntityManager em = self.getJpaTransactionScopedSynchronizedEntityManager();
        assertNotNull(em);
        assertTrue(em.isOpen());

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @javax.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertFalse(em.isJoinedToTransaction());

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        final TransactionManager tm = self.getTransactionManager();
        assertNotNull(tm);
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());

        // Now magically our EntityManager should be joined to it.
        assertTrue(em.isJoinedToTransaction());

        // Create a JPA entity and insert it.
        Author author = new Author("Abraham Lincoln");
        em.persist(author);

        // No trip to the database has happened yet, so the author's
        // identifier isn't set yet.
        assertNull(author.getId());

        // Commit the transaction.  Because we're relying on the
        // default flush mode, this will cause a flush to the
        // database, which, in turn, will result in author identifier
        // generation.
        tm.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertEquals(Integer.valueOf(1), author.getId());

        // We're no longer in a transaction.
        assertFalse(em.isJoinedToTransaction());

        // The persistence context should be cleared.
        assertFalse(em.contains(author));

        // Ensure transaction statuses are what we think they are.
        tm.begin();
        tm.setRollbackOnly();
        try {
          assertEquals(Status.STATUS_MARKED_ROLLBACK, tm.getStatus());
        } finally {
          tm.rollback();
        }
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());

        // We can do non-transactional things.
        assertTrue(em.isOpen());
        author = em.find(Author.class, Integer.valueOf(1));
        assertNotNull(author);

        // Note that because we've invoked this somehow outside of a
        // transaction everything it touches is detached, per section
        // 7.6.2 of the JPA 2.2 specification.
        assertFalse(em.contains(author));

        // Remove everything.
        tm.begin();
        author = em.merge(author);
        assertNotNull(author);
        assertTrue(em.contains(author));
        em.remove(author);
        tm.commit();
        assertFalse(em.contains(author));

        author = new Author("John Kennedy");
        tm.begin();
        em.persist(author);
        assertTrue(em.contains(author));
        tm.rollback();
        assertFalse(em.contains(author));
        

    }

}
