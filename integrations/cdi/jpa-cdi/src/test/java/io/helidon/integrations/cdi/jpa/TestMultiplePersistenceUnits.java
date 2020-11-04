/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
    url = "jdbc:h2:mem:TestMultiplePersistenceUnits",
    serverName = "",
    properties = {
        "user=sa"
    }
)
@DataSourceDefinition(
    name = "test2",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestMultiplePersistenceUnits2",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestMultiplePersistenceUnits {

    @PersistenceContext(unitName = "test")
    private EntityManager testEm;

    @PersistenceContext(unitName = "test2")
    private EntityManager test2Em;

    private SeContainer cdiContainer;

    TestMultiplePersistenceUnits() {
        super();
    }

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

    EntityManager getTestEntityManager() {
        return this.testEm;
    }

    EntityManager getTest2EntityManager() {
        return this.test2Em;
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

    @Test
    void testMultiplePersistenceUnits() {
        TestMultiplePersistenceUnits self = this.cdiContainer.select(TestMultiplePersistenceUnits.class).get();
        assertNotNull(self);

        EntityManager testEm = self.getTestEntityManager();
        assertNotNull(testEm);
        assertTrue(testEm.isOpen());

        EntityManager test2Em = self.getTest2EntityManager();
        assertNotNull(test2Em);
        assertTrue(test2Em.isOpen());
    }

}
