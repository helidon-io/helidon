/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
        assertThat(initializer, notNullValue());
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
        assertThat(event, notNullValue());
        assertThat(self, notNullValue());
        self.doSomethingTransactional();
    }

    private void onBeginningOfTransactionScope(@Observes @Initialized(TransactionScoped.class) final Object event) {
        assertThat(event, instanceOf(Transaction.class));
        this.transactionScopeStarted = true;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    void doSomethingTransactional() throws SystemException {
        assertThat(this.transactionScopeStarted, is(true));
        assertThat(this.userTransaction, notNullValue());
        assertThat(this.userTransaction.getStatus(), is(Status.STATUS_ACTIVE));
        assertThat(this.transaction, notNullValue());
        assertThat(this.transaction.getStatus(), is(Status.STATUS_ACTIVE));
    }

    @Test
    void testTransactionalAnnotationSupport() {

    }
  
}
