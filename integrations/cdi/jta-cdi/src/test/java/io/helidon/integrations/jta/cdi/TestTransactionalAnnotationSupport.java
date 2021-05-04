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
package io.helidon.integrations.jta.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionScoped;
import javax.transaction.Transactional;
import javax.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ApplicationScoped
public class TestTransactionalAnnotationSupport {

    private SeContainer cdiContainer;

    private boolean transactionScopeStarted;

    @Inject
    private Transaction transaction;

    @Inject
    private UserTransaction userTransaction;

    TestTransactionalAnnotationSupport() {
        super();
    }

    @BeforeEach
    void startCdiContainer() {
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(TestTransactionalAnnotationSupport.class);
        assertNotNull(initializer);
        this.cdiContainer = initializer.initialize();
    }
  
    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
    }
  
    private static void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                                  final TestTransactionalAnnotationSupport self)
        throws SystemException {
        assertNotNull(event);
        assertNotNull(self);
        self.doSomethingTransactional();
    }

    private void onBeginningOfTransactionScope(@Observes @Initialized(TransactionScoped.class) final Object event) {
        assertTrue(event instanceof Transaction);
        this.transactionScopeStarted = true;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    void doSomethingTransactional() throws SystemException {
        assertTrue(this.transactionScopeStarted);
        assertNotNull(this.userTransaction);
        assertEquals(Status.STATUS_ACTIVE, this.userTransaction.getStatus());
        assertNotNull(this.transaction);
        assertEquals(Status.STATUS_ACTIVE, this.transaction.getStatus());
    }

    @Test
    void testTransactionalAnnotationSupport() {

    }
  
}
