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
import jakarta.persistence.PersistenceException;
import jakarta.persistence.SynchronizationType;
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp2",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestExtendedSynchronizedEntityManager2;MODE=LEGACY;INIT=SET TRACE_LEVEL_FILE=4\\;RUNSCRIPT FROM 'classpath:chirp2.ddl'",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestExtendedSynchronizedEntityManager2 {

    static {
        System.setProperty("jpaAnnotationRewritingEnabled", "true");
    }

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;

    @PersistenceContext(
        type = PersistenceContextType.EXTENDED,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp2"
    )
    private EntityManager extendedSynchronizedEntityManager;


    /*
     * Constructors.
     */


    TestExtendedSynchronizedEntityManager2() {
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
    EntityManager getExtendedSynchronizedEntityManager() {
        return this.extendedSynchronizedEntityManager;
    }

    TransactionManager getTransactionManager() {
        return this.transactionManager;
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
    void testExtendedSynchronizedEntityManager()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException
    {

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        TestExtendedSynchronizedEntityManager2 self =
            this.cdiContainer.select(TestExtendedSynchronizedEntityManager2.class).get();
        assertThat(self, notNullValue());

        // Get the EntityManager that is synchronized with but whose
        // persistence context extends past a single JTA transaction.
        EntityManager em = self.getExtendedSynchronizedEntityManager();
        assertThat(em, notNullValue());
        assertThat(em.isOpen(), is(true));

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @jakarta.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertThat(em.isJoinedToTransaction(), is(false));

        // Create a JPA entity and try to insert it.  Should be just
        // fine.
        Author author = new Author(1, "Abraham Lincoln");

        // With an EXTENDED EntityManager, persisting outside of a
        // transaction is OK.
        em.persist(author);

        // Our PersistenceContextType is EXTENDED, not TRANSACTION, so
        // the underlying persistence context spans transactions.
        assertThat(em.contains(author), is(true));

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.
        TransactionManager tm = self.getTransactionManager();
        tm.begin();

        // Now magically our EntityManager should be joined to it.
        assertThat(em.isJoinedToTransaction(), is(true));

        // Roll the transaction back and note that our EntityManager
        // is no longer joined to it.
        tm.rollback();
        assertThat(em.isJoinedToTransaction(), is(false));
        assertThat(em.contains(author), is(false));

        // Start another transaction and persist our Author.
        tm.begin();

        try {
            // See
            // https://www.baeldung.com/hibernate-detached-entity-passed-to-persist#trying-to-persist-a-detached-entity
            // and
            // https://hibernate.atlassian.net/browse/HHH-15738. Eclipselink
            // handles all this just fine.
            em.persist(author);

            assertThat(em.contains(author), is(true));
            tm.commit();

            // The transaction is over, so our EntityManager is not
            // joined to one anymore.
            assertThat(em.isJoinedToTransaction(), is(false));

            // Our PersistenceContextType is EXTENDED, not
            // TRANSACTION, so the underlying persistence context
            // spans transactions.
            assertThat(em.contains(author), is(true));
        } catch (PersistenceException hhh15738) {
            assertThat(tm.getStatus(), is(Status.STATUS_MARKED_ROLLBACK));
            tm.rollback();
        }
    }

}
