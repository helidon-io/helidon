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
package io.helidon.integrations.cdi.jpa.weld;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "test",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:test",
    serverName = "",
    properties = {
        "user=sa"
    }
)
public class TestIntegration {

    private SeContainer cdiContainer;

    @Inject
    private Transaction transaction;

    @PersistenceContext
    private EntityManager entityManager;
  
    TestIntegration() {
        super();
    }

    @BeforeEach
    void startCdiContainer() {
        shutDownCdiContainer();
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(TestIntegration.class);
        assertNotNull(initializer);
        this.cdiContainer = initializer.initialize();
    }
  
    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
            this.cdiContainer = null;
        }
    }

    private static void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                                  final TestIntegration self)
        throws SystemException {
        assertNotNull(event);
        assertNotNull(self);
        self.doSomethingTransactional();
        self.doSomethingNonTransactional();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    void doSomethingTransactional() throws SystemException {
        assertNotNull(this.transaction);
        assertEquals(Status.STATUS_ACTIVE, this.transaction.getStatus());
        assertNotNull(this.entityManager);
        assertTrue(this.entityManager.isOpen());
        assertTrue(this.entityManager.isJoinedToTransaction());        
    }

    void doSomethingNonTransactional() {
        assertNotNull(this.transaction); // ...but the scope won't be active
        try {
            this.transaction.toString();
            fail("The TransactionScoped scope was active when it should not have been");
        } catch (final ContextNotActiveException expected) {

        }
        assertNotNull(this.entityManager);
        assertTrue(this.entityManager.isOpen());
        
    }

    @Test
    void testIntegration() {
    }

}
