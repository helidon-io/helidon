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
package io.helidon.integrations.cdi.jpa;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceUnit;
import javax.persistence.SynchronizationType;
import javax.persistence.TransactionRequiredException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "test",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestAnnotationRewriting",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestAnnotationRewriting {

    @PersistenceUnit(unitName = "test-resource-local")
    private EntityManagerFactory emf;
    
    @PersistenceContext(name = "bogus", unitName = "test")
    private EntityManager em;

    @PersistenceContext(unitName = "test", type = PersistenceContextType.EXTENDED)
    private EntityManager extendedEm;
    
    private SeContainer cdiContainer;

    TestAnnotationRewriting() {
        super();
    }
    
    @BeforeEach
    void startCdiContainer() {
        System.setProperty("jpaAnnotationRewritingEnabled", "true");
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
  
    @PersistenceContext(unitName = "test")
    private void observerMethod(@Observes final TestIsRunning event,
                                final EntityManager emParameter) {
        assertNotNull(event);

        assertNotNull(emParameter);
        assertTrue(emParameter.isOpen());
        assertFalse(emParameter.isJoinedToTransaction());

        assertNotNull(this.em);
        assertTrue(this.em.isOpen());
        assertFalse(this.em.isJoinedToTransaction());

        assertNotNull(this.emf);
        assertTrue(this.emf.isOpen());
        EntityManager em = null;
        try {
          em = this.emf.createEntityManager();
          assertNotNull(em);
          assertTrue(em.isOpen());
          assertFalse(em.isJoinedToTransaction());
        } finally {
          if (em != null && !em.isOpen()) {
            em.close();
          }
        }
        try {
          em = this.emf.createEntityManager(SynchronizationType.UNSYNCHRONIZED, null);
          fail("Was able to pass a non-null SynchronizationType");
        } catch (final IllegalStateException expected) {
          
        } finally {
          if (em != null && !em.isOpen()) {
            em.close();
          }
        }
    }

    @Test
    void testAnnotationRewriting() {
        this.cdiContainer.getBeanManager()
            .getEvent()
            .select(TestIsRunning.class)
            .fire(new TestIsRunning("testAnnotationRewriting"));
    }

    @Test
    void testNonTransactionalEntityManager() {
        this.cdiContainer.getBeanManager()
            .getEvent()
            .select(TestIsRunning.class)
            .fire(new TestIsRunning("testNonTransactionalEntityManager"));
        final Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(JpaTransactionScoped.Literal.INSTANCE);
        final EntityManager entityManager = this.cdiContainer.select(EntityManager.class, qualifiers.toArray(new Annotation[qualifiers.size()])).get();
        assertTrue(entityManager instanceof DelegatingEntityManager);
        assertTrue(entityManager.isOpen());
        assertFalse(entityManager.isJoinedToTransaction());
        try {
            entityManager.persist(new Object());
            fail("A TransactionRequiredException should have been thrown");
        } catch (final TransactionRequiredException expected) {

        }
        try {
            entityManager.close();
            fail("Closed EntityManager; should not have been able to");
        } catch (final IllegalStateException expected) {

        }
    }

    @Test
    void testTransactionalEntityManager() {
        this.cdiContainer.getBeanManager()
            .getEvent()
            .select(TestIsRunning.class)
            .fire(new TestIsRunning("testTransactionalEntityManager"));
        final Instance<TestAnnotationRewriting> instance = this.cdiContainer.select(TestAnnotationRewriting.class);
        final TestAnnotationRewriting test = instance.get();
        assertNotNull(test);
        test.testEntityManagerIsJoinedToTransactionInTransactionalAnnotatedMethod();
    }
    
    @Transactional
    void testEntityManagerIsJoinedToTransactionInTransactionalAnnotatedMethod() {
        assertNotNull(this.em);
        assertTrue(this.em.isJoinedToTransaction());
        try {
            this.em.close();
            fail("Closed EntityManager; should not have been able to");
        } catch (final IllegalStateException expected) {

        }
    }

    private static final class TestIsRunning {

        private final String test;

        private TestIsRunning(final String test) {
            super();
            this.test = test;
        }

        @Override
        public final String toString() {
            return this.test;
        }
    }

}
