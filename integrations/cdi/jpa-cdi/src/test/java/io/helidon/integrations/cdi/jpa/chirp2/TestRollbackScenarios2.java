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

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
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
    url = "jdbc:h2:mem:TestRollbackScenarios2;MODE=LEGACY;INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp2.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestRollbackScenarios2 {

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp2"
    )
    private EntityManager jpaTransactionScopedSynchronizedEntityManager;


    /*
     * Constructors.
     */


    TestRollbackScenarios2() {
        super();
    }


    /*
     * Setup and teardown methods.
     */


    @BeforeEach
    void startCdiContainer() {
        System.setProperty(io.helidon.integrations.cdi.jpa.JpaExtension.class.getName() + ".enabled", "false");
        System.setProperty(io.helidon.integrations.cdi.jpa.PersistenceExtension.class.getName() + ".enabled", "true");
        SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
        assertThat(initializer, notNullValue());
        this.cdiContainer = initializer.initialize();
    }

    @AfterEach
    void shutDownCdiContainer() {
        try {

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


    TestRollbackScenarios2 self() {
        return this.cdiContainer.select(TestRollbackScenarios2.class).get();
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

    private void onShutdown(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event, TransactionManager tm)
        throws SystemException {
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
        try {
            this.jpaTransactionScopedSynchronizedEntityManager.clear();
            this.jpaTransactionScopedSynchronizedEntityManager.getEntityManagerFactory().getCache().evictAll();
        } finally {
            if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
                tm.rollback();
            }
        }
    }


    /*
     * Test methods.
     */


    @Test
    void testRollbackScenarios()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               InterruptedException,
               NotSupportedException,
               RollbackException,
               SystemException
    {

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        TestRollbackScenarios2 self = self();
        assertThat(self, notNullValue());

        // Get the EntityManager that is synchronized with and scoped
        // to a JTA transaction.
        EntityManager em = self.getJpaTransactionScopedSynchronizedEntityManager();
        assertThat(em, notNullValue());
        assertThat(em.isOpen(), is(true));

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @jakarta.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertThat(em.isJoinedToTransaction(), is(false));

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        TransactionManager tm = self.getTransactionManager();
        assertThat(tm, notNullValue());
        tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
        tm.begin();
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));

        // Now magically our EntityManager should be joined to it.
        assertThat(em.isJoinedToTransaction(), is(true));

        // Create a JPA entity and insert it.
        Author author = new Author(1, "Abraham Lincoln");
        assertThat(author.getId(), is(1));

        em.persist(author);
        // assertThat(author.getId(), is(nullValue()));

        // Commit the transaction.  Because we're relying on the
        // default flush mode, this will cause a flush to the
        // database, which, in turn, will result in author identifier
        // generation.
        tm.commit();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(author.getId(), is(1));

        // We're no longer in a transaction.
        assertThat(em.isJoinedToTransaction(), is(false));

        // The persistence context should be cleared.
        assertThat(em.contains(author), is(false));

        // Ensure transaction statuses are what we think they are.
        tm.begin();
        tm.setRollbackOnly();
        try {
          assertThat(tm.getStatus(), is(Status.STATUS_MARKED_ROLLBACK));
        } finally {
          tm.rollback();
        }
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));

        // We can do non-transactional things.
        assertThat(em.isOpen(), is(true));
        author = em.find(Author.class, Integer.valueOf(1));
        assertThat(author, notNullValue());

        // Note that because we've invoked this somehow outside of a
        // transaction everything it touches is detached, per section
        // 7.6.2 of the JPA 2.2 specification.
        assertThat(em.contains(author), is(false));

        // Remove everything.
        tm.begin();
        author = em.merge(author);
        assertThat(author, notNullValue());
        assertThat(em.contains(author), is(true));
        em.remove(author);
        tm.commit();
        assertThat(em.contains(author), is(false));

        // Create a new unmanaged Author.
        author = new Author(2, "John Kennedy");
        assertThat(author.getId(), is(2));

        tm.begin();
        em.persist(author);

        // This assertion depends on the ID generation strategy,
        // sadly, and will not necessarily work across JPA providers
        // for identity column ID generation.
        assertThat(author.getId(), is(2));

        assertThat(em.contains(author), is(true));

        // Perform a rollback "in the middle" of a sequence of
        // operations and observe that the EntityManager is in the
        // proper state throughout.
        tm.rollback();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(em.contains(author), is(false));

        assertThat(author.getId(), is(2));

        // Try to remove the now-detached author outside of a
        // transaction. Should fail.
        try {
          em.remove(author);
          fail("remove() was allowed to complete without a transaction");
        } catch (IllegalArgumentException | TransactionRequiredException expected) {
          // The javadocs say only that either of these exceptions may
          // be thrown in this case but do not indicate which one is
          // preferred.  EclipseLink 2.7.4 throws a
          // TransactionRequiredException here.  It probably should
          // throw an IllegalArgumentException; see
          // https://bugs.eclipse.org/bugs/show_bug.cgi?id=553117
          // which is related.
        }

        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(em.contains(author), is(false));
        assertThat(author.getId(), is(2));

        // Start a transaction.
        tm.begin();
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));
        assertThat(em.isJoinedToTransaction(), is(true));

        // author is still detached
        assertThat(em.contains(author), is(false));
        em.detach(author); // redundant; just making a point
        assertThat(em.contains(author), is(false));
        assertThat(author.getId(), is(2));

        // Try again to remove the detached author, but this time in a
        // transaction.  Shouldn't matter; should also fail.
        try {
          em.remove(author);
          // We shouldn't get here because author is detached but with
          // EclipseLink 2.7.4 we do.  See
          // https://bugs.eclipse.org/bugs/show_bug.cgi?id=553117.
          // fail("remove() was allowed to accept a detached object");
        } catch (IllegalArgumentException expected) {

        }
        tm.rollback();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(em.contains(author), is(false));
        assertThat(author.getId(), is(2));

        // Remove the author properly.
        tm.begin();
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));
        assertThat(em.isJoinedToTransaction(), is(true));
        assertThat(em.contains(author), is(false));
        author = em.merge(author);
        assertThat(em.contains(author), is(true));
        em.remove(author);
        tm.commit();
        assertThat(em.contains(author), is(false));

        // Cause a timeout-tripped rollback.
        tm.setTransactionTimeout(1); // 1 second
        author = new Author(3, "Woodrow Wilson");
        tm.begin();
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));
        Thread.sleep(1500L); // 1.5 seconds (arbitrarily greater than 1 second)
        assertThat(tm.getStatus(), is(Status.STATUS_ROLLEDBACK));
        try {
          em.persist(author);
          fail("Transaction rolled back but persist still happened");
        } catch (TransactionRequiredException expected) {

        }
        tm.rollback();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));

        tm.setTransactionTimeout(0); // set the timeout back to the default (that's what 0 means (!))

    }

}
